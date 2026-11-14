package top.wkbin.taixu.runtime.virtualdisplay

import org.junit.Assert.assertEquals
import org.junit.Test

class PhoneTaskNotifierTest {
    @Test
    fun channelIdsAndNotificationIdsMatch() {
        assertEquals("twin_task_need", PhoneTaskNotifier.CHANNEL_NEED)
        assertEquals("twin_task_progress", PhoneTaskNotifier.CHANNEL_PROGRESS)
        assertEquals("twin_task_result", PhoneTaskNotifier.CHANNEL_RESULT)
        assertEquals(71, PhoneTaskNotifier.ID_PROGRESS)
        assertEquals(72, PhoneTaskNotifier.ID_RESULT)
        assertEquals(73, PhoneTaskNotifier.ID_NEED)
    }

    @Test
    fun answerActionMirrorsConstants() {
        assertEquals("top.wkbin.taixu.action.ANSWER_AGENT_NEED", PhoneTaskActionReceiver.ACTION_ANSWER_AGENT_NEED)
        assertEquals("accepted", PhoneTaskActionReceiver.EXTRA_ACCEPTED)
        assertEquals("pause", PhoneTaskActionReceiver.KIND_PAUSE)
        assertEquals("confirm", PhoneTaskActionReceiver.KIND_CONFIRM)
        assertEquals("handoff", PhoneTaskActionReceiver.KIND_HANDOFF)
        assertEquals("takeover", PhoneTaskActionReceiver.KIND_TAKEOVER)
        assertEquals("need", PhoneTaskActionReceiver.KIND_NEED)
    }
}
