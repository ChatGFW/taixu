package top.wkbin.taixu.ui.chat

import androidx.compose.foundation.layout.*
import top.wkbin.taixu.ui.miuix.RuntimeTheme
import top.yukonga.miuix.kmp.basic.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import top.wkbin.taixu.feature.chat.R
import top.wkbin.taixu.harness.QueuedPrompt
import top.wkbin.taixu.harness.queue.PromptQueue
import top.wkbin.taixu.ui.components.RuntimeCard
import top.wkbin.taixu.ui.components.RuntimeIcon
import top.wkbin.taixu.ui.components.RuntimeIconButton
import top.wkbin.taixu.ui.components.RuntimeIconName
import top.wkbin.taixu.ui.theme.semanticColors

@Composable
internal fun QueuedPromptStack(
    prompts: List<QueuedPrompt>,
    onEdit: (QueuedPrompt) -> Unit,
    onRemove: (QueuedPrompt) -> Unit,
    onConvertToSteer: ((QueuedPrompt) -> Unit)? = null,
) {
    if (prompts.isEmpty()) return
    Column(Modifier.fillMaxWidth().padding(top = 4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        prompts.forEach { prompt ->
            val (label, tint) = when (prompt.queue) {
                PromptQueue.STEER -> stringResource(R.string.chat_label_steer) to semanticColors().agent
                PromptQueue.FOLLOW_UP -> stringResource(R.string.chat_label_follow_up) to RuntimeTheme.colorScheme.tertiary
                PromptQueue.NEXT_RUN -> stringResource(R.string.chat_label_queued) to RuntimeTheme.colorScheme.secondary
            }
            val editable = !prompt.isEditing && prompt.message.initiatorKind == "person" &&
                prompt.message.groupWorkId == null && prompt.message.groupId == null
            RuntimeCard(containerColor = tint.copy(alpha = 0.08f), contentPadding = PaddingValues(start = 8.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(if (prompt.isEditing) stringResource(R.string.chat_queue_editing) else label,
                        style = RuntimeTheme.typography.labelSmall, color = tint)
                    Spacer(Modifier.width(8.dp))
                    Text(prompt.message.text.ifBlank { stringResource(R.string.chat_attachment_message) },
                        style = RuntimeTheme.typography.labelMedium, maxLines = 1,
                        overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    if (prompt.queue == PromptQueue.NEXT_RUN && onConvertToSteer != null) {
                        RuntimeIconButton(onClick = { onConvertToSteer(prompt) }, enabled = editable,
                            contentDescription = stringResource(R.string.chat_label_steer)) {
                            RuntimeIcon(RuntimeIconName.Tune, Modifier.size(18.dp), semanticColors().agent)
                        }
                    }
                    RuntimeIconButton(onClick = { onEdit(prompt) }, enabled = editable,
                        contentDescription = stringResource(R.string.chat_queue_edit_title)) {
                        RuntimeIcon(RuntimeIconName.Edit, Modifier.size(18.dp))
                    }
                    RuntimeIconButton(onClick = { onRemove(prompt) },
                        contentDescription = stringResource(R.string.chat_delete)) {
                        RuntimeIcon(RuntimeIconName.Close, Modifier.size(18.dp))
                    }
                }
            }
        }
    }
}
