package top.wkbin.taixu.harness.bot

import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.Job
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import top.wkbin.taixu.core.database.repository.BotAssignmentRepository
import top.wkbin.taixu.core.model.bot.BotAssignment
import top.wkbin.taixu.core.model.bot.BotAssignmentStatus

data class ActiveBotAssignment(
    val taskId: String,
    val groupId: String?,
    val botName: String,
    val startedAt: Long,
)

/** 持久化 + 进程内 Job 句柄：群聊据此显示执行态、停止任务并查询历史。 */
class BotAssignmentTracker(
    private val repository: BotAssignmentRepository,
) {
    private val jobs = ConcurrentHashMap<String, Job>()
    private val jobGroups = ConcurrentHashMap<String, String>()
    private val _active = MutableStateFlow<List<ActiveBotAssignment>>(emptyList())
    val active: StateFlow<List<ActiveBotAssignment>> = _active.asStateFlow()

    suspend fun start(assignment: BotAssignment, job: Job?) {
        repository.save(assignment)
        if (job != null) {
            jobs[assignment.id] = job
            assignment.groupId?.let { jobGroups[assignment.id] = it }
            job.invokeOnCompletion {
                jobs.remove(assignment.id, job)
                jobGroups.remove(assignment.id)
            }
        }
        _active.update { current ->
            current + ActiveBotAssignment(
                taskId = assignment.id,
                groupId = assignment.groupId,
                botName = assignment.botName,
                startedAt = assignment.startedAt,
            )
        }
    }

    suspend fun finish(
        taskId: String,
        status: BotAssignmentStatus,
        summary: String? = null,
        error: String? = null,
    ) {
        repository.finish(taskId, status, summary, error)
        _active.update { current -> current.filterNot { it.taskId == taskId } }
    }

    suspend fun cancel(taskId: String) {
        val job = jobs[taskId]
        if (job != null) {
            job.cancel()
        }
        repository.finish(taskId, BotAssignmentStatus.CANCELLED, error = "用户取消")
        _active.update { current -> current.filterNot { it.taskId == taskId } }
    }

    suspend fun cancelGroup(groupId: String) {
        val tasks = _active.value.filter { it.groupId == groupId }
        val pending = jobGroups.filterValues { it == groupId }.keys.mapNotNull { jobs[it] }
        tasks.forEach { assignment ->
            cancel(assignment.taskId)
        }
        // 清空会话前排空 finally 中的结果通知，防止删除后又写回旧消息。
        pending.joinAll()
        _active.update { current -> current.filterNot { it.groupId == groupId } }
    }

    fun observeForGroup(groupId: String): Flow<List<BotAssignment>> = repository.observeForGroup(groupId)

    suspend fun listForGroup(groupId: String): List<BotAssignment> =
        repository.observeForGroup(groupId).first()

    suspend fun listRecent(limit: Int = 20): List<BotAssignment> = repository.listRecent(limit)

    suspend fun get(taskId: String): BotAssignment? = repository.get(taskId)

    suspend fun recoverOrphaned() = repository.failRunning()
}
