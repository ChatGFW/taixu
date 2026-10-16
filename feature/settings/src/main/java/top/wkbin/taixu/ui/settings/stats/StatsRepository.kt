package top.wkbin.taixu.ui.settings.stats

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import top.wkbin.taixu.core.database.AiModelRepository
import top.wkbin.taixu.core.database.HarnessRuntimeRepository
import top.wkbin.taixu.core.database.HarnessSessionRepository
import top.wkbin.taixu.core.datastore.AppStatsPreferences
import top.wkbin.taixu.core.model.StatsDateRange
import top.wkbin.taixu.core.model.StatsHeatmapDay
import top.wkbin.taixu.core.model.StatsLedger
import top.wkbin.taixu.core.model.StatsLedgerCallRow
import top.wkbin.taixu.core.model.StatsLedgerModelRow
import top.wkbin.taixu.core.model.StatsRankItem
import top.wkbin.taixu.core.model.StatsSnapshot
import top.wkbin.taixu.core.model.StatsSummary
import top.wkbin.taixu.core.model.StatsTokenBucket
import top.wkbin.taixu.core.model.StatsTrendDay
import java.time.LocalDate
import java.time.ZoneId

class StatsRepository(
    private val runtimeRepository: HarnessRuntimeRepository,
    private val sessionRepository: HarnessSessionRepository,
    private val aiModelRepository: AiModelRepository,
    private val appStatsPreferences: AppStatsPreferences,
) {
    suspend fun buildSnapshot(
        range: StatsDateRange,
        now: LocalDate = LocalDate.now(),
    ): StatsSnapshot = withContext(Dispatchers.IO) {
        val zone = ZoneId.systemDefault()
        // 与 SQL 日切共用同一偏移，避免 CAST(createdAt/86400000) 按 UTC 日切、本地午夜错桶。
        val tzOffsetMs = zone.rules.getOffset(now.atStartOfDay(zone).toInstant()).totalSeconds * 1000L
        val startEpochMs = range.start?.atStartOfDay(zone)?.toInstant()?.toEpochMilli()
        val endEpochMs = range.end?.plusDays(1)?.atStartOfDay(zone)?.toInstant()?.toEpochMilli()

        // 1. 基础汇总
        val totalSessions = sessionRepository.countInRange(startEpochMs, endEpochMs)
        val totalMessages = runtimeRepository.countEntriesInRange(startEpochMs, endEpochMs)
        val launchCount = appStatsPreferences.appLaunchCount.first()

        // 2. 所有模型与会话缓存
        val allModels = aiModelRepository.observeAll().first().associateBy { it.id }
        val allSessions = sessionRepository.listAll().associateBy { it.id }

        // 3. 用量聚合 —— 关键改动（2026-09-14 OOM 修复）
        //
        // 原实现：runtimeRepository.listEntriesInRange(startEpochMs, endEpochMs) 把区间内**全部**
        // harness_entries（含完整 payloadJson）读进内存，再对每条 json.parseToJsonElement 建树。
        // 实测库内 15,888 条（tool_result 5,696 条，单体可达数十 KB~数 MB），
        // 选「全部时间」时直接把 256MB 堆打爆 → java.lang.OutOfMemoryError。
        //
        // 现改为 SQL 层聚合：json_extract 在 SQLite 侧取标量、按 (sessionId, customType) GROUP BY，
        // 1.5 万行压缩成几十行小结果返回，Kotlin 侧不再接触 payloadJson 本体。
        val usageRows = runtimeRepository.aggregateUsageInRange(startEpochMs, endEpochMs)

        var totalInputTokens = 0L
        var totalOutputTokens = 0L
        var totalCachedTokens = 0L

        val modelUsageCounts = mutableMapOf<String, Int>()
        val providerUsageCounts = mutableMapOf<String, Int>()
        val assistantUsageCounts = mutableMapOf<String, Int>()

        // 趋势图聚合桶
        val trendStart = if (range.isAllTime) now.minusDays(29) else (range.start ?: now.minusDays(29))
        val trendEnd = if (range.isAllTime) now else (range.end ?: now)
        val trendBuckets = mutableMapOf<LocalDate, MutableMap<String, StatsTokenBucket>>()

        var dayCursor = trendStart
        while (!dayCursor.isAfter(trendEnd)) {
            trendBuckets[dayCursor] = mutableMapOf()
            dayCursor = dayCursor.plusDays(1)
        }

        // 话题会话排行用的每会话累计条数（由聚合行累加，无需原始消息）
        val sessionEntryCounts = mutableMapOf<String, Int>()

        for (row in usageRows) {
            val session = allSessions[row.sessionId]
            val sessionModelId = session?.modelId
            val modelEntity = sessionModelId?.let { allModels[it] }

            val providerName = modelEntity?.provider?.ifBlank { "默认" } ?: "内置"
            val modelName = modelEntity?.name ?: sessionModelId ?: "通用助手"

            // 统计助手/工作区维度
            val workspaceLabel = session?.workspace?.takeIf { it.isNotBlank() }
                ?.substringAfterLast('/')?.ifBlank { null }
                ?: session?.title?.takeIf { it.isNotBlank() }
                ?: "默认工程"
            assistantUsageCounts[workspaceLabel] = (assistantUsageCounts[workspaceLabel] ?: 0) + row.entryCount
            sessionEntryCounts[row.sessionId] = (sessionEntryCounts[row.sessionId] ?: 0) + row.entryCount

            val tokens = tokenContributionFromAggregate(
                customType = row.customType,
                promptTokens = row.promptTokens,
                completionTokens = row.completionTokens,
                cachedTokens = row.cachedTokens,
                textChars = row.textChars,
                reasoningChars = row.reasoningChars,
            )
            totalInputTokens += tokens.input
            totalOutputTokens += tokens.output
            totalCachedTokens += tokens.cached

            when (row.customType) {
                "assistant" -> {
                    modelUsageCounts[modelName] = (modelUsageCounts[modelName] ?: 0) + row.entryCount
                    providerUsageCounts[providerName] = (providerUsageCounts[providerName] ?: 0) + row.entryCount
                }
                "tool_call" -> {
                    modelUsageCounts[modelName] = (modelUsageCounts[modelName] ?: 0) + row.entryCount
                }
            }
        }

        // 趋势桶：按本地日聚合 token（与区间汇总同一套 json_extract 标量，不取 payloadJson）
        val dailyRows = runtimeRepository.aggregateDailyCounts(startEpochMs, endEpochMs, tzOffsetMs)
        for (row in dailyRows) {
            val msgDate = LocalDate.ofEpochDay(row.localEpochDay)
            if (msgDate.isBefore(trendStart) || msgDate.isAfter(trendEnd)) continue
            val session = allSessions[row.sessionId]
            val providerName = session?.modelId?.let { allModels[it] }?.provider?.ifBlank { "默认" } ?: "内置"
            val dayMap = trendBuckets.getOrPut(msgDate) { mutableMapOf() }
            val bucket = dayMap[providerName] ?: StatsTokenBucket()
            val tokens = tokenContributionFromAggregate(
                customType = row.customType,
                promptTokens = row.promptTokens,
                completionTokens = row.completionTokens,
                cachedTokens = row.cachedTokens,
                textChars = row.textChars,
                reasoningChars = row.reasoningChars,
            )
            dayMap[providerName] = bucket.add(
                input = tokens.input,
                output = tokens.output,
                cached = tokens.cached,
                activity = row.entryCount,
            )
        }

        // 4. 热力图（近 180 天打卡矩阵）—— 同样用按本地日聚合，不加载原始条目
        val heatmapStart = now.minusDays(180)
        val heatmapStartEpoch = heatmapStart.atStartOfDay(zone).toInstant().toEpochMilli()
        val heatmapStartEpochDay = heatmapStart.toEpochDay()
        val rawHeatmapRows = runtimeRepository.aggregateDailyCounts(heatmapStartEpoch, null, tzOffsetMs)
            .filter { it.localEpochDay >= heatmapStartEpochDay }
            .groupingBy { LocalDate.ofEpochDay(it.localEpochDay).toString() }
            .fold(0) { acc, item -> acc + item.entryCount }

        val heatmapDays = mutableListOf<StatsHeatmapDay>()
        var hCursor = now.minusDays(180)
        while (!hCursor.isAfter(now)) {
            val dateStr = hCursor.toString()
            val count = rawHeatmapRows[dateStr] ?: 0
            heatmapDays.add(StatsHeatmapDay(date = hCursor, count = count))
            hCursor = hCursor.plusDays(1)
        }

        // 5. 话题会话排行（用聚合出的每会话条目数，替代原先对全量消息分组）
        val topicRankRows = sessionEntryCounts.entries.sortedByDescending { it.value }.take(20)
        val topicRank = topicRankRows.map {
            StatsRankItem(
                id = it.key,
                label = allSessions[it.key]?.title?.ifBlank { "未命名会话" } ?: "未命名会话",
                value = it.value,
            )
        }

        // 6. 模型排行
        val modelRank = modelUsageCounts.entries
            .sortedByDescending { it.value }
            .take(20)
            .map { StatsRankItem(id = it.key, label = it.key, value = it.value) }

        // 7. 助手/工作区排行
        val assistantRank = assistantUsageCounts.entries
            .sortedByDescending { it.value }
            .take(20)
            .map { StatsRankItem(id = it.key, label = it.key, value = it.value) }

        // 8. 趋势数据组装
        val trendList = trendBuckets.entries.map { (date, map) ->
            StatsTrendDay(date = date, providerTokens = map)
        }

        // 9. 用量账本（权威口径）—— harness_usage 里逐次 LLM 调用的真实计量值。
        //
        // 与第 1~8 步的估算口径并行存在，而不是替换：估算口径覆盖了账本落库之前的历史数据，
        // 直接替换会让老会话用量凭空归零。它额外补上三样一直以来在界面上完全不存在的东西：
        // 推理 token、缓存读写分列、按模型/供应商的归因。
        val ledgerRows = runtimeRepository.aggregateUsageLedger(startEpochMs, endEpochMs, tzOffsetMs)
        val ledgerRecentRows = runtimeRepository.listRecentUsage(startEpochMs, endEpochMs, 40)

        var ledgerCalls = 0
        var ledgerInput = 0L
        var ledgerOutput = 0L
        var ledgerReasoning = 0L
        var ledgerCacheRead = 0L
        var ledgerCacheWrite = 0L
        val ledgerModelRows = linkedMapOf<String, StatsLedgerModelRow>()
        val ledgerTrendBuckets = mutableMapOf<LocalDate, MutableMap<String, StatsTokenBucket>>()

        for (row in ledgerRows) {
            ledgerCalls += row.calls
            ledgerInput += row.inputTokens
            ledgerOutput += row.outputTokens
            ledgerReasoning += row.reasoningTokens
            ledgerCacheRead += row.cacheReadTokens
            ledgerCacheWrite += row.cacheWriteTokens

            val ledgerModel = row.modelId.takeIf { it.isNotBlank() }?.let { allModels[it] }
            val modelLabel = ledgerModel?.name ?: row.modelId.ifBlank { "未标注模型" }
            val providerLabel = ledgerModel?.provider?.ifBlank { "默认" }
                ?: row.provider.ifBlank { "内置" }
            val key = row.modelId.ifBlank { providerLabel }

            // 同一模型可能分布在多个本地日/供应商桶里，这里按 key 累加成一个模型行。
            val merged = ledgerModelRows[key]
            ledgerModelRows[key] = StatsLedgerModelRow(
                id = key,
                label = modelLabel,
                provider = providerLabel,
                calls = (merged?.calls ?: 0) + row.calls,
                inputTokens = (merged?.inputTokens ?: 0L) + row.inputTokens,
                outputTokens = (merged?.outputTokens ?: 0L) + row.outputTokens,
                reasoningTokens = (merged?.reasoningTokens ?: 0L) + row.reasoningTokens,
                cacheReadTokens = (merged?.cacheReadTokens ?: 0L) + row.cacheReadTokens,
                cacheWriteTokens = (merged?.cacheWriteTokens ?: 0L) + row.cacheWriteTokens,
            )

            val day = runCatching { LocalDate.parse(row.localDay) }.getOrNull() ?: continue
            if (day.isBefore(trendStart) || day.isAfter(trendEnd)) continue
            val dayMap = ledgerTrendBuckets.getOrPut(day) { mutableMapOf() }
            dayMap[modelLabel] = (dayMap[modelLabel] ?: StatsTokenBucket()).add(
                input = row.inputTokens,
                output = row.outputTokens,
                cached = row.cacheReadTokens,
                // 推理与缓存写入归入 uncategorized，才能计入 totalTokens、参与柱高。
                uncategorized = row.reasoningTokens + row.cacheWriteTokens,
                activity = row.calls,
            )
        }

        val ledgerTrend = ledgerTrendBuckets.entries
            .sortedBy { it.key }
            .map { (date, map) -> StatsTrendDay(date = date, providerTokens = map) }

        val ledgerRecentCalls = ledgerRecentRows.map { row ->
            val ledgerModel = row.modelId?.takeIf { it.isNotBlank() }?.let { allModels[it] }
            StatsLedgerCallRow(
                id = row.id,
                createdAt = row.createdAt,
                label = ledgerModel?.name ?: row.modelId?.takeIf { it.isNotBlank() } ?: "未标注模型",
                provider = ledgerModel?.provider?.ifBlank { "默认" }
                    ?: row.provider?.takeIf { it.isNotBlank() } ?: "内置",
                inputTokens = row.inputTokens,
                outputTokens = row.outputTokens,
                reasoningTokens = row.reasoningTokens,
                cacheReadTokens = row.cacheReadTokens,
                cacheWriteTokens = row.cacheWriteTokens,
            )
        }

        // 成本一律留空：estimatedCostUsd 需要模型定价表支撑，当前没有任何定价来源，
        // 宁可显式呈现「未计算」，也不给一个看起来像真的估算数字。
        val ledger = StatsLedger(
            calls = ledgerCalls,
            inputTokens = ledgerInput,
            outputTokens = ledgerOutput,
            reasoningTokens = ledgerReasoning,
            cacheReadTokens = ledgerCacheRead,
            cacheWriteTokens = ledgerCacheWrite,
            pricedCalls = 0,
            estimatedCostUsd = null,
            models = ledgerModelRows.values.sortedByDescending { it.calls }.take(20),
            recentCalls = ledgerRecentCalls,
            trend = ledgerTrend,
        )

        StatsSnapshot(
            range = range,
            summary = StatsSummary(
                totalConversations = totalSessions,
                totalMessages = totalMessages,
                inputTokens = totalInputTokens,
                outputTokens = totalOutputTokens,
                cachedTokens = totalCachedTokens,
                launchCount = launchCount,
            ),
            heatmap = heatmapDays,
            trend = trendList,
            modelRank = modelRank,
            assistantRank = assistantRank,
            topicRank = topicRank,
            ledger = ledger,
        )
    }

}

