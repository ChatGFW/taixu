package top.wkbin.taixu.core.browser

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class BrowserHelpRequestsTest {
    private val events = mutableListOf<String>()
    private val listener = BrowserHelpRequests.Listener { tool, _ -> events += tool }

    @Before
    fun setUp() {
        events.clear()
        BrowserHelpRequests.resetDebounceForTest()
        BrowserHelpRequests.addListener(listener)
    }

    @After
    fun tearDown() {
        BrowserHelpRequests.removeListener(listener)
        BrowserHelpRequests.resetDebounceForTest()
        events.clear()
    }

    @Test
    fun `debounce suppresses same tool within window`() {
        BrowserHelpRequests.notifyPageActionRefused("browser.navigate")
        BrowserHelpRequests.notifyPageActionRefused("browser.navigate")
        BrowserHelpRequests.notifyPageActionRefused("browser.click")
        assertEquals(listOf("browser.navigate", "browser.click"), events)
    }
}
