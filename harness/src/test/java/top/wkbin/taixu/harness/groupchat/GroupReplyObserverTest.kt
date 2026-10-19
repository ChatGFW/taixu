package top.wkbin.taixu.harness.groupchat

import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import top.wkbin.taixu.core.common.logging.AppLogger
import top.wkbin.taixu.core.model.SessionRunState
import top.wkbin.taixu.core.model.bot.Bot
import top.wkbin.taixu.harness.AssistantText
import top.wkbin.taixu.harness.HarnessMessage
import top.wkbin.taixu.harness.queue.PromptQueue
import top.wkbin.taixu.harness.session.PromptSubmission
import top.wkbin.taixu.harness.session.SessionControl

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@OptIn(ExperimentalCoroutinesApi::class)
class GroupReplyObserverTest {
    private val log = AppLogger(ApplicationProvider.getApplicationContext()) { it }
    private val bot = Bot("bot", "name", "role", "persona", "seed", sessionId = "session", createdAt = 0, updatedAt = 0)

    @Test fun `new tokens publish immediately and unchanged input causes no refresh loop`() = runTest {
        val control = FakeControl()
        val updates = mutableListOf<GroupReplyProgress>()
        val result = async { GroupReplyObserver(control, log) { testScheduler.currentTime }
            .awaitReply("session", bot, emptySet(), 0) { updates += it } }
        runCurrent()
        val initialCount = updates.size
        advanceTimeBy(1_000)
        runCurrent()
        assertEquals(initialCount, updates.size)
        control.messages.value = listOf(AssistantText("one", 1_000, "", reasoning = "first token"))
        runCurrent()
        assertEquals(1_000L, testScheduler.currentTime)
        assertEquals("first token", updates.last().reasoning)
        control.messages.value = listOf(AssistantText("one", 1_000, "done", reasoning = "first token", totalMs = 1))
        runCurrent()
        assertEquals("done", result.await().candidate?.text)
    }

    @Test fun `reasoning updates are separate from body and preserve earlier reasoning`() = runTest {
        val control = FakeControl()
        val updates = mutableListOf<GroupReplyProgress>()
        val result = async { GroupReplyObserver(control, log) { testScheduler.currentTime }
            .awaitReply("session", bot, emptySet(), 0, updates::add) }
        control.messages.value = listOf(AssistantText("one", 0, "", reasoning = "thought ".repeat(200)))
        advanceTimeBy(150)
        runCurrent()
        assertTrue(updates.last().content.isEmpty())
        assertTrue(updates.last().reasoning.length > 600)
        control.messages.value += AssistantText("two", 150, "answer", reasoning = "next thought", totalMs = 200)
        control.sessionRunStates.value = mapOf("session" to SessionRunState.COMPLETED)
        advanceTimeBy(100)
        val outcome = result.await()
        assertEquals("answer", outcome.candidate?.text)
        assertTrue(outcome.reasoning.contains("next thought"))
        assertTrue(outcome.reasoning.startsWith("thought"))
    }

    @Test fun `waiting for approval does not finish the reply or trip the first token timeout`() = runTest {
        val control = FakeControl()
        control.sessionRunStates.value = mapOf("session" to SessionRunState.WAITING_APPROVAL)
        val result = async { GroupReplyObserver(control, log) { testScheduler.currentTime }
            .awaitReply("session", bot, emptySet(), 0) { } }
        advanceTimeBy(125_000)
        runCurrent()
        assertFalse(result.isCompleted)
        assertTrue(control.cancelled.isEmpty())
        control.messages.value = listOf(AssistantText("reply", 125_000, "resumed", totalMs = 1))
        control.sessionRunStates.value = mapOf("session" to SessionRunState.COMPLETED)
        advanceTimeBy(100)
        assertEquals("resumed", result.await().candidate?.text)
    }

    private class FakeControl : SessionControl {
        override val currentSessionId = MutableStateFlow("session")
        override val sessionRunStates = MutableStateFlow(mapOf("session" to SessionRunState.RUNNING))
        override val sessionStatuses = MutableStateFlow<Map<String, String>>(emptyMap())
        val messages = MutableStateFlow<List<HarnessMessage>>(emptyList())
        val cancelled = mutableListOf<String?>()
        override fun messagesForSession(sessionId: String) = messages
        override suspend fun prepareRemoteSession(sessionId: String) = messages.value
        override suspend fun persistedMessages(sessionId: String) = messages.value
        override suspend fun deleteSession(id: String) = Unit
        override suspend fun submit(sessionId: String, text: String, imageUrls: List<String>, queue: PromptQueue): PromptSubmission = error("unused")
        override fun send(text: String, targetSessionId: String?, imageUrls: List<String>) = Unit
        override fun steer(text: String, targetSessionId: String?, imageUrls: List<String>) = Unit
        override fun followUp(text: String, targetSessionId: String?, imageUrls: List<String>) = Unit
        override fun cancel(targetSessionId: String?) { cancelled += targetSessionId }
        override fun resolveApproval(requestId: String, approved: Boolean, rememberForSession: Boolean) = Unit
        override fun resolveQuestion(requestId: String, answersJson: String) = Unit
    }
}
