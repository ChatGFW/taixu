package top.wkbin.taixu.harness

import kotlinx.serialization.json.JsonObject

/**
 * 当前轮工具结果的预算兜底。
 *
 * [ContextWindowPolicy.truncateStaleToolResults] 只压缩"老轮次"结果——最后一条用户消息
 * 之后的结果一律原样保护。单轮长时间自主运行时，这会让几十条大输出累积到折叠线以上，
 * 只能靠一次 LLM 整段压缩收场；而在 4MB 请求体上限之前，物理体积预检也不会出手。
 *
 * 这里在越过折叠线时先用和体积治理同一套头尾压缩口径收一轮，只保护最近几条结果，
 * 全文仍可通过 history_read 回读。返回值是同一实例表示无需改动。
 */
internal object CurrentTurnToolResultBudget {

    /** 与历史压缩的保护窗口一致：模型下一步决策主要依赖最近的几条结果。 */
    private const val KEEP_RECENT_RESULTS = 4

    fun apply(
        messages: List<HarnessMessage>,
        toolCallDetails: Map<String, Pair<String, JsonObject>>,
        budgetTokens: Int,
        keepRecentResults: Int = KEEP_RECENT_RESULTS,
    ): List<HarnessMessage> {
        if (messages.isEmpty() || budgetTokens <= 0) return messages
        val lastUserIndex = messages.indexOfLast { it is UserMessage }
        val activeIndexes = messages.indices.filter {
            it > lastUserIndex && messages[it] is ToolResult
        }
        if (activeIndexes.size <= keepRecentResults) return messages

        var tokens = activeIndexes.sumOf {
            ContextWindowPolicy.estimateTokens((messages[it] as ToolResult).output)
        }
        if (tokens <= budgetTokens) return messages

        val protected = activeIndexes.takeLast(keepRecentResults)
            .mapTo(HashSet()) { (messages[it] as ToolResult).id }
        val out = messages.toMutableList()
        // 从最大的开始压：回收同样的预算，触及的消息条数最少。
        for (index in activeIndexes.sortedByDescending { (messages[it] as ToolResult).output.length }) {
            if (tokens <= budgetTokens) break
            val result = out[index] as ToolResult
            if (result.id in protected) continue
            val (name, args) = toolCallDetails[result.toolCallId] ?: (null to null)
            val compacted = ContextWindowPolicy.compactActiveToolOutput(name, args, result.output, result.success) +
                "\n[本轮较早的工具输出已压缩到上下文预算内；全文在会话记录中，需要细节时调用 history_read(message_id=\"${result.id}\") 回读]"
            if (compacted.length >= result.output.length) continue
            val saved = ContextWindowPolicy.estimateTokens(result.output) -
                ContextWindowPolicy.estimateTokens(compacted)
            if (saved <= 0) continue
            out[index] = result.copy(output = compacted)
            tokens -= saved
        }
        return out
    }
}
