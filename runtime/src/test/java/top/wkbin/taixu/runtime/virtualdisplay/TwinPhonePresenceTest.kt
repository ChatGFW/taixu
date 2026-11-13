package top.wkbin.taixu.runtime.virtualdisplay

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TwinPhonePresenceTest {
    @Test
    fun embeddedVisibleDefaultsOffAndToggles() {
        TwinPhonePresence.embeddedVisible = false
        assertFalse(TwinPhonePresence.embeddedVisible)
        TwinPhonePresence.embeddedVisible = true
        assertTrue(TwinPhonePresence.embeddedVisible)
        TwinPhonePresence.embeddedVisible = false
        assertFalse(TwinPhonePresence.embeddedVisible)
    }

    @Test
    fun requestOpenIncrementsGeneration() {
        TwinPhonePresence.unpinMini()
        val before = TwinPhonePresence.openGeneration.value
        assertTrue(TwinPhonePresence.requestOpen())
        assertEquals(before + 1L, TwinPhonePresence.openGeneration.value)
        assertTrue(TwinPhonePresence.miniPinned.value)
        assertTrue(TwinPhonePresence.requestOpen())
        assertEquals(before + 2L, TwinPhonePresence.openGeneration.value)
    }

    @Test
    fun clearStickyUnpinsMini() {
        TwinPhonePresence.pinMini()
        TwinPhonePresence.showResultSticky("done", success = true)
        assertTrue(TwinPhonePresence.miniPinned.value)
        TwinPhonePresence.clearStickyResult()
        assertFalse(TwinPhonePresence.miniPinned.value)
    }

    @Test
    fun requestExpandIncrementsExpandAndOpen() {
        val openBefore = TwinPhonePresence.openGeneration.value
        val expandBefore = TwinPhonePresence.expandGeneration.value
        assertTrue(TwinPhonePresence.requestExpand())
        assertEquals(openBefore + 1L, TwinPhonePresence.openGeneration.value)
        assertEquals(expandBefore + 1L, TwinPhonePresence.expandGeneration.value)
    }

    @Test
    fun showResultStickyHoldsDoneThenClearable() {
        TwinPhonePresence.clearStickyResult()
        TwinPhonePresence.showResultSticky("ok", success = true)
        val sticky = TwinPhonePresence.stickyResult.value
        assertTrue(sticky != null)
        assertEquals(TwinPhoneStickyKind.Done, sticky!!.kind)
        TwinPhonePresence.clearStickyResult()
        assertEquals(null, TwinPhonePresence.stickyResult.value)
    }

    @Test
    fun phoneSurfaceOpenBlocksDrawerGesturesFlag() {
        TwinPhonePresence.setPhoneSurfaceOpen(false)
        assertFalse(TwinPhonePresence.phoneSurfaceOpen.value)
        TwinPhonePresence.setPhoneSurfaceOpen(true)
        assertTrue(TwinPhonePresence.phoneSurfaceOpen.value)
        TwinPhonePresence.setPhoneSurfaceOpen(false)
        assertFalse(TwinPhonePresence.phoneSurfaceOpen.value)
    }
}
