package top.wkbin.taixu.harness.core

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

sealed interface ToolGateDecision {
    data object Allow : ToolGateDecision
    data class Block(val reason: String) : ToolGateDecision
}

data class ToolCheckpointBlock(val checkpointId: String, val reason: String)
data class ToolCheckpointNote(val checkpointId: String, val text: String)

/** Trusted code callbacks: veto a call or annotate its result; no argument/result replacement. */
interface ToolCheckpoint<Request, Result> {
    val id: String
    suspend fun before(request: Request): ToolGateDecision = ToolGateDecision.Allow
    suspend fun after(request: Request, result: Result): String? = null
}

/** Awaited control checkpoints, independent of lossy observation events and persistence. */
class ToolCheckpoints<Request, Result>(checkpoints: List<ToolCheckpoint<Request, Result>> = emptyList()) {
    private val registrations = checkpoints.map { it.id to it }

    init {
        require(registrations.size <= 32) { "Too many tool checkpoints" }
        require(registrations.map { it.first }.distinct().size == registrations.size) { "Duplicate tool checkpoint id" }
        require(registrations.all { it.first.matches(Regex("[A-Za-z0-9._-]{1,64}")) }) { "Invalid tool checkpoint id" }
    }

    suspend fun before(request: Request): ToolCheckpointBlock? {
        currentCoroutineContext().ensureActive()
        for ((id, checkpoint) in registrations) {
            val decision = try {
                checkpoint.before(request)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (failure: Throwable) {
                currentCoroutineContext().ensureActive()
                // Never expose exception messages, which may contain credentials or raw payloads.
                return ToolCheckpointBlock(id, "执行前检查点失败（${failure::class.simpleName}），本次调用未执行。")
            }
            currentCoroutineContext().ensureActive()
            if (decision is ToolGateDecision.Block) {
                return ToolCheckpointBlock(id, decision.reason.take(MAX_NOTE_CHARS).ifBlank { "工具调用被扩展检查点阻止。" })
            }
        }
        return null
    }

    suspend fun after(request: Request, result: Result): List<ToolCheckpointNote> {
        currentCoroutineContext().ensureActive()
        val notes = mutableListOf<ToolCheckpointNote>()
        for ((id, checkpoint) in registrations) {
            val text = try {
                checkpoint.after(request, result)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (failure: Throwable) {
                currentCoroutineContext().ensureActive()
                // A failed annotation must not turn an already executed effect into a retryable tool failure.
                "结果检查点失败（${failure::class.simpleName}），工具结果状态保持不变。"
            }
            currentCoroutineContext().ensureActive()
            if (!text.isNullOrBlank()) notes += ToolCheckpointNote(id, text.take(MAX_NOTE_CHARS))
        }
        return notes
    }

    companion object { const val MAX_NOTE_CHARS = 4_096 }
}
