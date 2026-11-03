package top.wkbin.taixu.core.browser

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class BrowserHumanControlTest {

    private val events = mutableListOf<Boolean>()
    private val listener = BrowserHumanControl.CoBrowsingListener { enabled -> events += enabled }

    @Before
    fun setUp() {
        events.clear()
        BrowserHumanControl.addListener(listener)
        BrowserHumanControl.setCoBrowsingEnabled(true)
        events.clear()
    }

    @After
    fun tearDown() {
        BrowserHumanControl.removeListener(listener)
        BrowserHumanControl.setCoBrowsingEnabled(true)
        events.clear()
    }

    @Test
    fun `disabling co-browsing means human has control`() {
        assertFalse(BrowserHumanControl.humanHasControl())
        BrowserHumanControl.setCoBrowsingEnabled(false)
        assertTrue(BrowserHumanControl.humanHasControl())
        BrowserHumanControl.setCoBrowsingEnabled(true)
        assertFalse(BrowserHumanControl.humanHasControl())
    }

    @Test
    fun `page acting tools are refused while read-only observation is allowed`() {
        assertTrue(BrowserHumanControl.isPageActingTool("browser.navigate"))
        assertTrue(BrowserHumanControl.isPageActingTool("browser.click"))
        assertTrue(BrowserHumanControl.isPageActingTool("browser.type"))
        assertTrue(BrowserHumanControl.isPageActingTool("browser.open"))
        assertFalse(BrowserHumanControl.isPageActingTool("browser.snapshot"))
        assertFalse(BrowserHumanControl.isPageActingTool("browser.current_url"))
        assertFalse(BrowserHumanControl.isPageActingTool("browser.list_tabs"))
        assertFalse(BrowserHumanControl.isPageActingTool("browser.debug_state"))
    }

    @Test
    fun `refuse message names handover without claiming queue`() {
        val message = BrowserHumanControl.refuseMessage()
        assertTrue(message.contains("拒绝"))
        assertTrue(message.contains("未排队"))
        assertTrue(message.contains("共浏览"))
    }

    @Test
    fun `listener fires only on actual change`() {
        BrowserHumanControl.setCoBrowsingEnabled(true)
        assertTrue(events.isEmpty())
        BrowserHumanControl.setCoBrowsingEnabled(false)
        BrowserHumanControl.setCoBrowsingEnabled(false)
        BrowserHumanControl.setCoBrowsingEnabled(true)
        assertEquals(listOf(false, true), events)
    }
}
