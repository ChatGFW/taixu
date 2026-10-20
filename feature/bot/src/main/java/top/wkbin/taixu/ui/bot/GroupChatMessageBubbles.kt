package top.wkbin.taixu.ui.bot

import android.content.ClipData
import android.content.Intent
import android.widget.Toast
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import top.wkbin.taixu.core.model.bot.Bot
import top.wkbin.taixu.core.model.bot.GroupMessage
import top.wkbin.taixu.core.model.bot.GroupSenderType
import top.wkbin.taixu.feature.bot.R
import top.wkbin.taixu.ui.components.MarkdownText
import top.wkbin.taixu.ui.components.RuntimeIcon
import top.wkbin.taixu.ui.components.RuntimeIconButton
import top.wkbin.taixu.ui.components.RuntimeIconName

private val AvatarSize = 32.dp
private val GroupGap = 2.dp

@Composable
internal fun GroupMessageRow(
    message: GroupMessage,
    bot: Bot?,
    firstInGroup: Boolean,
    lastInGroup: Boolean,
    onOpenBot: (String) -> Unit,
    onToggleReaction: (messageId: String, emoji: String) -> Unit,
) {
    when (message.senderType) {
        GroupSenderType.USER -> UserMessageBubble(message, firstInGroup, lastInGroup, onToggleReaction)
        GroupSenderType.BOT ->
            BotMessageBubble(message, bot, firstInGroup, lastInGroup, onOpenBot, onToggleReaction)
        GroupSenderType.SYSTEM -> SystemNotice(message)
    }
}

@Composable
internal fun TimeSeparator(label: String) {
    Box(
        modifier = Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun BotMessageBubble(
    message: GroupMessage,
    bot: Bot?,
    firstInGroup: Boolean,
    lastInGroup: Boolean,
    onOpenBot: (String) -> Unit,
    onToggleReaction: (messageId: String, emoji: String) -> Unit,
) {
    val role = bot?.role.orEmpty()
    val botId = bot?.id
    // 头像跟随作者名放在消息组顶部；同组后续消息用等宽占位保持缩进。
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        if (firstInGroup) {
            BotAvatar(
                seed = bot?.avatarSeed ?: message.senderName,
                colorHex = bot?.colorHex ?: "#3D5AFE",
                size = AvatarSize,
                active = message.isStreaming,
                modifier = if (botId != null) Modifier.clickable { onOpenBot(botId) } else Modifier,
            )
        } else {
            Spacer(Modifier.width(AvatarSize))
        }
        Column(
            modifier = Modifier.weight(1f).padding(start = 10.dp),
            verticalArrangement = Arrangement.spacedBy(GroupGap),
        ) {
            if (firstInGroup) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text = bot?.name ?: message.senderName,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = if (botId != null) Modifier.clickable { onOpenBot(botId) } else Modifier,
                    )
                    if (bot?.isMainBot == true) MainBadge()
                    if (role.isNotBlank()) {
                        Text(
                            text = role,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.outline,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                    } else {
                        Spacer(Modifier.weight(1f))
                    }
                }
            }
            if (message.reasoning.isNotBlank() || (message.isStreaming && message.content.isBlank())) {
                GroupReasoningPanel(message.id, message.reasoning, message.isStreaming)
            }
            if (message.content.isNotBlank() || message.steps.isNotEmpty() || !message.isStreaming) {
                MessageBubble(message, botShape(firstInGroup, lastInGroup))
            }
            if (!message.isStreaming) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ReactionBar(message, onToggleReaction, Modifier.weight(1f, fill = false))
                    Spacer(Modifier.weight(1f))
                    if (message.content.isNotBlank()) MessageMenuButton(message.content)
                }
            }
        }
    }
}

@Composable
private fun MainBadge() {
    Surface(color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f), shape = CircleShape) {
        Text(
            text = stringResource(R.string.fbot_main_bot),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp),
        )
    }
}

