package top.wkbin.taixu.ui.chat

import java.lang.reflect.Proxy
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.serialization.json.buildJsonObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import top.wkbin.taixu.core.model.SessionRunState
import top.wkbin.taixu.harness.*
import top.wkbin.taixu.harness.session.InteractiveSessionControl

class ChatMessageActionsTest {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
    private val current = MutableStateFlow("a")
    private val running = MutableStateFlow(false)
    private val states = MutableStateFlow<Map<String, SessionRunState>>(emptyMap())
    private val messages = MutableStateFlow<List<HarnessMessage>>(listOf(
        UserMessage("user", 0, "original"),
        ToolCall("tool", 1, HarnessTool.READ, buildJsonObject { }),
        AssistantText("assistant", 2, "answer"),
    ))
    private val calls = mutableListOf<List<String?>>()
    private val unused = Proxy.newProxyInstance(
        InteractiveSessionControl::class.java.classLoader, arrayOf(InteractiveSessionControl::class.java),
    ) { _, method, _ -> error("Unexpected call ${method.name}") } as InteractiveSessionControl
    private val control = object : InteractiveSessionControl by unused {
        override val currentSessionId = current
        override val running = this@ChatMessageActionsTest.running
        override val sessionRunStates = states
        override fun messagesForSession(sessionId: String) = this@ChatMessageActionsTest.messages
        override fun regenerateLast(targetSessionId: String?) { calls += listOf("regenerate", targetSessionId) }
        override fun retryToolCall(toolCallId: String, targetSessionId: String?) { calls += listOf("retry", targetSessionId, toolCallId) }
        override fun truncateAndResend(userMessageId: String, newText: String, targetSessionId: String?) { calls += listOf("edit", targetSessionId, userMessageId, newText) }
        override suspend fun deleteMessage(messageId: String, targetSessionId: String?) { calls += listOf("delete", targetSessionId, messageId) }
        override fun cancel(targetSessionId: String?) { calls += listOf("stop", targetSessionId) }
    }
    private val actions = ChatMessageActions(control, scope)
    @After fun close() { scope.cancel() }

    @Test fun `all message operations pass the rendered session explicitly`() {
        actions.regenerate("a", "assistant")
        actions.retryTool("a", "tool")
        actions.editAndResend("a", "user", "revised")
        actions.delete("a", "assistant")
        actions.stop("a")
        assertEquals(listOf(
            listOf("regenerate", "a"), listOf("retry", "a", "tool"),
            listOf("edit", "a", "user", "revised"), listOf("delete", "a", "assistant"),
            listOf("stop", "a"),
        ), calls)
    }

    @Test fun `callbacks from a previous session cannot act on the new foreground`() {
        current.value = "b"
        actions.regenerate("a", "assistant")
        actions.retryTool("a", "tool")
        actions.editAndResend("a", "user", "revised")
        actions.delete("a", "user")
        actions.stop("a")
        assertTrue(calls.isEmpty())
    }

    @Test fun `blank session never falls back to foreground`() {
        actions.regenerate("")
        actions.retryTool("", "tool")
        actions.editAndResend("", "user", "revised")
        actions.delete("", "user")
        actions.stop("")
        assertTrue(calls.isEmpty())
    }

    @Test fun `old message projection cannot edit or retry messages in the newly selected session`() {
        messages.value = emptyList()
        actions.regenerate("a", "assistant")
        actions.retryTool("a", "tool")
        actions.editAndResend("a", "user", "revised")
        actions.delete("a", "user")
        assertTrue(calls.isEmpty())
    }

    @Test fun `regeneration rejects an answer superseded since rendering`() {
        messages.value += AssistantText("new-answer", 3, "new")
        actions.regenerate("a", "assistant")
        assertTrue(calls.isEmpty())
        actions.regenerate("a", "new-answer")
        assertEquals(listOf(listOf("regenerate", "a")), calls)
    }

    @Test fun `message type and nonblank text are checked before dispatch`() {
        actions.retryTool("a", "user")
        actions.editAndResend("a", "assistant", "revised")
        actions.editAndResend("a", "user", " \n ")
        assertTrue(calls.isEmpty())
    }

    @Test fun `busy and waiting approval sessions reject mutations but permit stop`() {
        for (state in listOf(SessionRunState.RUNNING, SessionRunState.WAITING_APPROVAL)) {
            states.value = mapOf("a" to state)
            actions.regenerate("a", "assistant")
            actions.retryTool("a", "tool")
            actions.editAndResend("a", "user", "revised")
            actions.delete("a", "user")
        }
        assertTrue(calls.isEmpty())
        actions.stop("a")
        assertEquals(listOf(listOf("stop", "a")), calls)
    }

    @Test fun `foreground running mirror retains its mutation guard`() {
        running.value = true
        actions.regenerate("a", "assistant")
        actions.retryTool("a", "tool")
        actions.editAndResend("a", "user", "revised")
        actions.delete("a", "user")
        assertTrue(calls.isEmpty())
    }

    @Test fun `queued deletion is ignored if selection changes before execution`() {
        val dispatcher = QueuedDispatcher()
        val delayed = ChatMessageActions(control, scope + dispatcher)
        delayed.delete("a", "user")
        current.value = "b"
        dispatcher.drain()
        assertTrue(calls.isEmpty())
    }

    @Test fun `queued deletion rechecks message existence and run state`() {
        val dispatcher = QueuedDispatcher()
        val delayed = ChatMessageActions(control, scope + dispatcher)
        delayed.delete("a", "user")
        messages.value = emptyList()
        dispatcher.drain()
        assertTrue(calls.isEmpty())
        messages.value = listOf(UserMessage("user", 0, "original"))
        delayed.delete("a", "user")
        states.value = mapOf("a" to SessionRunState.RUNNING)
        dispatcher.drain()
        assertTrue(calls.isEmpty())
    }

    private class QueuedDispatcher : CoroutineDispatcher() {
        private val pending = ArrayDeque<Runnable>()
        override fun dispatch(context: CoroutineContext, block: Runnable) { pending.addLast(block) }
        fun drain() { while (pending.isNotEmpty()) pending.removeFirst().run() }
    }
}
