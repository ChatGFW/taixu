package top.wkbin.taixu.harness

import top.wkbin.taixu.core.database.AgentApprovalRepository
import top.wkbin.taixu.core.database.AgentApprovalRequestEntity
import top.wkbin.taixu.harness.operation.OperationCoordinator
import top.wkbin.taixu.harness.projection.SessionMessageProjector

/** 把仍在等待的审批记成已取消的工具结果，避免停止后审批卡悬挂。 */
internal suspend fun cancelPendingApprovalsForSession(
    sessId: String,
    approvalRepository: AgentApprovalRepository,
    operationCoordinator: OperationCoordinator,
    messageProjector: SessionMessageProjector,
    now: Long,
    newId: () -> String,
) {
    val pending = approvalRepository.pendingNow(sessId)
    if (pending.isEmpty()) return
    for (request in pending) {
        if (!approvalRepository.claimPending(
                request.id,
                AgentApprovalRequestEntity.STATUS_REJECTED,
            )
        ) {
            continue
        }
        val result = ToolResult(
            id = newId(),
            createdAt = now,
            toolCallId = request.toolCallId,
            success = false,
            output = "用户已停止本次运行，待审批工具未执行。",
        )
        val active = operationCoordinator.active(sessId)
        if (active != null && (request.operationId == null || request.operationId == active.id)) {
            operationCoordinator.toolSettled(active.id, result, round = 0, toolName = request.toolName)
            messageProjector.publishPersisted(sessId, result)
        } else {
            messageProjector.append(sessId, result)
        }
    }
    operationCoordinator.finish(sessId, "aborted")
}
