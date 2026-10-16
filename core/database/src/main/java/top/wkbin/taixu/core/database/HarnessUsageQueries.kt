package top.wkbin.taixu.core.database

import androidx.room.Query

/**
 * `harness_usage` 账本的只读聚合查询。
 *
 * `harness_usage` 是一张 append-only 的 provider/工具计费账本，逐次 LLM 调用落一行，
 * 承载的是**真实计量值**（inputTokens / outputTokens / reasoningTokens /
 * cacheReadTokens / cacheWriteTokens / provider / modelId / estimatedCostUsd），
 * 而不是从正文长度反推的估算值。
 *
 * 统计面板此前完全没读这张表：它从 `harness_entries.payloadJson` 里用 `json_extract`
 * 抠标量、再用字符数估 token。于是推理 token、缓存读写分列、模型归因这三类信息
 * 在界面上根本不存在，而且总量只是估算。
 *
 * 本接口把聚合全部压在 SQLite 侧（按「本地日 × provider × model」分组），
 * Kotlin 侧只接触标量，不会把实体带进托管堆——延续 2026-09-14 OOM 事故后的口径。
 */
interface HarnessUsageQueries {

    /**
     * 按本地日与模型聚合账本。
     *
     * [tzOffsetMs] 与 StatsRepository 使用的 [java.time.ZoneId] 偏移一致，
     * 避免 UTC 日切把本地午夜前后的调用错桶。
     *
     * 修正行（`adjustment = true`）在 SQL 内剔除，杜绝重复计数；
     * `SUM` 外包一层 `COALESCE` 保证分组内无值时也不返回 NULL，
     * 投影字段因此可以声明为非空。
     */
    @Query("""
        SELECT
            strftime('%Y-%m-%d', (createdAt + :tzOffsetMs) / 1000, 'unixepoch') AS localDay,
            COALESCE(provider, '') AS provider,
            COALESCE(modelId, '') AS modelId,
            COALESCE(SUM(inputTokens), 0) AS inputTokens,
            COALESCE(SUM(outputTokens), 0) AS outputTokens,
            COALESCE(SUM(reasoningTokens), 0) AS reasoningTokens,
            COALESCE(SUM(cacheReadTokens), 0) AS cacheReadTokens,
            COALESCE(SUM(cacheWriteTokens), 0) AS cacheWriteTokens,
            COUNT(*) AS calls
        FROM harness_usage
        WHERE adjustment = 0
          AND (:start IS NULL OR createdAt >= :start)
          AND (:end IS NULL OR createdAt < :end)
        GROUP BY
            strftime('%Y-%m-%d', (createdAt + :tzOffsetMs) / 1000, 'unixepoch'),
            provider,
            modelId
        ORDER BY localDay
    """)
    suspend fun aggregateLedgerByDay(start: Long?, end: Long?, tzOffsetMs: Long): List<UsageLedgerRow>

    /** 账本最近若干次调用（倒序），供明细下钻使用。 */
    @Query("""
        SELECT * FROM harness_usage
        WHERE adjustment = 0
          AND (:start IS NULL OR createdAt >= :start)
          AND (:end IS NULL OR createdAt < :end)
        ORDER BY sequence DESC
        LIMIT :limit
    """)
    suspend fun listRecentUsage(start: Long?, end: Long?, limit: Int): List<HarnessUsageEntity>
}

/** 账本按 (本地日, provider, model) 聚合后的投影行；字段全部为 SQLite 侧标量。 */
data class UsageLedgerRow(
    val localDay: String,
    val provider: String,
    val modelId: String,
    val inputTokens: Long,
    val outputTokens: Long,
    val reasoningTokens: Long,
    val cacheReadTokens: Long,
    val cacheWriteTokens: Long,
    val calls: Int,
)
