package top.wkbin.taixu.ui.chat

import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import top.wkbin.taixu.harness.QueuedPrompt
import top.wkbin.taixu.harness.session.QueuedPromptEditingControl

internal data class QueuedPromptEditState(
    val sessionId: String,
    val prompt: QueuedPrompt,
    val token: String,
    val text: String,
    val busy: Boolean = true,
    val error: String? = null,
)

/** Owns a separate draft; never reads or writes the main composer. */
internal class QueuedPromptEditor(
    private val scope: CoroutineScope,
    private val control: QueuedPromptEditingControl,
    sessions: StateFlow<String>,
    private val notice: (String) -> Unit,
) {
    private val sessions = sessions
    private val mutableState = MutableStateFlow<QueuedPromptEditState?>(null)
    val state = mutableState.asStateFlow()

    init {
        scope.launch {
            try { sessions.collect { id -> if (state.value?.sessionId?.let { it != id } == true) cancel() } }
            finally { cancel() }
        }
    }

    fun begin(prompt: QueuedPrompt) {
        if (state.value != null || prompt.isEditing) return
        val edit = QueuedPromptEditState(sessions.value, prompt, UUID.randomUUID().toString(), prompt.message.text)
        mutableState.value = edit
        scope.launch {
            try {
                val held = control.beginQueuedPromptEdit(edit.sessionId, prompt.id, edit.token)
                if (state.value?.token != edit.token) return@launch
                if (held == null) { cancel(); notice("消息已被处理、正在编辑或不支持编辑") }
                else mutableState.value = edit.copy(prompt = held, busy = false)
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { if (state.value?.token == edit.token) { cancel(); notice("暂时无法编辑，请重试") } }
            finally {
                // A dismissal can arrive before the asynchronous begin acquires its reservation.
                if (state.value?.token != edit.token) control.cancelQueuedPromptEdit(edit.sessionId, prompt.id, edit.token)
            }
        }
    }

    fun update(text: String) { state.value?.takeUnless { it.busy }?.let { mutableState.value = it.copy(text = text, error = null) } }

    fun save() {
        val edit = state.value?.takeUnless { it.busy } ?: return
        if (edit.text.isBlank() && edit.prompt.message.imageUrls.isEmpty()) return
        mutableState.value = edit.copy(busy = true, error = null)
        scope.launch {
            try {
                val saved = control.saveQueuedPromptEdit(edit.sessionId, edit.prompt.id, edit.token, edit.text)
                if (state.value?.token != edit.token) return@launch
                if (saved) mutableState.value = null
                else { cancel(); notice("消息已被处理或移除，未保存修改") }
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) {
                if (state.value?.token == edit.token) mutableState.value = edit.copy(error = "保存失败，修改已保留，请重试")
            }
        }
    }

    fun cancel() {
        val edit = mutableState.value ?: return
        mutableState.value = null
        control.cancelQueuedPromptEdit(edit.sessionId, edit.prompt.id, edit.token)
    }
}
