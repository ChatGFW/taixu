package top.wkbin.taixu.harness.bot.memory

import java.util.concurrent.ConcurrentHashMap

/**
 * Bot 常驻记忆：会话内冻结的「人格 / 核心偏好 / 工作备忘」三档，外加一行按需索引导引。
 *
 * 与 [BotMemoryManager.assembleContext] 的差别：
 * 1. 同一 (bot, session) 只读取一次并冻结，会话内注入内容保持稳定；用户改人格时显式失效。
 * 2. `knowledge` 目录下的 Markdown 不再逐轮投递，改为一行索引导引，正文由 `memory-read` 工具按需读取。
 * 3. 超限只截断注入、不回写文件，并提示模型自行精简（原文不受影响）。
 *
 * 冻结只在进程内有效：进程重启或会话重建后重新读取。常驻内容变化很慢，重读一次的成本
 * 远低于每轮重读 Git 仓库；模型自己的 memory-write 不触发刷新，避免会话内自我漂移。
 */
class BotResidentMemory(
    private val memory: BotMemoryManager,
) {
    private data class Snapshot(val text: String, val capturedAt: Long)

    private val snapshots = ConcurrentHashMap<String, Snapshot>()

    /** 常驻上下文；非 Bot 会话或记忆为空时返回空串，由调用方决定是否挂载。 */
    suspend fun render(botId: String, sessionId: String): String {
        if (botId.isBlank() || sessionId.isBlank()) return ""
        val key = "$botId@$sessionId"
        snapshots[key]?.let { return it.text }
        if (snapshots.size >= MAX_ENTRIES) {
            snapshots.entries.minByOrNull { it.value.capturedAt }?.let { snapshots.remove(it.key) }
        }
        val text = build(botId)
        snapshots[key] = Snapshot(text, System.currentTimeMillis())
        return text
    }

    /** 用户改人格/偏好后调用：让该 Bot 的所有会话在下一轮重新读取。 */
    fun invalidateBot(botId: String) {
        snapshots.keys.removeAll { it.startsWith("$botId@") }
    }

    /** 会话删除或重建时调用，避免快照表随会话增长。 */
    fun invalidateSession(sessionId: String) {
        snapshots.keys.removeAll { it.endsWith("@$sessionId") }
    }

    private suspend fun build(botId: String): String {
        val text = buildString {
            appendSection(botId, "【常驻人格 · persona.md】", BotMemoryManager.PERSONA, PERSONA_CHARS)
            appendSection(botId, "【常驻核心记忆 · preferences.md】", BotMemoryManager.PREFERENCES, CORE_CHARS)
            appendSection(botId, "【最近工作备忘 · dialogue_notes.md】", BotMemoryManager.DIALOGUE_NOTES, NOTES_CHARS)
            if (isNotEmpty()) {
                appendLine()
                appendLine(ON_DEMAND_POINTER)
            }
        }
        if (text.length <= MAX_TOTAL_CHARS) return text
        return text.take(MAX_TOTAL_CHARS - TOTAL_TRUNCATION.length) + TOTAL_TRUNCATION
    }

    private suspend fun StringBuilder.appendSection(
        botId: String,
        header: String,
        path: String,
        limit: Int,
    ) {
        val content = memory.readMemory(botId, path).trim()
        if (content.isEmpty()) return
        appendLine(header)
        appendLine(content.take(limit))
        if (content.length > limit) {
            appendLine(TRUNCATED_NOTE.format(limit, limit))
        }
        appendLine()
    }

    companion object {
        const val PERSONA_CHARS = 1_600
        const val CORE_CHARS = 1_200
        const val NOTES_CHARS = 1_200
        const val MAX_TOTAL_CHARS = 4_400
        private const val MAX_ENTRIES = 128

        private const val TRUNCATED_NOTE = "（超出 %d 字符上限，本条只注入前 %d 字符；文件未改动，请在合适时机精简。）"
        private const val TOTAL_TRUNCATION = "（常驻记忆超出总长上限，已截断注入；原文未改动。）"
        private const val ON_DEMAND_POINTER =
            "【按需记忆】领域知识（knowledge/*.md）不常驻；需要细节时调用 memory-read 读取，避免每轮重复投递。"
    }
}
