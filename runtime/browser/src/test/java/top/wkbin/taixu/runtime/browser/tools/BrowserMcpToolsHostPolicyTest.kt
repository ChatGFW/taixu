package top.wkbin.taixu.runtime.browser.tools

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import top.wkbin.taixu.core.browser.ActionPolicyDocument
import top.wkbin.taixu.core.browser.BrowserHumanControl
import top.wkbin.taixu.core.browser.BrowserPreferences
import top.wkbin.taixu.core.browser.HostActionPolicy

/** CEL-lite 主机策略在 BrowserMcpTools 入口生效。 */
class BrowserMcpToolsHostPolicyTest {

    private val tools = BrowserMcpTools(
        engines = emptyList(),
        engineSelector = { _ -> null },
        prefs = BrowserPreferences.DEFAULT,
    )

    @Before
    fun setUp() {
        BrowserHumanControl.setCoBrowsingEnabled(true)
        HostActionPolicy.resetToDefault()
    }

    @After
    fun tearDown() {
        BrowserHumanControl.setCoBrowsingEnabled(true)
        HostActionPolicy.resetToDefault()
    }

    @Test
    fun `deny host refuses navigate before engine lookup`() = runBlocking {
        HostActionPolicy.install(
            ActionPolicyDocument(
                deny = listOf("contains(page.host, 'blocked')"),
                allow = listOf("true"),
            ),
        )
        val result = tools.invoke(
            "browser.navigate",
            buildJsonObject { put("url", "https://blocked.example/path") },
        )
        assertFalse(result.success)
        assertTrue(result.output.contains("策略拒绝") || result.output.contains("deny"))
    }

    @Test
    fun `default policy still reaches engine check`() = runBlocking {
        val result = tools.invoke(
            "browser.navigate",
            buildJsonObject { put("url", "https://example.com/") },
        )
        assertFalse(result.success)
        assertTrue(result.output.contains("无浏览器引擎"))
    }

    @Test
    fun `metadata address hard denied`() = runBlocking {
        val result = tools.invoke(
            "browser.navigate",
            buildJsonObject { put("url", "http://169.254.169.254/latest/meta-data/") },
        )
        assertFalse(result.success)
        assertTrue(result.output.contains("元数据"))
    }

    @Test
    fun `initiator kind participates in deny rules`() = runBlocking {
        HostActionPolicy.install(
            ActionPolicyDocument(
                deny = listOf("initiator.kind == 'schedule' && tool.name == 'browser.navigate'"),
                allow = listOf("true"),
            ),
        )
        val blocked = tools.invoke(
            "browser.navigate",
            buildJsonObject { put("url", "https://example.com/") },
            initiatorKind = "schedule",
        )
        assertFalse(blocked.success)
        assertTrue(blocked.output.contains("策略拒绝") || blocked.output.contains("deny"))

        val allowed = tools.invoke(
            "browser.navigate",
            buildJsonObject { put("url", "https://example.com/") },
            initiatorKind = "person",
        )
        assertFalse(allowed.success)
        assertTrue(allowed.output.contains("无浏览器引擎"))
    }
}
