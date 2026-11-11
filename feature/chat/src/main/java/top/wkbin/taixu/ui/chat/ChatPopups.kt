package top.wkbin.taixu.ui.chat

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import top.wkbin.taixu.ui.miuix.HorizontalDivider
import top.wkbin.taixu.ui.miuix.RuntimeTheme
import top.wkbin.taixu.ui.miuix.Surface
import top.yukonga.miuix.kmp.basic.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import top.wkbin.taixu.feature.chat.R
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import top.wkbin.taixu.ui.components.RuntimeIcon

/**
 * 输入框上方的斜杠指令 / @能力 内联建议面板（非 WindowListPopup）。
 * 锚定在 Composer 内容区，故意不用独立 window popup。
 */
@Composable
internal fun SlashCommandPopup(
    commands: List<SlashCommandItem>,
    onSelect: (SlashCommandItem) -> Unit,
) {
    Surface(
        color = RuntimeTheme.colorScheme.surfaceContainerLow,
        shape = RuntimeTheme.shapes.small,
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 6.dp),
    ) {
        Column(modifier = Modifier.padding(vertical = 4.dp)) {
            commands.forEach { cmd ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSelect(cmd) }
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    RuntimeIcon(cmd.icon, Modifier.size(16.dp), tint = RuntimeTheme.colorScheme.onSurface)
                    Text(
                        cmd.command,
                        style = RuntimeTheme.typography.labelMedium,
                        color = RuntimeTheme.colorScheme.primary,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Medium,
                    )
                    Text(
                        cmd.description,
                        style = RuntimeTheme.typography.bodySmall,
                        color = RuntimeTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
internal fun MentionPopup(
    mentions: List<MentionItem>,
    onSelect: (MentionItem) -> Unit,
) {
    Surface(
        color = RuntimeTheme.colorScheme.surfaceContainer,
        shape = RuntimeTheme.shapes.medium,
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 6.dp),
    ) {
        Column(modifier = Modifier.padding(vertical = 6.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    stringResource(R.string.chat_mention_picker_title),
                    style = RuntimeTheme.typography.labelSmall.copy(fontSize = 11.sp, fontWeight = FontWeight.Medium),
                    color = RuntimeTheme.colorScheme.primary,
                )
                Text(
                    stringResource(R.string.chat_mention_picker_hint),
                    style = RuntimeTheme.typography.labelSmall.copy(fontSize = 11.sp),
                    color = RuntimeTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                )
            }
            HorizontalDivider(color = RuntimeTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
            LazyColumn(
                modifier = Modifier.heightIn(max = 240.dp),
            ) {
                items(mentions.size, key = { mentions[it].id }) { index ->
                    val item = mentions[index]
                    val isSkill = item.type == MentionType.SKILL
                    val tagBg = if (isSkill) RuntimeTheme.colorScheme.primaryContainer.copy(alpha = 0.65f)
                                else RuntimeTheme.colorScheme.tertiaryContainer.copy(alpha = 0.65f)
                    val tagBorder = if (isSkill) RuntimeTheme.colorScheme.primary.copy(alpha = 0.4f)
                                    else RuntimeTheme.colorScheme.tertiary.copy(alpha = 0.4f)
                    val tagColor = if (isSkill) RuntimeTheme.colorScheme.primary
                                   else RuntimeTheme.colorScheme.tertiary

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(item) }
                            .padding(horizontal = 12.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        // 左侧图标芯片
                        Surface(
                            shape = RuntimeTheme.shapes.chip,
                            color = tagBg,
                            border = androidx.compose.foundation.BorderStroke(1.dp, tagBorder),
                            modifier = Modifier.size(34.dp),
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                RuntimeIcon(
                                    item.icon,
                                    Modifier.size(17.dp),
                                    tint = tagColor,
                                )
                            }
                        }

                        // 中间文案与描述
                        Column(modifier = Modifier.weight(1f)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                Text(
                                    "@${item.name}",
                                    style = RuntimeTheme.typography.labelMedium.copy(fontWeight = FontWeight.Medium),
                                    color = RuntimeTheme.colorScheme.onSurface,
                                    modifier = Modifier.weight(1f, fill = false), maxLines = 1, overflow = TextOverflow.Ellipsis,
                                )
                                Surface(
                                    color = tagBg,
                                    border = androidx.compose.foundation.BorderStroke(0.8.dp, tagBorder),
                                    shape = RuntimeTheme.shapes.tiny,
                                ) {
                                    Text(
                                        stringResource(when (item.type) {
                                            MentionType.SKILL -> R.string.chat_skill_badge
                                            MentionType.MCP_SERVER -> R.string.chat_mcp_badge
                                            MentionType.FILE -> R.string.chat_file_badge
                                            MentionType.DIRECTORY -> R.string.chat_directory_badge
                                        }),
                                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp),
                                        style = RuntimeTheme.typography.labelSmall.copy(fontSize = 11.sp, fontWeight = FontWeight.Medium),
                                        color = tagColor,
                                    )
                                }
                            }
                            Text(
                                item.description,
                                style = RuntimeTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                color = RuntimeTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        }
    }
}

