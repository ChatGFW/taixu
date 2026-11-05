package top.wkbin.taixu.harness

/**
 * 子智能体的时间预算协商。
 *
 * 轮数上限看不出口袋里还剩多少时间：一个 Lane 可能在最后一轮之前就把 15 分钟用光，
 * 然后在编排层的硬超时处被直接掐断，连一句结论都交不出来。这里把预算拆成两段：
 * 进入收尾窗口后停止派工具、强制交付结论；硬超时只兜"收尾轮自己也卡死"。
 */
internal object SubagentTimeBudget {

    /** 剩余时间低于此值即进入收尾：不再派新工具，用剩余时间写结论。 */
    const val WRAP_UP_WINDOW_MS = 2 * 60 * 1000L

    /** 编排层硬超时相对预算的宽限，给收尾轮留出完成时间。 */
    const val GRACE_MS = 90_000L

    fun exhausted(deadlineAtMs: Long?, nowMs: Long): Boolean =
        deadlineAtMs != null && nowMs >= deadlineAtMs

    fun shouldWrapUp(deadlineAtMs: Long?, nowMs: Long): Boolean =
        deadlineAtMs != null && deadlineAtMs - nowMs <= WRAP_UP_WINDOW_MS

    /** 预算被用尽时的交接正文：父智能体至少拿到子任务最后阶段的输出。 */
    fun exhaustedSummary(finalText: String): String = buildString {
        append("⚠️ 子任务时间预算已用尽，未产出最终结论。")
        if (finalText.isNotBlank()) append("\n\n最后阶段输出：\n").append(finalText)
    }
}
