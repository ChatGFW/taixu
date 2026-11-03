package top.wkbin.taixu.harness.approval

import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.currentCoroutineContext

/**
 * 本次运行「因何发起」——与「以谁的权限执行」正交（借鉴 OpenBot initiator）。
 *
 * - [PERSON]：用户在场交互（默认；缺省不得冒充其它 kind）
 * - [SCHEDULE]：Bot 排期无人值守触发
 * - [HANDOFF]：群委派 / Bot 接力，用户未必盯着执行过程
 * - [WORKFLOW]：工作流节点或定时工作流触发
 * - [SYSTEM]：部署/恢复等非用户任务（极少写入审批审计）
 *
 * 值写入 PendingMessage / agent_tasks / agent_approval_audits，未知 kind 读回时按 [PERSON] 处理，
 * 避免 Bot 或旧数据把无人值守伪装成「有人在看」。
 */
data class RunInitiator(
    val kind: String = PERSON,
    val id: String? = null,
) {
    fun normalized(): RunInitiator {
        val k = kind.trim().lowercase().ifBlank { PERSON }
        val known = k in KNOWN
        return RunInitiator(
            kind = if (known) k else PERSON,
            // 未知 kind 不得保留外来 id，避免无人值守伪装成有人在场时仍挂着假引用。
            id = if (known) id?.trim()?.takeIf { it.isNotEmpty() }?.take(ID_MAX) else null,
        )
    }

    companion object {
        const val PERSON = "person"
        const val SCHEDULE = "schedule"
        const val HANDOFF = "handoff"
        const val WORKFLOW = "workflow"
        const val SYSTEM = "system"

        val KNOWN = setOf(PERSON, SCHEDULE, HANDOFF, WORKFLOW, SYSTEM)
        private const val ID_MAX = 128

        fun person() = RunInitiator(PERSON)
        fun schedule(scheduleId: String) = RunInitiator(SCHEDULE, scheduleId).normalized()
        fun handoff(taskOrBotId: String) = RunInitiator(HANDOFF, taskOrBotId).normalized()
        fun workflow(runOrNodeId: String) = RunInitiator(WORKFLOW, runOrNodeId).normalized()

        /** 「无人盯着」：排期 / 接力 / 工作流——对应 OpenBot Nobody watching 过滤语义。 */
        fun isUnattended(kind: String): Boolean =
            kind.trim().lowercase() in setOf(SCHEDULE, HANDOFF, WORKFLOW)
    }
}

/** 准入点安装；SessionInputController 在建 PendingMessage 时捕获。 */
class RunInitiatorContext(
    val initiator: RunInitiator,
) : AbstractCoroutineContextElement(Key) {
    constructor(kind: String, id: String? = null) : this(RunInitiator(kind, id).normalized())

    companion object Key : CoroutineContext.Key<RunInitiatorContext>
}

suspend fun currentRunInitiator(): RunInitiator =
    currentCoroutineContext()[RunInitiatorContext]?.initiator?.normalized() ?: RunInitiator.person()
