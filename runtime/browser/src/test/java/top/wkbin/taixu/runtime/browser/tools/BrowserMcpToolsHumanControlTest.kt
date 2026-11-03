package top.wkbin.taixu.runtime.browser.tools

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.buildJsonObject
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import top.wkbin.taixu.core.browser.BrowserHelpRequests
import top.wkbin.taixu.core.browser.BrowserHumanControl
import top.wkbin.taixu.core.browser.BrowserPreferences

/** 人接管时 Bot 页面动作拒绝、观测工具放行（OpenBot takeover）。 */
class BrowserMcpToolsHumanControlTest {

    private val tools = BrowserMcpTools(
        engines = emptyList(),
        engineSelector = { _ -> null },
        prefs = BrowserPreferences.DEFAULT,
    )
    private val helpEvents = mutableListOf<String>()
    private val helpListener = BrowserHelpRequests.Listener { tool, _ -> helpEvents += tool }

    @Before
    fun setUp() {
        helpEvents.clear()
        BrowserHelpRequests.resetDebounceForTest()
        BrowserHelpRequests.addListener(helpListener)
        BrowserHumanControl.setCoBrowsingEnabled(true)
    }

    @After
    fun tearDown() {
        BrowserHelpRequests.removeListener(helpListener)
        BrowserHelpRequests.resetDebounceForTest()
        BrowserHumanControl.setCoBrowsingEnabled(true)
        helpEvents.clear()
    }

    @Test
    fun `human control refuses navigate before engine lookup`() = runBlocking {
        BrowserHumanControl.setCoBrowsingEnabled(false)
        val result = tools.invoke("browser.navigate", buildJsonObject {})
        assertFalse(result.success)
        assertTrue(result.output.contains("接管") || result.output.contains("拒绝"))
        assertTrue(helpEvents.contains("browser.navigate"))
    }

    @Test
    fun `human control still allows snapshot observation path to reach engine check`() = runBlocking {
        BrowserHumanControl.setCoBrowsingEnabled(false)
        // 无引擎时只读观测会落到「无浏览器引擎」，说明未被接管闸门提前拒绝。
        val result = tools.invoke("browser.snapshot", buildJsonObject {})
        assertFalse(result.success)
        assertFalse(result.output.contains("接管"))
        assertTrue(result.output.contains("无浏览器引擎"))
    }
}
