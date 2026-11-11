package top.wkbin.taixu.core.model

/**
 * 聊天页顶部右侧可置顶的快捷动作（不含始终保留的「更多」）。
 *
 * 顺序即展示顺序；最多 [MAX_PINNED] 个。缺省/脏数据回落到 [DEFAULT_PINNED]，
 * 与历史版本「智能体 + 工作区 + 更多」一致。
 */
enum class ChatTopToolbarAction(val id: String) {
    BOTS("bots"),
    WORKSPACE("workspace"),
    MEMORY("memory"),
    OUTLINE("outline"),
    EXPORT_MARKDOWN("export_markdown"),
    BROWSER("browser"),
    REPOSITORY("repository"),
    SPEECH("speech"),
    RUN_MODE("run_mode"),
    BRANCHES("branches"),
    RUNTIME("runtime"),
    TOOLCHAIN("toolchain"),
    REASONING("reasoning"),
    FLOATING_CHAT("floating_chat");

    companion object {
        const val MAX_PINNED = 3

        val DEFAULT_PINNED: List<ChatTopToolbarAction> = listOf(BOTS, WORKSPACE)

        fun fromId(id: String): ChatTopToolbarAction? = entries.firstOrNull { it.id == id }

        /**
         * 解析持久化串。
         * - `null`：从未写入 → 默认 [DEFAULT_PINNED]
         * - `""`：用户明确清空 → 空列表（仅保留「更多」）
         * - 未知 id 忽略；超长截断；若清洗后为空但原文非空 → 回落默认
         */
        fun parsePinned(raw: String?): List<ChatTopToolbarAction> {
            if (raw == null) return DEFAULT_PINNED
            if (raw.isEmpty()) return emptyList()
            val parsed = raw.split(',')
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .mapNotNull(::fromId)
                .distinct()
                .take(MAX_PINNED)
            return parsed.ifEmpty { DEFAULT_PINNED }
        }

        fun encode(actions: List<ChatTopToolbarAction>): String =
            actions.distinct().take(MAX_PINNED).joinToString(",") { it.id }
    }
}
