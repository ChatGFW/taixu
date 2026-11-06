package top.wkbin.taixu.ui.chat

import kotlinx.coroutines.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import top.wkbin.taixu.core.database.MessageSearchHitRow

class SessionMessageSearchTest {
    private val job = SupervisorJob()
    private val scope = CoroutineScope(job + Dispatchers.Unconfined)
    @After fun close() { job.cancel() }
    private fun hit(text: String) = MessageSearchHitRow("s", "session", text, true, 1, text)

    @Test fun `new query clears old hits before its search returns`() = runBlocking {
        val gate = CompletableDeferred<List<MessageSearchHitRow>>()
        val search = SessionMessageSearch(scope, { if (it == "first") listOf(hit(it)) else gate.await() }, 0)
        search.submit(" first ")
        assertEquals("first", search.state.value.hits.single().excerpt)
        search.submit("second")
        assertTrue(search.state.value.loading)
        assertTrue(search.state.value.hits.isEmpty())
        gate.complete(listOf(hit("second")))
        yield()
        assertEquals("second", search.state.value.hits.single().excerpt)
        assertFalse(search.state.value.loading)
    }

    @Test fun `late storage response that ignores cancellation cannot replace current results`() = runBlocking {
        val gate = CompletableDeferred<Unit>()
        val search = SessionMessageSearch(scope, { query ->
            if (query == "old") withContext(NonCancellable) { gate.await() }
            listOf(hit(query))
        }, 0)
        search.submit("old")
        search.submit("new")
        gate.complete(Unit)
        yield()
        assertEquals("new", search.state.value.query)
        assertEquals("new", search.state.value.hits.single().excerpt)
        assertFalse(search.state.value.failed)
    }

    @Test fun `failure is distinct from no results and retry recovers`() {
        var failing = true
        val search = SessionMessageSearch(scope, { if (failing) error("storage") else listOf(hit(it)) }, 0)
        search.submit("word")
        assertTrue(search.state.value.failed)
        assertFalse(search.state.value.loading)
        failing = false
        search.submit("word")
        assertFalse(search.state.value.failed)
        assertEquals("word", search.state.value.hits.single().excerpt)
    }

    @Test fun `empty query cancels deferred work without calling storage`() {
        var requests = 0
        val search = SessionMessageSearch(scope, { requests++; emptyList() }, Long.MAX_VALUE)
        search.submit("one")
        search.submit("two")
        assertEquals("two", search.state.value.query)
        assertTrue(search.state.value.loading)
        search.submit("  ")
        assertEquals(SessionMessageSearchState(), search.state.value)
        assertEquals(0, requests)
    }

    @Test fun `closing prevents late publication`() = runBlocking {
        val gate = CompletableDeferred<Unit>()
        val search = SessionMessageSearch(scope, { withContext(NonCancellable) { gate.await() }; listOf(hit(it)) }, 0)
        search.submit("old")
        search.cancel()
        gate.complete(Unit)
        yield()
        assertEquals(SessionMessageSearchState(), search.state.value)
    }

    @Test fun `storage cancellation leaves an active query retryable instead of spinning forever`() {
        val search = SessionMessageSearch(scope, { throw CancellationException("cancelled") }, 0)
        search.submit("word")
        assertTrue(search.state.value.failed)
        assertFalse(search.state.value.loading)
        assertTrue(search.state.value.hits.isEmpty())
    }

    @Test fun `literal highlights cover unicode wildcard characters and repeated matches`() {
        assertEquals(listOf(0..1, 3..4), literalSearchRanges("中文 中文", "中文"))
        assertEquals(listOf(0..1, 3..4), literalSearchRanges("Aa aA", "aa"))
        assertEquals(listOf(1..2), literalSearchRanges("x😀y", "😀"))
        assertEquals(listOf(1..1), literalSearchRanges("a%b", "%"))
        assertEquals(listOf(1..1), literalSearchRanges("a_b", "_"))
        assertEquals(listOf(0..2), literalSearchRanges("a.* literal abbb", "a.*"))
        assertTrue(literalSearchRanges("anything", " ").isEmpty())
        assertTrue(literalSearchRanges("a", "longer").isEmpty())
    }
}
