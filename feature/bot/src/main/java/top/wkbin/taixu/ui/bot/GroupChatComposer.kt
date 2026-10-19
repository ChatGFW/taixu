package top.wkbin.taixu.ui.bot

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import top.wkbin.taixu.core.model.bot.Bot
import top.wkbin.taixu.core.model.bot.GroupAttachment
import top.wkbin.taixu.feature.bot.R
import top.wkbin.taixu.ui.components.RuntimeCircularProgressIndicator
import top.wkbin.taixu.ui.components.RuntimeFilterChip
import top.wkbin.taixu.ui.components.RuntimeIcon
import top.wkbin.taixu.ui.components.RuntimeIconButton
import top.wkbin.taixu.ui.components.RuntimeIconName

@Composable
internal fun GroupChatComposer(
    members: List<Bot>,
    value: String,
    attachments: List<GroupAttachment>,
    sending: Boolean,
    busy: Boolean,
    workingCount: Int,
    stopping: Boolean,
    onValueChange: (String) -> Unit,
    onMention: (String) -> Unit,
    onAttach: () -> Unit,
    onRemoveAttachment: (GroupAttachment) -> Unit,
    onSend: () -> Unit,
    onStop: () -> Unit,
) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, tonalElevation = 3.dp) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .imePadding()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (members.isNotEmpty()) {
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    members.forEach { bot ->
                        RuntimeFilterChip(
                            selected = false,
                            onClick = { onMention(bot.name) },
                            label = { Text("@${bot.name}", style = MaterialTheme.typography.labelMedium) },
                            leadingIcon = { BotAvatar(bot, 18.dp) },
                        )
                    }
                }
            }
            if (attachments.isNotEmpty()) {
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    attachments.forEach { attachment ->
                        RuntimeFilterChip(
                            selected = false,
                            onClick = { onRemoveAttachment(attachment) },
                            label = {
                                Text(
                                    text = attachment.name,
                                    style = MaterialTheme.typography.labelMedium,
                                    maxLines = 1,
                                )
                            },
                            leadingIcon = {
                                RuntimeIcon(RuntimeIconName.Attach, Modifier.size(14.dp))
                            },
                            trailingIcon = {
                                RuntimeIcon(RuntimeIconName.Close, Modifier.size(14.dp))
                            },
                        )
                    }
                }
            }
            if (busy) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    RuntimeCircularProgressIndicator(Modifier.size(12.dp), strokeWidth = 2.dp)
                    Text(
                        text = stringResource(R.string.fbot_group_running, workingCount.coerceAtLeast(1)),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = value,
                    onValueChange = onValueChange,
                    placeholder = { Text(stringResource(R.string.fbot_input_hint)) },
                    maxLines = 4,
                    shape = RoundedCornerShape(16.dp),
                    leadingIcon = {
                        RuntimeIconButton(
                            onClick = onAttach,
                            modifier = Modifier.size(36.dp),
                            contentDescription = stringResource(R.string.fbot_attach),
                        ) {
                            RuntimeIcon(
                                RuntimeIconName.Attach,
                                Modifier.size(18.dp),
                                MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surface,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                    ),
                    modifier = Modifier.weight(1f),
                )
                val hasContent = value.isNotBlank() || attachments.isNotEmpty()
                val canSend = hasContent && !sending && !stopping
                // 有输入时优先显示发送，允许在 Bot 执行期间追加消息；只有输入为空才显示停止。
                val showStop = busy && !hasContent
                val buttonEnabled = if (showStop) !stopping else canSend
                val buttonColor = when {
                    showStop -> MaterialTheme.colorScheme.error
                    sending || canSend -> MaterialTheme.colorScheme.primary
                    else -> MaterialTheme.colorScheme.surfaceContainerHigh
                }
                Surface(
                    color = buttonColor,
                    shape = CircleShape,
                    modifier = Modifier.size(46.dp),
                ) {
                    RuntimeIconButton(
                        onClick = if (showStop) onStop else onSend,
                        enabled = buttonEnabled,
                        modifier = Modifier.fillMaxSize(),
                        contentDescription = stringResource(if (showStop) R.string.fbot_stop else R.string.fbot_send),
                    ) {
                        when {
                            stopping -> RuntimeCircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                color = MaterialTheme.colorScheme.onError,
                                trackColor = MaterialTheme.colorScheme.onError.copy(alpha = 0.25f),
                                strokeWidth = 2.dp,
                            )
                            showStop -> RuntimeIcon(
                                RuntimeIconName.Stop,
                                Modifier.size(20.dp),
                                tint = MaterialTheme.colorScheme.onError,
                            )
                            sending -> RuntimeCircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                color = MaterialTheme.colorScheme.onPrimary,
                                trackColor = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.25f),
                                strokeWidth = 2.dp,
                            )
                            else -> RuntimeIcon(
                                RuntimeIconName.ArrowUp,
                                Modifier.size(20.dp),
                                tint = if (canSend) MaterialTheme.colorScheme.onPrimary
                                else MaterialTheme.colorScheme.outline,
                            )
                        }
                    }
                }
            }
        }
    }
}
