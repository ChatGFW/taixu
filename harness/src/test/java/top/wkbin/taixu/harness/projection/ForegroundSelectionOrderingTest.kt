package top.wkbin.taixu.harness.projection

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import org.junit.Assert.*
import org.junit.Test

class ForegroundSelectionOrderingTest {
    private val tracker = CurrentSessionTracker()

    @Test fun `preparing a new session preserves the published selection`() {
        val previous = tracker.select("old")
        tracker.reserveSelection()
        assertEquals("old", tracker.foregroundId)
        assertTrue(tracker.isSelectionCurrent(previous, "old"))
    }

    @Test fun `a later load prevents an earlier creation from taking foreground`() {
        val creation = tracker.reserveSelection()
        val load = tracker.select("chosen")
        assertFalse(tracker.selectIfLatest(creation, "created"))
        assertTrue(tracker.isSelectionCurrent(load, "chosen"))
    }

    @Test fun `only the newest pending creation can publish`() {
        val first = tracker.reserveSelection()
        val second = tracker.reserveSelection()
        assertTrue(tracker.selectIfLatest(second, "second"))
        assertFalse(tracker.selectIfLatest(first, "first"))
        assertEquals("second", tracker.foregroundId)
    }

    @Test fun `newer request blocks earlier creation even before it publishes`() {
        val first = tracker.reserveSelection()
        val second = tracker.reserveSelection()
        assertFalse(tracker.selectIfLatest(first, "first"))
        assertEquals("", tracker.foregroundId)
        assertTrue(tracker.selectIfLatest(second, "second"))
    }

    @Test fun `reloading the same id invalidates the previous projection ticket`() {
        val first = tracker.select("same")
        val second = tracker.select("same")
        assertFalse(tracker.isSelectionCurrent(first, "same"))
        assertTrue(tracker.isSelectionCurrent(second, "same"))
    }

    @Test fun `legacy setter invalidates a pending creation`() {
        val creation = tracker.reserveSelection()
        tracker.setCurrent("manual")
        assertFalse(tracker.selectIfLatest(creation, "created"))
        assertTrue(tracker.isForeground("manual"))
    }

    @Test fun `synchronous observer navigation wins during publication`() {
        val scope = CoroutineScope(Dispatchers.Unconfined)
        try {
            scope.launch(start = CoroutineStart.UNDISPATCHED) {
                tracker.currentSessionId.collect { if (it == "created") tracker.select("manual") }
            }
            assertFalse(tracker.selectIfLatest(tracker.reserveSelection(), "created"))
            assertEquals("manual", tracker.foregroundId)
        } finally { scope.cancel() }
    }
}
