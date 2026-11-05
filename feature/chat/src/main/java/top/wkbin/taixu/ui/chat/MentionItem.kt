package top.wkbin.taixu.ui.chat

import top.wkbin.taixu.ui.components.RuntimeIconName

data class MentionItem(
    val id: String,
    val name: String,
    val description: String,
    val category: String,
    val type: MentionType,
    val icon: RuntimeIconName,
    val sessionId: String? = null,
    val workspace: String? = null,
    val completionQuery: String? = null,
)

enum class MentionType {
    SKILL, MCP_SERVER, FILE, DIRECTORY
}

/** Suggestions carry their originating world/query so a delayed click cannot replace a new draft. */
internal fun insertMention(text: String, item: MentionItem, sessionId: String, workspace: String): String? {
    if (item.sessionId != null && (item.sessionId != sessionId || item.workspace != workspace)) return null
    val atIndex = trailingMentionStart(text) ?: return null
    if (item.completionQuery != null && text.substring(atIndex + 1).removePrefix("\"") != item.completionQuery) return null
    val reference = if (item.type == MentionType.FILE || item.type == MentionType.DIRECTORY)
        completionReference(item.id, item.type == MentionType.DIRECTORY) else "@${item.id} "
    return text.substring(0, atIndex) + reference
}
