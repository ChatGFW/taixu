package top.wkbin.taixu.harness.bot

import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withContext
import top.wkbin.taixu.core.common.logging.AppLogger
import top.wkbin.taixu.core.database.HarnessSessionEntity
import top.wkbin.taixu.core.database.HarnessSessionRepository
import top.wkbin.taixu.core.database.repository.BotAssignmentRepository
import top.wkbin.taixu.core.database.repository.GroupChatRepository
import top.wkbin.taixu.core.database.repository.PersonaBotRepository
import top.wkbin.taixu.core.model.ApprovalMode
import top.wkbin.taixu.core.model.SessionRunState
import top.wkbin.taixu.core.model.bot.BotAssignment
import top.wkbin.taixu.core.model.bot.BotAssignmentRequest
import top.wkbin.taixu.core.model.bot.BotAssignmentStatus
import top.wkbin.taixu.harness.groupchat.GroupTurnOutcome
import top.wkbin.taixu.harness.session.SessionControl

fun interface BotAssignmentExecutor {
    suspend fun execute(task: BotAssignment, request: BotAssignmentRequest, admitted: suspend (String?) -> Unit): GroupTurnOutcome
}

/** Each task owns one native root session. Later instructions become durable turns in that session. */
class BotAssignmentRuntime(
    private val repository: BotAssignmentRepository,
    private val groups: GroupChatRepository,
    private val bots: PersonaBotRepository,
    private val sessions: HarnessSessionRepository,
    private val control: SessionControl,
    private val tracker: BotAssignmentTracker,
    private val executor: BotAssignmentExecutor,
    private val logger: AppLogger,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
) {
    private val pump = AssignmentRequestPump(repository, scope, logger, ::execute)
    suspend fun start() = pump.start()

    suspend fun create(botId: String, groupId: String, requesterId: String, sourceSessionId: String, instruction: String,
        writePaths: List<String> = emptyList()): BotAssignment {
        start()
        require(instruction.isNotBlank()) { "任务内容不能为空" }
        val bot = requireNotNull(bots.getBot(botId)) { "执行成员不存在" }
        val source = requireNotNull(sessions.findById(sourceSessionId)) { "来源会话不存在" }
        require(groups.getMembers(groupId).any { it.id == requesterId }) { "请求者不在群内" }
        val now = System.currentTimeMillis()
        val id = UUID.randomUUID().toString()
        val task = BotAssignment(id, groupId, botId, bot.name, instruction.lineSequence().first().take(60), instruction,
            BotAssignmentStatus.QUEUED, writePaths, now, now, sessionId = UUID.randomUUID().toString(),
            reportToBotId = requesterId, independent = true)
        val session = HarnessSessionEntity(
            id = requireNotNull(task.sessionId), title = "${bot.name} · ${task.title}", createdAt = now, updatedAt = now,
            modelId = bot.modelId ?: source.modelId,
            modelVariant = if (bot.modelId == null || bot.modelId == source.modelId) source.modelVariant else null,
            workspace = source.workspace, projectType = source.projectType,
            approvalMode = ApprovalMode.FULL_ACCESS.id, executionTargetJson = source.executionTargetJson,
        )
        repository.createIndependent(task, session, BotAssignmentRequest("initial:$id", id, instruction, now))
        return task
    }

    suspend fun continueTask(id: String, groupId: String, instruction: String, requestId: String = UUID.randomUUID().toString()): String {
        start()
        require(instruction.isNotBlank()) { "请填写补充要求" }
        val task = requireNotNull(repository.get(id)) { "任务不存在" }
        require(task.independent && task.groupId == groupId) { "该任务不属于当前群聊或没有独立会话" }
        val sessionId = requireNotNull(task.sessionId)
        requireNotNull(sessions.findById(sessionId)) { "原任务会话已删除，不能猜测其他会话继续" }
        // A blocked/interrupted session may have been resumed independently by native recovery.
        if (!task.status.isActive) {
            control.prepareRemoteSession(sessionId)
            require(control.sessionRunStates.value[sessionId] !in BUSY_STATES) { "原会话仍在执行，请先核对运行状态" }
        }
        repository.enqueueRequest(BotAssignmentRequest(requestId, id, instruction.trim(), System.currentTimeMillis()))
        return requestId
    }

    suspend fun awaitRequest(id: String): BotAssignmentRequest = repository.observeRequest(id).first {
        it == null || it.status !in setOf(BotAssignmentRequest.PENDING, BotAssignmentRequest.SUBMITTING, BotAssignmentRequest.ADMITTED)
    } ?: error("补充要求已清除")

    suspend fun taskForId(id: String) = repository.get(id)
    suspend fun cancelGroup(groupId: String) {
        repository.observeForGroup(groupId).first().filter { it.independent }.forEach { task ->
            if (task.status.isActive || control.sessionRunStates.value[task.sessionId] in BUSY_STATES) {
                tracker.cancel(task.id)
                task.sessionId?.let(control::cancel)
            }
        }
    }
    suspend fun history(id: String) = repository.requestsFor(id)
    suspend fun taskForSession(sessionId: String) = repository.assignmentForSession(sessionId)
    suspend fun report(sessionId: String, reason: String?, artifacts: List<String>) = repository.reportBlock(sessionId, reason, artifacts)

    private suspend fun execute(request: BotAssignmentRequest) {
        if (!repository.claimRequest(request.id, System.currentTimeMillis())) return
        val task = repository.get(request.assignmentId) ?: return
        tracker.track(task, currentCoroutineContext()[Job])
        try {
            val sessionId = requireNotNull(task.sessionId)
            requireNotNull(sessions.findById(sessionId)) { "独立任务会话已删除" }
            control.prepareRemoteSession(sessionId)
            check(control.sessionRunStates.value[sessionId] !in BUSY_STATES) { "原任务会话仍在运行，未重复提交" }
            val result = executor.execute(task, request) { nativeId ->
                check(repository.admitRequest(request.id, nativeId)) { "任务已停止或受理回执未保存" }
            }
            if (!result.uncertain) withTimeout(10_000) {
                control.sessionRunStates.first { it[sessionId] !in BUSY_STATES }
            }
            val state = control.sessionRunStates.value[sessionId]
            val uncertain = result.uncertain || state == SessionRunState.IDLE
            val failed = result.failed || state == SessionRunState.FAILED
            repository.finishRequest(request.id, !failed, uncertain, result.text, if (failed || uncertain) result.text else null)
        } catch (cancelled: CancellationException) {
            task.sessionId?.let(control::cancel)
            withContext(NonCancellable) {
                repository.finishRequest(request.id, false, true, null, "执行被中断，结果待核对")
            }
            throw cancelled
        } catch (failure: Exception) {
            logger.e("[BotAssignment] 独立任务执行结果待核对", failure)
            repository.finishRequest(request.id, false, true, null, "执行结果待核对：${failure.message}")
        } finally {
            tracker.untrack(task.id)
        }
    }

    companion object {
        private val BUSY_STATES = setOf(SessionRunState.RUNNING, SessionRunState.WAITING_APPROVAL)
    }
}
