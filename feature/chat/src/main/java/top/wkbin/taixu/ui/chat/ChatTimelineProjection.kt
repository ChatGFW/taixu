package top.wkbin.taixu.ui.chat

import top.wkbin.taixu.harness.*
import top.wkbin.taixu.harness.effects.ToolRecoveryNotice

internal data class ToolStepsItem(val steps: List<ChatRenderItem.MessageItem>) : ChatRenderItem {
    // Anchor to the first call so streaming appends do not reset expansion or scroll position.
    override val stableKey: String get() = "tool_steps_${steps.first().message.id}"
}

internal fun projectChatTimeline(
    messages: List<HarnessMessage>, toolResults: Map<String, ToolResult> = emptyMap(),
    expandedOverrides: Map<String, Boolean> = emptyMap(), collapseEnabled: Boolean = false,
    revealLimits: Map<String, Int> = emptyMap(),
): List<ChatRenderItem> = groupToolSteps(projectChatMessages(messages, toolResults, expandedOverrides, collapseEnabled, revealLimits))

/** Only adjacent ordinary tools are grouped. Text, interactive surfaces and subagent cards retain their order; question overviews join the chain. */
internal fun groupToolSteps(items: List<ChatRenderItem>): List<ChatRenderItem> = buildList {
    val pending = mutableListOf<ChatRenderItem.MessageItem>()
    fun flush() {
        if (pending.isNotEmpty()) add(ToolStepsItem(pending.toList()))
        pending.clear()
    }
    items.forEach { item ->
        val call = (item as? ChatRenderItem.MessageItem)?.message as? ToolCall
        if (call != null && call.tool != HarnessTool.SUBAGENT && call.tool != HarnessTool.RENDER_SURFACE) {
            pending += item
        } else {
            flush()
            add(item)
        }
    }
    flush()
}

internal fun ChatRenderItem.containsMessage(id: String): Boolean = when (this) {
    is ChatRenderItem.MessageItem -> message.id == id
    is ToolStepsItem -> steps.any { it.message.id == id }
    is ChatRenderItem.CollapseButtonItem -> false
}

internal fun focusedRoundKey(messages: List<HarnessMessage>, id: String?): String? {
    if (id == null) return null
    var roundKey = "__initial_round__"
    messages.forEach {
        if (it is UserMessage) roundKey = it.id
        if (it.id == id) return roundKey
    }
    return null
}

internal enum class ToolStepState { RUNNING, WAITING, DEFERRED, SUCCEEDED, FAILED, UNKNOWN, INCOMPLETE }

internal fun toolStepState(result: ToolResult?, running: Boolean): ToolStepState = when {
    result?.awaitingApproval == true -> ToolStepState.WAITING
    result?.approvalDeferred == true -> ToolStepState.DEFERRED
    result == null -> if (running) ToolStepState.RUNNING else ToolStepState.INCOMPLETE
    result.errorCode == ToolRecoveryNotice.OUTCOME_UNKNOWN -> ToolStepState.UNKNOWN
    result.success -> ToolStepState.SUCCEEDED
    else -> ToolStepState.FAILED
}

/** Show a bounded tail of one continuous chain, plus a focused step and the latest issue. */
internal fun visibleToolChainSteps(steps: List<ChatRenderItem.MessageItem>, results: Map<String, ToolResult>,
    running: Boolean, limit: Int = 8, focusedId: String? = null): List<ChatRenderItem.MessageItem> {
    val attention = steps.lastOrNull {
        toolStepState(results[it.message.id], running) in setOf(ToolStepState.FAILED, ToolStepState.UNKNOWN,
            ToolStepState.WAITING, ToolStepState.DEFERRED)
    }
    val ids = steps.takeLast(limit.coerceAtLeast(1)).map { it.message.id }.toSet() +
        listOfNotNull(attention?.message?.id, focusedId)
    return steps.filter { it.message.id in ids }
}

/** Final visible prose per user turn gets the full toolbar; intermediate updates keep a details menu. */
internal fun assistantToolbarIds(messages: List<HarnessMessage>, running: Boolean): Set<String> = buildSet {
    var candidate: String? = null
    messages.forEach { message ->
        if (message is UserMessage) {
            candidate?.let(::add)
            candidate = null
        } else if (message is AssistantText && message.text.isNotBlank()) candidate = message.id
    }
    if (!running) candidate?.let(::add)
}
