package top.wkbin.taixu.ui.chat

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import top.wkbin.taixu.core.model.SessionRunState
import top.wkbin.taixu.harness.AssistantText
import top.wkbin.taixu.harness.ToolCall
import top.wkbin.taixu.harness.UserMessage
import top.wkbin.taixu.harness.session.InteractiveSessionControl

/** Message controls carry the rendered session identity through asynchronous dispatch. */
internal class ChatMessageActions(
    private val control: InteractiveSessionControl,
    private val scope: CoroutineScope,
) {
    private fun editable(sessionId: String): Boolean =
        sessionId.isNotBlank() && control.currentSessionId.value == sessionId &&
            !control.running.value && control.sessionRunStates.value[sessionId] !in
            setOf(SessionRunState.RUNNING, SessionRunState.WAITING_APPROVAL)

    fun regenerate(sessionId: String, expectedAssistantId: String? = null) {
        if (!editable(sessionId)) return
        if (expectedAssistantId != null && control.messagesForSession(sessionId).value
                .filterIsInstance<AssistantText>().lastOrNull()?.id != expectedAssistantId) return
        control.regenerateLast(sessionId)
    }

    fun retryTool(sessionId: String, toolCallId: String) {
        if (!editable(sessionId)) return
        if (control.messagesForSession(sessionId).value.none { it.id == toolCallId && it is ToolCall }) return
        control.retryToolCall(toolCallId, sessionId)
    }

    fun editAndResend(sessionId: String, messageId: String, text: String) {
        if (!editable(sessionId) || text.isBlank()) return
        if (control.messagesForSession(sessionId).value.none { it.id == messageId && it is UserMessage }) return
        control.truncateAndResend(messageId, text, sessionId)
    }

    fun delete(sessionId: String, messageId: String) = scope.launch {
        if (!editable(sessionId)) return@launch
        if (control.messagesForSession(sessionId).value.none { it.id == messageId }) return@launch
        control.deleteMessage(messageId, sessionId)
    }

    fun stop(sessionId: String) {
        if (sessionId.isNotBlank() && control.currentSessionId.value == sessionId) control.cancel(sessionId)
    }
}
