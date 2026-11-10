package top.wkbin.taixu.harness

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import top.wkbin.taixu.harness.websearch.SearchHit
import top.wkbin.taixu.harness.websearch.WebSearchProvider
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import top.wkbin.taixu.harness.websearch.WebSearchProviders

/** search_web 工具后端：Provider 选择、结果格式化与失败转译的契约测试（假 Provider，不出网）。 */
class SearchWebToolBackendTest {

    private class FakeProvider(
        override val id: String,
        private val hits: List<SearchHit> = emptyList(),
        private val failure: Exception? = null,
    ) : WebSearchProvider {
        var lastQuery: String? = null
        var lastCount: Int = -1
        override suspend fun search(query: String, count: Int): List<SearchHit> {
            lastQuery = query
            lastCount = count
            failure?.let { throw it }
            return hits
        }
    }

    private fun args(query: String = "kotlin 协程", count: Int? = null) = buildJsonObject {
        put("query", query)
        count?.let { put("count", it) }
    }

    @Test
    fun `formats numbered results with citation guidance`() = runTest {
        val provider = FakeProvider("bing", listOf(SearchHit("标题一", "https://a.example/1", "摘要一"), SearchHit("标题二", "https://a.example/2", "")))
        val (success, output) = SearchWebToolBackend(WebSearchProviders(listOf(provider))).execute(
            SearchWebToolRequest(args(), "s1", "/workspace"),
        )
        assertTrue(success)
        assertTrue(output.contains("搜索「kotlin 协程」共 2 条结果"))
        assertTrue(output.contains("[1] 标题一"))
        assertTrue(output.contains("https://a.example/1"))
        assertTrue(output.contains("[2] 标题二"))
        assertTrue(output.contains("[n](url)"))
        assertEquals("kotlin 协程", provider.lastQuery)
        assertEquals(8, provider.lastCount)
    }

    @Test
    fun `empty query fails without hitting provider`() = runTest {
        val provider = FakeProvider("bing", listOf(SearchHit("t", "https://a.example/1", "")))
        val failure = runCatching {
            SearchWebToolBackend(WebSearchProviders(listOf(provider))).execute(
                SearchWebToolRequest(args(query = "  "), "s1", "/workspace"),
            )
        }.exceptionOrNull()
        assertTrue(failure is IllegalArgumentException)
        assertEquals(null, provider.lastQuery)
    }

    @Test
    fun `provider failure is translated into actionable output`() = runTest {
        val provider = FakeProvider("bing", failure = IllegalStateException("HTTP 503"))
        val (success, output) = SearchWebToolBackend(WebSearchProviders(listOf(provider))).execute(
            SearchWebToolRequest(args(), "s1", "/workspace"),
        )
        assertFalse(success)
        assertTrue(output.contains("搜索失败（bing）"))
        assertTrue(output.contains("HTTP 503"))
    }

    @Test
    fun `empty result set reports retry hint`() = runTest {
        val (success, output) = SearchWebToolBackend(WebSearchProviders(listOf(FakeProvider("bing")))).execute(
            SearchWebToolRequest(args(), "s1", "/workspace"),
        )
        assertFalse(success)
        assertTrue(output.contains("没有返回结果"))
    }

    @Test
    fun `count argument out of provider bounds is rejected`() = runTest {
        val provider = FakeProvider("bing", listOf(SearchHit("t", "https://a.example/1", "")))
        val failure = runCatching {
            SearchWebToolBackend(WebSearchProviders(listOf(provider))).execute(
                SearchWebToolRequest(args(count = 999), "s1", "/workspace"),
            )
        }.exceptionOrNull()
        assertTrue(failure is IllegalArgumentException)
        assertEquals(null, provider.lastQuery)
    }

    @Test
    fun `defaults to bing provider when present`() = runTest {
        val bing = FakeProvider("bing", listOf(SearchHit("t", "https://a.example/1", "")))
        val other = FakeProvider("other", listOf(SearchHit("t2", "https://a.example/2", "")))
        val (success, output) = SearchWebToolBackend(WebSearchProviders(listOf(other, bing))).execute(
            SearchWebToolRequest(args(), "s1", "/workspace"),
        )
        assertTrue(success)
        assertTrue(output.contains("标题一").not())
        assertEquals("kotlin 协程", bing.lastQuery)
        assertEquals(null, other.lastQuery)
    }
}
