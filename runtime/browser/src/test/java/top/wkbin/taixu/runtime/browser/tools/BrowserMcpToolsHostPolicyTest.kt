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
import top.wkbin.taixu.core.browser.BrowserDescriptor
import top.wkbin.taixu.core.browser.BrowserFamily
import top.wkbin.taixu.core.browser.BrowserHumanControl
import top.wkbin.taixu.core.browser.BrowserPreferences
import top.wkbin.taixu.core.browser.HostActionPolicy
import top.wkbin.taixu.core.browser.HostActionPolicyPresets
import top.wkbin.taixu.core.browser.PageSnapshot
import top.wkbin.taixu.core.model.ToolImageRef
import top.wkbin.taixu.runtime.browser.BrowserEngine
import top.wkbin.taixu.runtime.browser.BrowserEvent
import top.wkbin.taixu.runtime.browser.BrowserEventBus
import top.wkbin.taixu.runtime.browser.BrowserSessionToken

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

    @Test
    fun `click without url uses the current tab host`() = runBlocking {
        HostActionPolicy.install(HostActionPolicyPresets.ALLOWLIST_EXAMPLE.document)
        val blocked = BrowserMcpTools(listOf(TabUrlEngine("https://evil.test/a")), { null }, BrowserPreferences.DEFAULT)
        val denied = blocked.invoke("browser.click", buildJsonObject { put("ref", "e1") })
        assertFalse(denied.success)
        assertTrue(denied.output.contains("策略拒绝") || denied.output.contains("deny"))

        val allowedEngine = TabUrlEngine("https://evil.test/stale")
        allowedEngine.eventBus.publish(
            BrowserEvent.PageChanged(tabId = "t1", url = "https://example.com/ok", title = "ok"),
        )
        val allowed = BrowserMcpTools(listOf(allowedEngine), { null }, BrowserPreferences.DEFAULT)
        val result = allowed.invoke("browser.type", buildJsonObject { put("ref", "e1"); put("text", "hi") })
        assertTrue(result.output, result.success)
        assertTrue(allowedEngine.acted)
    }

    @Test
    fun `explicit url overrides the current tab`() = runBlocking {
        HostActionPolicy.install(HostActionPolicyPresets.ALLOWLIST_EXAMPLE.document)
        val engine = TabUrlEngine("https://example.com/")
        val tools = BrowserMcpTools(listOf(engine), { null }, BrowserPreferences.DEFAULT)
        val denied = tools.invoke(
            "browser.click",
            buildJsonObject { put("ref", "e1"); put("url", "https://evil.test/") },
        )
        assertFalse(denied.success)
        assertFalse(engine.acted)
        assertTrue(denied.output.contains("策略拒绝") || denied.output.contains("deny"))
    }

    @Test
    fun `allowlist fails closed when the current host cannot be determined`() = runBlocking {
        HostActionPolicy.install(HostActionPolicyPresets.ALLOWLIST_EXAMPLE.document)
        val noEngine = tools.invoke("browser.scroll", buildJsonObject { put("deltaY", 10) })
        assertFalse(noEngine.success)
        assertTrue(noEngine.output.contains("无法确定当前页面主机"))

        val blank = TabUrlEngine("")
        val withEngine = BrowserMcpTools(listOf(blank), { null }, BrowserPreferences.DEFAULT)
        val denied = withEngine.invoke("browser.press", buildJsonObject { put("key", "Enter") })
        assertFalse(denied.success)
        assertTrue(denied.output.contains("无法确定当前页面主机"))
        assertFalse(blank.acted)
    }

    @Test
    fun `default policy still reports a missing engine when url is omitted`() = runBlocking {
        val result = tools.invoke("browser.click", buildJsonObject { put("ref", "e1") })
        assertFalse(result.success)
        assertTrue(result.output.contains("无浏览器引擎"))
    }
}

private class TabUrlEngine(tokenUrl: String) : BrowserEngine {
    override val eventBus = BrowserEventBus()
    override val descriptor = BrowserDescriptor(
        family = BrowserFamily.IN_APP,
        displayName = "tab-url",
        healthy = true,
        capabilities = emptySet(),
    )
    private val active = BrowserSessionToken("t1", BrowserFamily.IN_APP, url = tokenUrl)
    var acted = false

    override suspend fun openTab(url: String?, activate: Boolean) = active
    override suspend fun navigate(tab: BrowserSessionToken, url: String) = Unit
    override suspend fun snapshot(tab: BrowserSessionToken, maxElements: Int) =
        PageSnapshot(tab.tabId, active.url, "t", emptyMap(), "fake", 0L)
    override suspend fun click(
        tab: BrowserSessionToken,
        ref: String,
        refSelectorLookup: suspend (BrowserSessionToken, String) -> String?,
    ): Boolean { acted = true; return true }
    override suspend fun typeInto(
        tab: BrowserSessionToken,
        ref: String,
        text: String,
        refSelectorLookup: suspend (BrowserSessionToken, String) -> String?,
    ): Boolean { acted = true; return true }
    override suspend fun press(
        tab: BrowserSessionToken,
        ref: String?,
        key: String,
        refSelectorLookup: suspend (BrowserSessionToken, String) -> String?,
    ): Boolean { acted = true; return true }
    override suspend fun scroll(tab: BrowserSessionToken, deltaY: Int): Boolean { acted = true; return true }
    override suspend fun screenshot(tab: BrowserSessionToken, prefs: BrowserPreferences): ToolImageRef? = null
    override suspend fun evaluate(tab: BrowserSessionToken, script: String): String? = ""
    override suspend fun pageSource(tab: BrowserSessionToken, maxBytes: Int) = ""
    override suspend fun back(tab: BrowserSessionToken) = false
    override suspend fun forward(tab: BrowserSessionToken) = false
    override suspend fun refresh(tab: BrowserSessionToken) = Unit
    override suspend fun closeTab(tab: BrowserSessionToken) = Unit
    override suspend fun listTabs() = listOf(active)
    override fun activeTab(): BrowserSessionToken = active
    override suspend fun setActiveTab(tab: BrowserSessionToken) = Unit
    override suspend fun cookiesGet(tab: BrowserSessionToken, url: String?) = ""
    override suspend fun cookiesSet(tab: BrowserSessionToken, url: String, headerLine: String) = Unit
    override suspend fun cookiesDelete(tab: BrowserSessionToken, url: String, name: String) = Unit
    override suspend fun localGet(tab: BrowserSessionToken, key: String): String? = null
    override suspend fun localSet(tab: BrowserSessionToken, key: String, value: String) = Unit
    override suspend fun localDelete(tab: BrowserSessionToken, key: String) = Unit
    override suspend fun localKeys(tab: BrowserSessionToken) = emptyList<String>()
    override suspend fun sessionGet(tab: BrowserSessionToken, key: String): String? = null
    override suspend fun sessionSet(tab: BrowserSessionToken, key: String, value: String) = Unit
    override suspend fun sessionDelete(tab: BrowserSessionToken, key: String) = Unit
    override suspend fun sessionKeys(tab: BrowserSessionToken) = emptyList<String>()
    override suspend fun shutdown() = Unit
}