@Composable
private fun MessageBubble(message: GroupMessage, shape: Shape) {
    val content = message.content
    when {
        message.isStreaming && content.isBlank() -> Surface(
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            shape = shape,
            modifier = Modifier.widthIn(max = 600.dp),
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                verticalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                StepTimeline(message.steps)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    TypingDots(MaterialTheme.colorScheme.primary)
                    Text(
                        text = stringResource(R.string.fbot_thinking),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        content.startsWith("❌") -> Surface(
            color = MaterialTheme.colorScheme.errorContainer,
            shape = shape,
            modifier = Modifier.widthIn(max = 600.dp),
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                StepTimeline(message.steps)
                Text(
                    text = content,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
            }
        }
        content.startsWith("⏳") -> Row(
            modifier = Modifier.padding(horizontal = 2.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            RuntimeIcon(RuntimeIconName.Sparkles, Modifier.size(14.dp), MaterialTheme.colorScheme.primary)
            Text(
                text = content.removePrefix("⏳").trim(),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        content.startsWith("💭") -> GroupReasoningPanel(
            message.id, content.removePrefix("💭").trim(), message.isStreaming,
        )
        else -> Surface(
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            shape = shape,
            modifier = Modifier.widthIn(max = 600.dp),
        ) {
            Column(Modifier.padding(horizontal = 12.dp, vertical = 9.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                StepTimeline(message.steps)
                MarkdownText(content)
                if (message.isStreaming) TypingDots(MaterialTheme.colorScheme.primary)
            }
        }
    }
}

@Composable
private fun UserMessageBubble(
    message: GroupMessage,
    firstInGroup: Boolean,
    lastInGroup: Boolean,
    onToggleReaction: (messageId: String, emoji: String) -> Unit,
) {
    val clipboard = LocalClipboard.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.spacedBy(GroupGap),
    ) {
        BoxWithConstraints(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
            Surface(
                color = MaterialTheme.colorScheme.primary,
                shape = userShape(firstInGroup, lastInGroup),
                modifier = Modifier
                    .widthIn(max = maxWidth * 0.86f)
                    .pointerInput(message.content) {
                        detectTapGestures(
                            onLongPress = {
                                scope.launch {
                                    clipboard.setClipEntry(
                                        ClipEntry(ClipData.newPlainText("taixu-group-message", message.content)),
                                    )
                                    Toast.makeText(context, context.getString(R.string.fbot_copied), Toast.LENGTH_SHORT).show()
                                }
                            },
                        )
                    },
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    if (message.content.isNotBlank()) {
                        Text(
                            text = message.content,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onPrimary,
                        )
                    }
                    AttachmentChips(
                        attachments = message.attachments,
                        tint = MaterialTheme.colorScheme.onPrimary,
                    )
                }
            }
        }
        ReactionBar(message, onToggleReaction)
    }
}

@Composable
private fun SystemNotice(message: GroupMessage) {
    val content = message.content.trim()
    val warning = content.startsWith("⚠️") || content.startsWith("❌")
    val icon = if (warning) RuntimeIconName.Alert else RuntimeIconName.Info
    val tint = if (warning) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            RuntimeIcon(icon, Modifier.size(16.dp), tint)
            MarkdownText(
                markdown = content.removePrefix("⚠️").removePrefix("❌").trimStart(),
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/** 同组消息只保留外侧圆角，内部用小圆角拼接，贴近参考实现的气泡分组。 */
private fun botShape(first: Boolean, last: Boolean) = RoundedCornerShape(
    topStart = if (first) 12.dp else 4.dp,
    topEnd = 12.dp,
    bottomEnd = 12.dp,
    bottomStart = if (last) 12.dp else 4.dp,
)

private fun userShape(first: Boolean, last: Boolean) = RoundedCornerShape(
    topStart = 12.dp,
    topEnd = if (first) 12.dp else 4.dp,
    bottomEnd = if (last) 12.dp else 4.dp,
    bottomStart = 12.dp,
)
