package top.wkbin.taixu.harness.queue

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import top.wkbin.taixu.harness.QueuedPrompt
import top.wkbin.taixu.harness.session.QueuedPromptEditingControl
import top.wkbin.taixu.harness.session.SessionTurnCoordinator

/** Serializes foreground queue mutations with input admission and run completion. */
internal class QueuedPromptActions(
    private val queues: PromptQueueManager,
    private val coordinator: SessionTurnCoordinator,
    private val scope: CoroutineScope,
    private val busy: (String) -> Boolean,
    private val startNext: suspend (String) -> Unit,
    private val refresh: suspend (String) -> Unit,
    private val cancelTask: suspend (String) -> Unit,
    private val failure: (String, Exception) -> Unit,
) : QueuedPromptEditingControl {
    private fun launch(sessionId: String, block: suspend () -> Unit) {
        scope.launch {
            try { coordinator.withSessionMutex(sessionId) { block() } }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { failure(sessionId, e) }
        }
    }

    /** A committed edit remains successful even when scheduling/projection needs retry. */
    private suspend fun afterCommit(sessionId: String, resume: Boolean = true) {
        try {
            if (resume && !busy(sessionId)) startNext(sessionId)
            refresh(sessionId)
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) { failure(sessionId, e) }
    }

    override fun removeQueuedPromptById(sessionId: String, itemId: String) = launch(sessionId) {
        queues.removeById(sessionId, itemId)?.message?.taskId?.let { cancelTask(it) }
        afterCommit(sessionId)
    }

    override suspend fun convertQueuedPromptToSteer(sessionId: String, itemId: String): Boolean =
        coordinator.withSessionMutex(sessionId) {
            val converted = busy(sessionId) && queues.convertToSteer(sessionId, itemId)
            if (converted) afterCommit(sessionId, resume = false)
            converted
        }

    override suspend fun beginQueuedPromptEdit(sessionId: String, itemId: String, token: String): QueuedPrompt? =
        coordinator.withSessionMutex(sessionId) {
            queues.beginEdit(sessionId, itemId, token).also { afterCommit(sessionId, resume = false) }
        }

    override suspend fun saveQueuedPromptEdit(sessionId: String, itemId: String, token: String, text: String): Boolean =
        coordinator.withSessionMutex(sessionId) {
            val saved = queues.saveEdit(sessionId, itemId, token, text)
            if (saved) afterCommit(sessionId)
            saved
        }

    override fun cancelQueuedPromptEdit(sessionId: String, itemId: String, token: String) = launch(sessionId) {
        queues.cancelEdit(sessionId, itemId, token)
        afterCommit(sessionId)
    }
}
