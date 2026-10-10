package top.wkbin.taixu.harness.workflow

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import top.wkbin.taixu.core.database.WorkflowRepository
import top.wkbin.taixu.core.model.workflow.WorkflowRuntimeState

/** Failed checkpoints remain observable; terminal snapshots get bounded retries. */
internal class WorkflowHistoryWriter(
    private val repository: WorkflowRepository,
    private val reportFailure: (String, Throwable) -> Unit,
) {
    private val failures = MutableStateFlow<Map<String, String>>(emptyMap())
    val errors: StateFlow<Map<String, String>> = failures.asStateFlow()

    suspend fun save(state: WorkflowRuntimeState, source: String, scheduleId: String?): Boolean {
        val attempts = if (state.status in WorkflowRunManager.TERMINAL) 3 else 1
        repeat(attempts) { attempt ->
            try {
                repository.saveExecution(state, source, scheduleId)
                failures.update { it - state.executionId }
                return true
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                failures.update { current ->
                    (current - state.executionId + (state.executionId to (error.message ?: "工作流历史保存失败")))
                        .entries.toList().takeLast(64).associate { it.key to it.value }
                }
                reportFailure("保存工作流历史失败：${state.executionId} / ${state.status}（${attempt + 1}/$attempts）", error)
                if (attempt + 1 < attempts) delay(500L * (attempt + 1))
            }
        }
        return false
    }

    fun forget(executionId: String) { failures.update { it - executionId } }
}
