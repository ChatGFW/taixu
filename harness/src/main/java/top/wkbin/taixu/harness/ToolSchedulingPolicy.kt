package top.wkbin.taixu.harness

import kotlinx.serialization.json.JsonObject

/** Only tools without shared-state mutations may overlap within a round. */
internal object ToolSchedulingPolicy {
    private val readOnlyTools = setOf(
        HarnessTool.READ,
        HarnessTool.HISTORY_SEARCH,
        HarnessTool.HISTORY_READ,
        HarnessTool.LOAD_RULE,
    )

    /**
     * 该调用能否进入回合内的并发组。
     *
     * 子智能体此前是无条件屏障，导致主智能体一轮里发出的多个 `invoke_subagent`
     * 只能逐个整批执行（每批最坏 15 分钟）。实际上子智能体自己按 write_paths 协调写租约，
     * 批次之间是否安全只取决于两点：写路径集合是否相交、有没有整工作区独占（`["*"]`）。
     * 后者由 [conflicts] 在组内两两判定，这里只回答"这次调用本身是否具备并行资格"：
     * 解析不出子任务（参数非法）时保守回退为屏障。
     */
    fun isParallelSafe(tool: HarnessTool, args: JsonObject? = null): Boolean = when (tool) {
        in readOnlyTools -> true
        HarnessTool.SUBAGENT -> subagentWritePaths(args) != null
        else -> false
    }

    fun needsMutationLock(tool: HarnessTool): Boolean =
        tool !in readOnlyTools && tool != HarnessTool.SUBAGENT && tool != HarnessTool.BASE

    /**
     * 同一并发组内两个调用是否必须错开。
     *
     * 只对子智能体做判定：两条只读批次（write_paths 为空）互不冲突；
     * 任一方解析失败，或写路径集合相交（含任一方为 `["*"]`，由 [writePathsConflict] 兜住）
     * 都视为冲突。非子智能体的并行成员本来就是只读工具，返回 false。
     */
    fun conflicts(
        leftTool: HarnessTool,
        leftArgs: JsonObject?,
        rightTool: HarnessTool,
        rightArgs: JsonObject?,
    ): Boolean {
        if (leftTool != HarnessTool.SUBAGENT || rightTool != HarnessTool.SUBAGENT) return false
        val left = subagentWritePaths(leftArgs) ?: return true
        val right = subagentWritePaths(rightArgs) ?: return true
        if (left.isEmpty() || right.isEmpty()) return false
        return left.any { candidate ->
            right.any { existing -> writePathsConflict(existing, candidate) }
        }
    }

    /**
     * 一次 `invoke_subagent` 调用声明的写路径并集。
     *
     * 复用 [SubagentArgsParser] 与 [normalizeWritePath]、[writePathsConflict]，
     * 保证回合级判定与编排层 `buildWriteCleanWaves` 的口径完全一致——两处各写一套规则
     * 迟早会漂移成"调度认为安全、租约认为冲突"的静默踩踏。解析不出任何任务时返回 null。
     */
    internal fun subagentWritePaths(args: JsonObject?): Set<String>? {
        val parsed = args ?: return null
        val tasks = SubagentArgsParser.parse(parsed)
        if (tasks.isEmpty()) return null
        return tasks.flatMap { it.writePaths }.mapTo(linkedSetOf()) { normalizeWritePath(it) }
    }

    // Memory/plan/scratchpad actions can mutate state; MCP and unknown capabilities are exclusive.
    // Base remains a round barrier, but a long shell command must not hold the workspace lock
    // across sessions. Shell effects have no structured path lease; concurrent commands may
    // touch the same files. Runtime admission separately serializes known memory-heavy commands.
    // Subagents hold no parent workspace lock; they are admitted to a concurrency group only when
    // their declared write paths are pairwise disjoint (see [conflicts]).
}
