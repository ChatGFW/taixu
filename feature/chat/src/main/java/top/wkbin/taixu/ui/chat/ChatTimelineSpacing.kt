package top.wkbin.taixu.ui.chat

import top.wkbin.taixu.harness.AssistantText
import top.wkbin.taixu.harness.HarnessTool
import top.wkbin.taixu.harness.ToolCall
import top.wkbin.taixu.harness.UserMessage

/** Compact tool rows share one rhythm even when an approval overview is outside a tool group. */
internal fun chatTimelineItemSpacing(previous: ChatRenderItem?, current: ChatRenderItem): Int {
    if (previous == null) return 0
    val message = (current as? ChatRenderItem.MessageItem)?.message
    if (message is UserMessage) return 16
    if (previous.isCompactToolLine() && current.isCompactToolLine()) return 0
    if (previous.isCompactToolLine() || current.isCompactToolLine() ||
        (message is AssistantText && !message.reasoning.isNullOrBlank())) return 4
    return 8
}

private fun ChatRenderItem.isCompactToolLine(): Boolean {
    if (this is ToolStepsItem) return true
    val call = (this as? ChatRenderItem.MessageItem)?.message as? ToolCall ?: return false
    return call.tool != HarnessTool.SUBAGENT && call.tool != HarnessTool.RENDER_SURFACE
}
