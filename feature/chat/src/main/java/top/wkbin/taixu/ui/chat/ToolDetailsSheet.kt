package top.wkbin.taixu.ui.chat

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import top.wkbin.taixu.ui.miuix.RuntimeTheme
import top.yukonga.miuix.kmp.basic.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import top.wkbin.taixu.feature.chat.R
import top.wkbin.taixu.harness.*
import top.wkbin.taixu.ui.components.*

/** Inspect commands in a modal, so opening output never changes the chat list's height or anchor. */
@Composable
internal fun ToolDetailsSheet(call: ToolCall, result: ToolResult?, workspace: String,
    onOpenFile: ((String, String) -> Unit)?, executing: Boolean, canRetry: Boolean,
    onRetry: () -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val renderer = LocalToolUiRegistry.current.resolve(call)
    val ui = ToolUiContext(call, result, workspace, executing)
    val previewLabel = renderer.previewLabel
    val fileTool = previewLabel != null
    var selectedTab by rememberSaveable(call.id) { mutableIntStateOf(if (fileTool) 2 else 0) }
    val input = remember(renderer, ui) { renderer.input(ui) }
    val currentText = if (selectedTab == 1) result?.output.orEmpty() else input
    val copied = stringResource(R.string.chat_tool_details_copied)
    ChatBottomSheet(
        onDismissRequest = onDismiss,
        title = {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                RuntimeIcon(renderer.icon(ui), Modifier.size(22.dp), RuntimeTheme.colorScheme.onSurface)
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.chat_tool_details_title), style = RuntimeTheme.typography.titleMedium)
                    Text(renderer.title(ui), style = RuntimeTheme.typography.bodySmall,
                        color = RuntimeTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                RuntimeIconButton(onClick = onDismiss, contentDescription = stringResource(R.string.chat_close)) {
                    RuntimeIcon(RuntimeIconName.Close, Modifier.size(20.dp))
                }
            }
        },
        text = {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                top.yukonga.miuix.kmp.basic.TabRow(
                tabs = buildList {
                    add(stringResource(if (call.tool == HarnessTool.BASE) R.string.chat_tool_details_command else R.string.chat_tool_details_arguments))
                    add(stringResource(R.string.chat_tool_details_output))
                    if (fileTool) add(stringResource(previewLabel))
                },
                selectedTabIndex = selectedTab,
                onTabSelected = { selectedTab = it },
            )
                Text(stringResource(toolStateLabel(toolStepState(result, executing))),
                    style = RuntimeTheme.typography.labelMedium, color = RuntimeTheme.colorScheme.onSurfaceVariant)
                Column(Modifier.fillMaxWidth().weight(1f, fill = false).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (selectedTab == 2 && fileTool) {
                        renderer.Preview(ui, onOpenFile?.let { open -> { project, path -> onDismiss(); open(project, path) } })
                    } else if (currentText.isNotEmpty()) {
                        SelectionContainer {
                            Text(currentText.take(64_000), style = RuntimeTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace))
                        }
                        if (currentText.length > 64_000) Text(stringResource(R.string.chat_tool_details_preview),
                            style = RuntimeTheme.typography.bodySmall, color = RuntimeTheme.colorScheme.onSurfaceVariant)
                    } else {
                        Text(stringResource(R.string.chat_tool_details_no_output), style = RuntimeTheme.typography.bodyMedium,
                            color = RuntimeTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        },
        dismissButton = {
            if (selectedTab != 2 && currentText.isNotEmpty()) RuntimeTextButton(onClick = {
                (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager)
                    .setPrimaryClip(ClipData.newPlainText(toolDisplayName(call), currentText))
                Toast.makeText(context, copied, Toast.LENGTH_SHORT).show()
            }) {
                RuntimeIcon(RuntimeIconName.Copy, Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.chat_copy))
            }
        },
        confirmButton = {
            if (canRetry && result != null) RuntimeTextButton(onClick = { onDismiss(); onRetry() }) {
                Text(stringResource(R.string.chat_retry_from_tool), maxLines = 1)
            }
        },
    )
}

private val toolDetailsJson = Json { prettyPrint = true }

internal fun toolDetailInput(call: ToolCall): String =
    if (call.tool == HarnessTool.BASE) (call.args["command"] as? JsonPrimitive)?.contentOrNull
        ?: (call.args["cmd"] as? JsonPrimitive)?.contentOrNull ?: call.args.toString()
    else toolDetailsJson.encodeToString(JsonObject.serializer(), call.args)
