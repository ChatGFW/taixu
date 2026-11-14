package top.wkbin.taixu.ui.chat

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import top.wkbin.taixu.feature.chat.R
import top.wkbin.taixu.harness.QueuedPrompt
import top.wkbin.taixu.harness.queue.PromptQueue
import top.wkbin.taixu.ui.components.RuntimeIcon
import top.wkbin.taixu.ui.components.RuntimeIconButton
import top.wkbin.taixu.ui.components.RuntimeIconName
import top.wkbin.taixu.ui.miuix.RuntimeTheme
import top.wkbin.taixu.ui.miuix.Surface
import top.wkbin.taixu.ui.theme.chatBackdropSurface
import top.wkbin.taixu.ui.theme.chatSoftDropShadow
import top.wkbin.taixu.ui.theme.semanticColors
import top.yukonga.miuix.kmp.basic.Text

/**
 * 排队指令条：悬浮在输入框上方（与 StickyPlanBar 同级），不叠进输入胶囊。
 */
@Composable
internal fun QueuedPromptStack(
    prompts: List<QueuedPrompt>,
    onEdit: (QueuedPrompt) -> Unit,
    onRemove: (QueuedPrompt) -> Unit,
    onConvertToSteer: ((QueuedPrompt) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    if (prompts.isEmpty()) return
    val queueShape = RuntimeTheme.shapes.small
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .chatSoftDropShadow(queueShape),
        color = chatBackdropSurface(RuntimeTheme.colorScheme.surfaceContainerLow),
        shape = queueShape,
        border = BorderStroke(1.dp, RuntimeTheme.colorScheme.outline.copy(alpha = 0.25f)),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 148.dp)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 6.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            prompts.forEach { prompt ->
                QueuedPromptRow(
                    prompt = prompt,
                    onEdit = onEdit,
                    onRemove = onRemove,
                    onConvertToSteer = onConvertToSteer,
                )
            }
        }
    }
}

@Composable
private fun QueuedPromptRow(
    prompt: QueuedPrompt,
    onEdit: (QueuedPrompt) -> Unit,
    onRemove: (QueuedPrompt) -> Unit,
    onConvertToSteer: ((QueuedPrompt) -> Unit)?,
) {
    val (label, tint) = when (prompt.queue) {
        PromptQueue.STEER -> stringResource(R.string.chat_label_steer) to semanticColors().agent
        PromptQueue.FOLLOW_UP -> stringResource(R.string.chat_label_follow_up) to RuntimeTheme.colorScheme.tertiary
        PromptQueue.NEXT_RUN -> stringResource(R.string.chat_label_queued) to RuntimeTheme.colorScheme.secondary
    }
    val editable = !prompt.isEditing && prompt.message.initiatorKind == "person" &&
        prompt.message.groupWorkId == null && prompt.message.groupId == null
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            if (prompt.isEditing) stringResource(R.string.chat_queue_editing) else label,
            style = RuntimeTheme.typography.labelSmall,
            color = tint,
        )
        Spacer(Modifier.width(8.dp))
        Text(
            prompt.message.text.ifBlank { stringResource(R.string.chat_attachment_message) },
            style = RuntimeTheme.typography.labelMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (prompt.queue == PromptQueue.NEXT_RUN && onConvertToSteer != null) {
            RuntimeIconButton(
                onClick = { onConvertToSteer(prompt) },
                enabled = editable,
                contentDescription = stringResource(R.string.chat_label_steer),
            ) {
                RuntimeIcon(RuntimeIconName.Tune, Modifier.size(18.dp), semanticColors().agent)
            }
        }
        RuntimeIconButton(
            onClick = { onEdit(prompt) },
            enabled = editable,
            contentDescription = stringResource(R.string.chat_queue_edit_title),
        ) {
            RuntimeIcon(RuntimeIconName.Edit, Modifier.size(18.dp))
        }
        RuntimeIconButton(
            onClick = { onRemove(prompt) },
            contentDescription = stringResource(R.string.chat_delete),
        ) {
            RuntimeIcon(RuntimeIconName.Close, Modifier.size(18.dp))
        }
    }
}
