package top.wkbin.taixu.harness.approval

import java.util.UUID
import kotlinx.coroutines.CancellationException
import top.wkbin.taixu.core.common.logging.AppLogger
import top.wkbin.taixu.core.database.AgentApprovalAuditEntity
import top.wkbin.taixu.core.database.AgentApprovalAuditRepository
import top.wkbin.taixu.harness.HarnessTool

/**
 * 审批审计埋点收口（借鉴 DSH + OpenBot「先裁决、后审计」）。
 *
 * 登记 agent_approval_requests 覆盖不到的事实：
 * - 会话授权写入 / 豁免执行 / Lane deferred
 * - 策略否决（PLAN / 禁用能力）
 * - 高风险或无人值守下的放行前决策
 * - 上述放行后的执行失败
 * - 浏览器人机接管 / 交还（control_taken / control_released）
 *
 * 每行带 [RunInitiator]（默认 person）。审计失败绝不影响主链路。
 */
class ApprovalAuditRecorder(
    private val audit: AgentApprovalAuditRepository?,
    private val logger: AppLogger? = null,
) {

    /** 用户批准并勾选「本会话内记住」时：写入授权表并登记审计。 */
    suspend fun grantWritten(
        sessionId: String,
        toolName: String,
        argumentsJson: String,
        riskLevel: String,
        approvalRequestId: String? = null,
    ) {
        SessionApprovalGrants.grantKey(toolName, argumentsJson)?.let { key ->
            stamp(
                sessionId = sessionId,
                kind = AgentApprovalAuditEntity.KIND_GRANT_WRITTEN,
                toolName = toolName,
                riskLevel = riskLevel,
                grantKey = key,
                approvalRequestId = approvalRequestId,
            )
        }
    }

    /** 某次调用因会话内授权豁免而免审批执行时登记。 */
    suspend fun grantExempted(
        sessionId: String,
        toolCallId: String,
        toolName: String,
        argumentsJson: String,
        riskLevel: String,
    ) {
        stamp(
            sessionId = sessionId,
            kind = AgentApprovalAuditEntity.KIND_GRANT_EXEMPTED,
            toolName = toolName,
            toolCallId = toolCallId,
            riskLevel = riskLevel,
            grantKey = SessionApprovalGrants.grantKey(toolName, argumentsJson),
        )
    }

    /** 子智能体后台 Lane 交接审批（approvalDeferred）时登记。 */
    suspend fun deferred(
        sessionId: String,
        toolCallId: String,
        toolName: String,
        riskLevel: String,
        reason: String,
    ) {
        stamp(
            sessionId = sessionId,
            kind = AgentApprovalAuditEntity.KIND_DEFERRED,
            toolName = toolName,
            toolCallId = toolCallId,
            riskLevel = riskLevel,
            detail = reason,
        )
    }

    /** 策略在执行前否决（PLAN 硬拦、能力禁用等）——OpenBot decision=deny。 */
    suspend fun policyDenied(
        sessionId: String,
        toolCallId: String,
        toolName: String,
        riskLevel: String,
        reason: String,
    ) {
        stamp(
            sessionId = sessionId,
            kind = AgentApprovalAuditEntity.KIND_POLICY_DENIED,
            toolName = toolName,
            toolCallId = toolCallId,
            riskLevel = riskLevel,
            detail = reason,
        )
    }

    /**
     * 策略已放行、即将执行的决策行（OpenBot decision=forward）。
     * 仅高风险 / 无人值守 / 计算机面工具，避免 FULL_ACCESS 刷屏。
     */
    suspend fun decisionForwarded(
        sessionId: String,
        toolCallId: String,
        toolName: String,
        riskLevel: String,
        summary: String,
    ) {
        stamp(
            sessionId = sessionId,
            kind = AgentApprovalAuditEntity.KIND_DECISION_FORWARDED,
            toolName = toolName,
            toolCallId = toolCallId,
            riskLevel = riskLevel,
            detail = summary,
        )
    }

    /** 已放行动作执行失败——OpenBot 第二行审计。 */
    suspend fun actionFailed(
        sessionId: String,
        toolCallId: String,
        toolName: String,
        riskLevel: String,
        detail: String,
    ) {
        stamp(
            sessionId = sessionId,
            kind = AgentApprovalAuditEntity.KIND_ACTION_FAILED,
            toolName = toolName,
            toolCallId = toolCallId,
            riskLevel = riskLevel,
            detail = detail,
        )
    }

    /** 用户关闭共浏览、接管浏览器——OpenBot `computer.control_taken`。 */
    suspend fun controlTaken(detail: String = "用户接管浏览器（共浏览已暂停）") {
        stamp(
            sessionId = AgentApprovalAuditEntity.SESSION_BROWSER_CONTROL,
            kind = AgentApprovalAuditEntity.KIND_CONTROL_TAKEN,
            toolName = AgentApprovalAuditEntity.TOOL_BROWSER_CONTROL,
            riskLevel = "normal",
            detail = detail,
        )
    }

    /** 用户重新开启共浏览、交还控制——OpenBot `computer.control_released`。 */
    suspend fun controlReleased(detail: String = "用户交还浏览器控制（共浏览已恢复）") {
        stamp(
            sessionId = AgentApprovalAuditEntity.SESSION_BROWSER_CONTROL,
            kind = AgentApprovalAuditEntity.KIND_CONTROL_RELEASED,
            toolName = AgentApprovalAuditEntity.TOOL_BROWSER_CONTROL,
            riskLevel = "low",
            detail = detail,
        )
    }

    /**
     * 人已接管时 Bot 仍发起页面动作——OpenBot `computer.help_requested`。
     * [toolName] 保留被拒工具名，便于核对是 navigate/click 等哪一类求助。
     */
    suspend fun helpRequested(toolName: String, detail: String) {
        stamp(
            sessionId = AgentApprovalAuditEntity.SESSION_BROWSER_CONTROL,
            kind = AgentApprovalAuditEntity.KIND_HELP_REQUESTED,
            toolName = toolName.ifBlank { AgentApprovalAuditEntity.TOOL_BROWSER_CONTROL },
            riskLevel = "normal",
            detail = detail,
        )
    }

    suspend fun deleteForSession(sessionId: String) {
        val repository = audit ?: return
        runCatching { repository.deleteForSession(sessionId) }
            .onFailure { if (it is CancellationException) throw it else logger?.w("Failed to clean approval audits for $sessionId: ${it.message}") }
    }

    private suspend fun stamp(
        sessionId: String,
        kind: String,
        toolName: String,
        toolCallId: String? = null,
        riskLevel: String = "",
        detail: String? = null,
        grantKey: String? = null,
        approvalRequestId: String? = null,
    ) {
        val initiator = currentRunInitiator()
        record(
            AgentApprovalAuditEntity(
                id = UUID.randomUUID().toString(),
                sessionId = sessionId,
                kind = kind,
                toolName = toolName,
                toolCallId = toolCallId,
                riskLevel = riskLevel,
                grantKey = grantKey,
                approvalRequestId = approvalRequestId,
                detail = detail?.take(DETAIL_MAX_CHARS),
                createdAt = System.currentTimeMillis(),
                initiatorKind = initiator.kind,
                initiatorId = initiator.id,
            ),
        )
    }

    private suspend fun record(entry: AgentApprovalAuditEntity) {
        val repository = audit ?: return
        runCatching { repository.record(entry) }
            .onFailure { if (it is CancellationException) throw it else logger?.w("Approval audit insert failed (${entry.kind}/${entry.toolName}): ${it.message}") }
    }

    companion object {
        private const val DETAIL_MAX_CHARS = 500

        /** 与 OpenBot computer 面接近的可变工具；只读检索不进双审计。 */
        fun isComputerSurface(tool: HarnessTool): Boolean = tool == HarnessTool.HOST ||
            tool == HarnessTool.BASE ||
            tool == HarnessTool.WRITE ||
            tool == HarnessTool.EDIT ||
            tool == HarnessTool.MCP ||
            tool == HarnessTool.PROCESS ||
            tool == HarnessTool.DOWNLOAD ||
            tool == HarnessTool.BUILD_SCRIPT

        fun shouldDualAudit(tool: HarnessTool, riskLevel: String, unattended: Boolean): Boolean {
            if (!isComputerSurface(tool)) return false
            if (unattended) return true
            val risk = riskLevel.trim().lowercase()
            return risk == "high" || risk == "critical"
        }
    }
}
