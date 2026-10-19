package top.wkbin.taixu.ui.bot

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import top.wkbin.taixu.core.model.bot.GroupAttachment
import top.wkbin.taixu.core.model.bot.GroupMessage
import top.wkbin.taixu.core.model.bot.GroupToolStep
import top.wkbin.taixu.feature.bot.R
import top.wkbin.taixu.ui.components.RuntimeIcon
import top.wkbin.taixu.ui.components.RuntimeIconButton
import top.wkbin.taixu.ui.components.RuntimeIconName

private val REACTION_PALETTE = listOf("👍", "❤️", "😂", "🎉", "👀", "🤔")

@Composable
internal fun ReactionBar(
    message: GroupMessage,
    onToggle: (messageId: String, emoji: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        message.reactions.groupBy { it.emoji }.forEach { (emoji, reactions) ->
            val mine = reactions.any { it.reactorId == CURRENT_USER_ID }
            Surface(
                onClick = { onToggle(message.id, emoji) },
                shape = RoundedCornerShape(50),
                color = if (mine) {
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
                } else {
                    MaterialTheme.colorScheme.surfaceContainerHigh
                },
                contentColor = if (mine) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            ) {
                Text(
                    text = "$emoji ${reactions.size}",
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
                )
            }
        }
        Box {
            Surface(
                onClick = { expanded = true },
                shape = RoundedCornerShape(50),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            ) {
                RuntimeIcon(
                    RuntimeIconName.Plus,
                    Modifier.padding(horizontal = 6.dp, vertical = 4.dp).size(12.dp),
                )
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                Row(Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
                    REACTION_PALETTE.forEach { emoji ->
                        Text(
                            text = emoji,
                            style = MaterialTheme.typography.titleLarge,
                            modifier = Modifier
                                .clickable {
                                    expanded = false
                                    onToggle(message.id, emoji)
                                }
                                .padding(horizontal = 6.dp, vertical = 4.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun AttachmentChips(
    attachments: List<GroupAttachment>,
    tint: Color,
    modifier: Modifier = Modifier,
) {
    if (attachments.isEmpty()) return
    val context = LocalContext.current
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        attachments.forEach { attachment ->
            Surface(
                onClick = { openAttachment(context, attachment) },
                shape = RoundedCornerShape(8.dp),
                color = tint.copy(alpha = 0.14f),
                contentColor = tint,
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    RuntimeIcon(RuntimeIconName.Attach, Modifier.size(14.dp))
                    Text(
                        text = attachment.name,
                        style = MaterialTheme.typography.labelMedium,
                        maxLines = 1,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (attachment.sizeBytes > 0) {
                        Text(
                            text = formatBytes(attachment.sizeBytes),
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                }
            }
        }
    }
}

private fun openAttachment(context: Context, attachment: GroupAttachment) {
    val uri = runCatching { Uri.parse(attachment.uri) }.getOrNull()
    if (uri == null) {
        Toast.makeText(
            context,
            context.getString(R.string.fbot_attachment_open_failed, attachment.name),
            Toast.LENGTH_SHORT,
        ).show()
        return
    }
    val intent = Intent(Intent.ACTION_VIEW)
        .setDataAndType(uri, attachment.mimeType.ifBlank { "*/*" })
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    runCatching { context.startActivity(intent) }
        .onFailure {
            Toast.makeText(
                context,
                context.getString(R.string.fbot_attachment_open_failed, attachment.name),
                Toast.LENGTH_SHORT,
            ).show()
        }
}

@Composable
internal fun StepTimeline(steps: List<GroupToolStep>) {
    if (steps.isEmpty()) return
    var expanded by rememberSaveable { mutableStateOf(false) }
    val visible = if (expanded) steps else steps.take(3)
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            RuntimeIcon(
                RuntimeIconName.Wrench,
                Modifier.size(13.dp),
                MaterialTheme.colorScheme.outline,
            )
            Text(
                text = stringResource(R.string.fbot_steps_title, steps.size),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline,
            )
            if (steps.size > 3) {
                Text(
                    text = stringResource(
                        if (expanded) R.string.fbot_steps_collapse else R.string.fbot_steps_expand,
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.clickable { expanded = !expanded },
                )
            }
        }
        visible.forEach { step ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                when (step.success) {
                    true -> RuntimeIcon(
                        RuntimeIconName.Check,
                        Modifier.size(12.dp),
                        MaterialTheme.colorScheme.tertiary,
                    )
                    false -> RuntimeIcon(
                        RuntimeIconName.Close,
                        Modifier.size(12.dp),
                        MaterialTheme.colorScheme.error,
                    )
                    null -> Box(
                        Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary),
                    )
                }
                Text(
                    text = step.name,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
                if (step.durationMs != null) {
                    Text(
                        text = "${step.durationMs}ms",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline,
                    )
                }
            }
        }
    }
}

@Composable
internal fun MessageMenuButton(text: String) {
    var expanded by remember { mutableStateOf(false) }
    val clipboard = LocalClipboard.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    Box {
        RuntimeIconButton(
            onClick = { expanded = true },
            modifier = Modifier.size(28.dp),
            contentDescription = stringResource(R.string.fbot_message_actions),
        ) {
            RuntimeIcon(RuntimeIconName.More, Modifier.size(15.dp), MaterialTheme.colorScheme.outline)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.fbot_copy)) },
                leadingIcon = { RuntimeIcon(RuntimeIconName.Copy, Modifier.size(16.dp)) },
                onClick = {
                    expanded = false
                    scope.launch {
                        clipboard.setClipEntry(ClipEntry(ClipData.newPlainText("taixu-group-message", text)))
                    }
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.fbot_share)) },
                leadingIcon = { RuntimeIcon(RuntimeIconName.OpenInNew, Modifier.size(16.dp)) },
                onClick = {
                    expanded = false
                    val send = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, text)
                    }
                    context.startActivity(Intent.createChooser(send, null))
                },
            )
        }
    }
}

@Composable
internal fun TypingDots(color: Color) {
    val transition = rememberInfiniteTransition(label = "group_typing")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 3f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "typing_phase",
    )
    Row(horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.CenterVertically) {
        repeat(3) { index ->
            val lit = phase.toInt().coerceAtMost(2) == index
            Box(
                Modifier
                    .size(5.dp)
                    .clip(CircleShape)
                    .background(color.copy(alpha = if (lit) 1f else 0.28f)),
            )
        }
    }
}

private const val CURRENT_USER_ID = "user_me"
