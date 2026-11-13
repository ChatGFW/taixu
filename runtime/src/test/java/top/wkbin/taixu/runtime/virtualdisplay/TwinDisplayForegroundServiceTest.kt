package top.wkbin.taixu.runtime.virtualdisplay

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class TwinDisplayForegroundServiceTest {
    @Test
    fun notificationIdsDoNotCollideWithTaskProgress() {
        assertEquals(737, TwinDisplayForegroundService.NOTIFICATION_ID)
        assertEquals(71, PhoneTaskNotifier.ID_PROGRESS)
        assertEquals(72, PhoneTaskNotifier.ID_RESULT)
        assertEquals(73, PhoneTaskNotifier.ID_NEED)
        assertNotEquals(TwinDisplayForegroundService.NOTIFICATION_ID, PhoneTaskNotifier.ID_PROGRESS)
    }

    @Test
    fun channelIsDistinctFromTaskChannels() {
        assertEquals("twin_display", TwinDisplayForegroundService.CHANNEL_ID)
        assertNotEquals(TwinDisplayForegroundService.CHANNEL_ID, PhoneTaskNotifier.CHANNEL_PROGRESS)
    }
}
