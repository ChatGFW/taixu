package top.wkbin.taixu.ui.chat

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import top.wkbin.taixu.feature.chat.R
import top.wkbin.taixu.harness.*
import top.wkbin.taixu.ui.components.*
import top.wkbin.taixu.ui.theme.semanticColors

@Composable
internal fun ToolStepHeader(call: ToolCall, result: ToolResult?, running: Boolean) {
    val renderer = LocalToolUiRegistry.current.resolve(call)
    val ui = ToolUiContext(call, result, workspace = "", executing = running)
    val state = toolStepState(result, running)
    val stateText = stringResource(toolStateLabel(state))
    val tint = when (state) {
        ToolStepState.FAILED, ToolStepState.UNKNOWN -> MaterialTheme.colorScheme.error
        ToolStepState.WAITING, ToolStepState.DEFERRED -> semanticColors().warning
        ToolStepState.SUCCEEDED, ToolStepState.RUNNING, ToolStepState.INCOMPLETE -> chatProcessColor()
    }
    Row(Modifier.fillMaxWidth().heightIn(min = 28.dp).padding(horizontal = 2.dp, vertical = 4.dp)
        .semantics { stateDescription = stateText },
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        ChatProcessIcon(renderer.icon(ui), running = state == ToolStepState.RUNNING)
        val target = renderer.target(ui)
        val title = renderer.title(ui)
        Text(if (target == null) title else "$title · $target", Modifier.weight(1f).then(chatProcessPulse(state == ToolStepState.RUNNING)),
            style = MaterialTheme.typography.bodySmall, color = chatProcessColor(),
            maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (state != ToolStepState.SUCCEEDED && state != ToolStepState.RUNNING) Text(stateText,
            style = MaterialTheme.typography.labelSmall, color = tint, maxLines = 1)
        val diff = remember(renderer, call, result) {
            renderer.diffStat(ui)
        }
        diff?.let { (added, deleted) -> DiffStatBadge(added, deleted) }
        val duration = result?.durationMs ?: result?.let { (it.createdAt - call.createdAt).coerceAtLeast(0) }
        ChatProcessTimer(call.id, state == ToolStepState.RUNNING, call.createdAt, duration)
        RuntimeIcon(RuntimeIconName.ChevronRight,
            Modifier.size(16.dp), chatProcessColor())
    }
}

internal fun toolStateLabel(state: ToolStepState): Int = when (state) {
    ToolStepState.RUNNING -> R.string.chat_step_running
    ToolStepState.WAITING -> R.string.chat_step_waiting
    ToolStepState.DEFERRED -> R.string.chat_step_deferred
    ToolStepState.SUCCEEDED -> R.string.chat_step_succeeded
    ToolStepState.FAILED -> R.string.chat_step_failed
    ToolStepState.UNKNOWN -> R.string.chat_step_unknown
    ToolStepState.INCOMPLETE -> R.string.chat_step_incomplete
}

/** Deliberately exclude command bodies, file content, URLs and arbitrary JSON from the collapsed summary. */
internal fun toolStepTarget(call: ToolCall): String? {
    val key = when (call.tool) {
        HarnessTool.READ, HarnessTool.WRITE, HarnessTool.EDIT -> "path"
        HarnessTool.LOAD_SKILL, HarnessTool.LOAD_RULE -> "name"
        HarnessTool.PROCESS -> "id"
        else -> return null
    }
    val base = (call.args[key] as? JsonPrimitive)?.contentOrNull?.take(512)
        ?.replace('\\', '/')?.trimEnd('/')?.substringAfterLast('/')
        ?.replace(Regex("[\\r\\n\\t]+"), " ")?.take(100)?.takeIf { it.isNotBlank() }
    if (call.tool != HarnessTool.EDIT) return base
    val hashline = top.wkbin.taixu.harness.text.HashlineEditArgs.opsLabel(call.args) ?: return base
    return when {
        base != null -> "$base · $hashline"
        else -> hashline
    }
}
