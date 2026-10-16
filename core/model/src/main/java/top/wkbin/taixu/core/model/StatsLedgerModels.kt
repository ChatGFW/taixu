package top.wkbin.taixu.core.model

/**
 * 用量账本（`harness_usage`）在统计面板上的投影。
 *
 * 与 [StatsSummary] 的关系是**并行展示、口径不同**：
 * `StatsSummary` 的 token 数是从正文长度估算出来的（见 `estimateTokensFromChars`），
 * 而这里全部来自账本落库的真实计量值，并额外带出推理 token 与缓存读写分列。
 *
 * 之所以保留两套口径而不是直接替换：估算口径覆盖了账本落库之前的历史数据，
 * 直接替换会让老会话的用量凭空归零。界面上把两者分区呈现，读者能自行判断可信度。
 */
data class StatsLedger(
    val calls: Int = 0,
    val inputTokens: Long = 0,
    val outputTokens: Long = 0,
    val reasoningTokens: Long = 0,
    val cacheReadTokens: Long = 0,
    val cacheWriteTokens: Long = 0,
    /** 已带定价的调用数；[estimatedCostUsd] 为 null 时说明当前没有任何模型配置过单价。 */
    val pricedCalls: Int = 0,
    val estimatedCostUsd: Double? = null,
    val models: List<StatsLedgerModelRow> = emptyList(),
    val recentCalls: List<StatsLedgerCallRow> = emptyList(),
    val trend: List<StatsTrendDay> = emptyList(),
) {
    /**
     * 计费口径的 token 合计。
     * 缓存读**不计入**：它是输入前缀的复用部分，计入会与 [inputTokens] 双重计算。
     */
    val totalTokens: Long get() = inputTokens + outputTokens + reasoningTokens

    /** 缓存命中率 = 命中读取量 ÷ (真实输入 + 命中读取量)。无可度量时为 0。 */
    val cacheHitRate: Double
        get() = (inputTokens + cacheReadTokens)
            .takeIf { it > 0L }
            ?.let { cacheReadTokens.toDouble() / it.toDouble() }
            ?: 0.0
}

/** 账本按模型聚合后的一行。 */
data class StatsLedgerModelRow(
    val id: String,
    val label: String,
    val provider: String,
    val calls: Int,
    val inputTokens: Long,
    val outputTokens: Long,
    val reasoningTokens: Long,
    val cacheReadTokens: Long,
    val cacheWriteTokens: Long,
)

/** 账本逐次调用的明细行。 */
data class StatsLedgerCallRow(
    val id: String,
    val createdAt: Long,
    val label: String,
    val provider: String,
    val inputTokens: Long,
    val outputTokens: Long,
    val reasoningTokens: Long,
    val cacheReadTokens: Long,
    val cacheWriteTokens: Long,
)