internal data class AggregateTokenContribution(
    val input: Long,
    val output: Long,
    val cached: Long,
)

/**
 * 把 SQL 聚合行转成与区间汇总相同的 input/output/cached 口径。
 * 趋势图按天桶必须走这条路径，否则只有 activityCount、totalTokens 恒为 0。
 */
internal fun tokenContributionFromAggregate(
    customType: String?,
    promptTokens: Long,
    completionTokens: Long,
    cachedTokens: Long,
    textChars: Long,
    reasoningChars: Long,
): AggregateTokenContribution = when (customType) {
    "user" -> AggregateTokenContribution(
        input = estimateTokensFromChars(textChars),
        output = 0L,
        cached = 0L,
    )
    "assistant" -> AggregateTokenContribution(
        input = promptTokens,
        output = if (completionTokens > 0L) {
            completionTokens
        } else {
            estimateTokensFromChars(textChars + reasoningChars)
        },
        cached = cachedTokens,
    )
    else -> AggregateTokenContribution(0L, 0L, 0L)
}

/**
 * 由「字符数」估算 token —— 供 SQL 聚合路径使用。
 *
 * 原实现按字符逐个数权重（CJK 记 2、ASCII 记 1）再乘 0.75；SQL 侧 LENGTH() 只能拿到字符数、
 * 拿不到原文，故这里直接用字符数近似：按 1 字符 ≈ 1 权重保守估计，再乘同一个 0.75 系数，
 * 保证与逐字符版本同量级、不产生数量级偏差。
 */
internal fun estimateTokensFromChars(chars: Long): Long {
    if (chars <= 0L) return 0L
    return (chars * 0.75).toLong().coerceAtLeast(1L)
}
