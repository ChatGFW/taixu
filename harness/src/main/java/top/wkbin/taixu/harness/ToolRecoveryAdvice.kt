package top.wkbin.taixu.harness

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import top.wkbin.taixu.harness.effects.ToolRecoveryNotice

/** Recovery suggestions are evidence for the model, never permission or an automatic replay policy. */
@Serializable
data class ToolRecoveryAdvice(
    /** There is a suggested inspection/recovery path; this does not mean the failed operation is safe to retry. */
    val recoverable: Boolean,
    val retrySameArguments: Boolean,
    val nextActions: List<ToolRecoveryAction>,
)

@Serializable
data class ToolRecoveryAction(val kind: String, val instruction: String, val tool: String? = null)

internal fun ToolResult.withRecoveryAdvice(tool: HarnessTool? = null): ToolResult {
    if (success || recovery != null) return this
    val hashlineCode = errorCode ?: metadata["hashline_error"]
    val actions = when {
        errorCode == "PLAN_BLOCKED" -> listOf(ToolRecoveryAction("respect_plan", "当前仅允许规划；把变更列入待办，由用户决定是否切换执行模式。"))
        errorCode == "TOOL_CHECKPOINT_BLOCKED" -> listOf(ToolRecoveryAction("respect_checkpoint", "本次调用被受信任检查点阻止；按原因调整任务，不绕过检查点。"))
        errorCode == ToolRecoveryNotice.OUTCOME_UNKNOWN -> listOf(
            ToolRecoveryAction("verify_state", "先用只读查询核验实际副作用；无法核验时说明不确定性并取得用户确认。禁止直接重放。"),
        )
        awaitingApproval -> listOf(ToolRecoveryAction("wait_approval", "等待当前审批或提问结果，不创建重复请求。"))
        approvalDeferred -> listOf(ToolRecoveryAction("return_to_parent", "把未执行的操作交给主会话发起并等待批准。"))
        errorCode == ToolRecoveryNotice.INTERRUPTED -> listOf(
            ToolRecoveryAction("resume_read", "只读查询已中断；仅在任务仍需继续且未被用户停止时重新查询。"),
        )
        hashlineCode == "HASHLINE_STALE" || hashlineCode == "stale" -> listOf(
            ToolRecoveryAction(
                "reread_anchors",
                "锚点已过期：立刻 read 同一 path（可带合适 offset/limit）获取最新 n:h 锚点，再用新锚点构造 ops；禁止改用猜测的 oldText 盲试同一处。",
                "read",
            ),
        )
        hashlineCode == "HASHLINE_BAD_ANCHOR" || hashlineCode == "HASHLINE_INVALID" ||
            hashlineCode == "HASHLINE_OVERLAP" || hashlineCode == "HASHLINE_EMPTY_OPS" ||
            hashlineCode == "HASHLINE_MISSING_ARGS" || hashlineCode == "HASHLINE_MIXED_MODE" ||
            hashlineCode == "HASHLINE_FAILURE" -> listOf(
            ToolRecoveryAction(
                "fix_hashline_args",
                "按 HASHLINE_* 错误修正 ops（锚点格式、区间不重叠、与 oldText 互斥、ops 非空）；需要时先 read 再 edit。",
                "read",
            ),
        )
        hashlineCode == "HASHLINE_OPS_DISABLED" || hashlineCode == "ops_disabled" -> listOf(
            ToolRecoveryAction(
                "use_legacy_or_enable_ops",
                "Hashline ops 已关闭：改用 oldText/newText，或在设置中开启「允许 Hashline ops 编辑」。",
            ),
        )
        hashlineCode == "HASHLINE_LEGACY_DISABLED" || hashlineCode == "legacy_disabled" -> listOf(
            ToolRecoveryAction(
                "use_hashline_ops",
                "legacy oldText 已关闭：先 read 获取 n:h 锚点，再用 ops 编辑；文件开头或空文件用 insert_after after=0:00。",
                "read",
            ),
        )
        tool == HarnessTool.EDIT || tool == HarnessTool.WRITE -> listOf(
            ToolRecoveryAction("inspect_current_file", "先读取目标文件的当前内容，核对路径、匹配文本及是否已有部分修改。", "read"),
            ToolRecoveryAction("correct_arguments", "根据观察修正参数；不要把失败解释为没有发生修改。"),
        )
        tool == HarnessTool.BASE -> listOf(
            ToolRecoveryAction("inspect_failure", "读取日志并核验命令可能产生的部分副作用，区分路径、依赖、语法和超时问题。"),
            ToolRecoveryAction("change_strategy", "修正原因后再决定下一步；命令退出失败不证明此前操作均未生效。"),
        )
        else -> listOf(ToolRecoveryAction("inspect_failure", "按错误信息核对参数、能力和当前状态，调整策略后再继续。"))
    }
    val blocked = errorCode == "PLAN_BLOCKED" || errorCode == "TOOL_CHECKPOINT_BLOCKED"
    return copy(recovery = ToolRecoveryAdvice(recoverable = !blocked, retrySameArguments = false, nextActions = actions))
}

/** Append only at model projection: UI output stays concise and the durable advice survives truncation. */
internal fun ToolResult.modelOutput(): String = recovery?.let {
    output + "\n\n[工具恢复建议]\n" + Json.encodeToString(ToolRecoveryAdvice.serializer(), it)
} ?: output
