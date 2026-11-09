package top.wkbin.taixu.ui.settings

import androidx.compose.foundation.background
import top.wkbin.taixu.ui.theme.semanticColors
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import top.wkbin.taixu.ui.miuix.RuntimeTheme
import top.wkbin.taixu.ui.miuix.Surface
import top.wkbin.taixu.ui.settings.LocalizedText as Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import top.wkbin.taixu.core.common.translation.TranslationModelStatus
import top.wkbin.taixu.ui.components.RuntimeButton as Button
import top.wkbin.taixu.ui.components.RuntimeCircularProgressIndicator
import top.wkbin.taixu.ui.components.RuntimeIcon
import top.wkbin.taixu.ui.components.RuntimeIconName
import top.wkbin.taixu.ui.components.RuntimeLinearProgressIndicator
import top.wkbin.taixu.ui.components.RuntimeOutlinedButton as OutlinedButton

/**
 * 离线语种模型状态与下载卡片（英语 -> 简体中文）。
 */
@Composable
fun TranslationModelCard(
    status: TranslationModelStatus,
    onDownload: () -> Unit,
    onDelete: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RuntimeTheme.shapes.small)
                    .background(RuntimeTheme.colorScheme.primary.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center,
            ) {
                RuntimeIcon(
                    name = RuntimeIconName.Globe,
                    modifier = Modifier.size(18.dp),
                    tint = RuntimeTheme.colorScheme.primary,
                )
            }
            Column(Modifier.weight(1f)) {
                Text(
                    text = "英语 (en) → 简体中文 (zh-CN)",
                    style = RuntimeTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                    color = RuntimeTheme.colorScheme.onSurface,
                )
                Text(
                    text = "Google ML Kit 离线翻译包 (约 60MB)",
                    style = RuntimeTheme.typography.bodySmall,
                    color = RuntimeTheme.colorScheme.onSurfaceVariant,
                )
            }
            when (status) {
                is TranslationModelStatus.Ready -> {
                    Surface(
                        shape = RuntimeTheme.shapes.chip,
                        color = semanticColors().success.copy(alpha = 0.15f),
                    ) {
                        Text(
                            text = "已就绪 (离线)",
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                            style = RuntimeTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                            color = semanticColors().success,
                        )
                    }
                }
                is TranslationModelStatus.Downloading -> {
                    val percent = ((status.progress ?: 0f) * 100).toInt().coerceIn(0, 100)
                    Surface(
                        shape = RuntimeTheme.shapes.chip,
                        color = RuntimeTheme.colorScheme.primary.copy(alpha = 0.12f),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            RuntimeCircularProgressIndicator(
                                modifier = Modifier.size(11.dp),
                                strokeWidth = 1.5.dp,
                            )
                            Text(
                                text = "下载中 $percent%",
                                style = RuntimeTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                                color = RuntimeTheme.colorScheme.primary,
                            )
                        }
                    }
                }
                is TranslationModelStatus.Checking -> {
                    Text(
                        text = "检测中...",
                        style = RuntimeTheme.typography.labelSmall,
                        color = RuntimeTheme.colorScheme.onSurfaceVariant,
                    )
                }
                is TranslationModelStatus.Error -> {
                    Surface(
                        shape = RuntimeTheme.shapes.chip,
                        color = RuntimeTheme.colorScheme.error.copy(alpha = 0.15f),
                    ) {
                        Text(
                            text = "下载失败",
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                            style = RuntimeTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                            color = RuntimeTheme.colorScheme.error,
                        )
                    }
                }
                is TranslationModelStatus.NotDownloaded -> {
                    Surface(
                        shape = RuntimeTheme.shapes.chip,
                        color = RuntimeTheme.colorScheme.surfaceContainerHigh,
                    ) {
                        Text(
                            text = "未下载",
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                            style = RuntimeTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                            color = RuntimeTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        if (status is TranslationModelStatus.Downloading) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RuntimeTheme.shapes.small)
                    .background(RuntimeTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.55f))
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = status.stepName,
                        style = RuntimeTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                        color = RuntimeTheme.colorScheme.primary,
                    )
                    Text(
                        text = status.detailText,
                        style = RuntimeTheme.typography.labelSmall,
                        color = RuntimeTheme.colorScheme.onSurfaceVariant,
                    )
                }

                val progressFraction = status.progress
                RuntimeLinearProgressIndicator(
                    progress = if (progressFraction != null) { { progressFraction } } else null,
                    modifier = Modifier.fillMaxWidth(),
                )

                Text(
                    text = "首次配置需从 Google 存储库拉取离线语言包。下载完成后留存本机，不消耗流量即可离线翻译。",
                    style = RuntimeTheme.typography.bodySmall.copy(fontSize = 11.sp),
                    color = RuntimeTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                )
            }
        } else if (status is TranslationModelStatus.Error) {
            Text(
                text = "${status.message}（若网络连接受限，可尝试检查网络或开启代理后重试）",
                style = RuntimeTheme.typography.bodySmall,
                color = RuntimeTheme.colorScheme.error,
            )
        } else {
            Text(
                text = "完全在手机端侧运行，不依赖云端，思考流离线秒级转译为中文，保护隐私且不消耗额外 Token。",
                style = RuntimeTheme.typography.bodySmall,
                color = RuntimeTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
            )
        }

        top.wkbin.taixu.ui.components.RuntimeActionRow(modifier = Modifier.fillMaxWidth()) {
            when (status) {
                is TranslationModelStatus.Ready -> {
                    OutlinedButton(
                        onClick = onDelete,
                    ) {
                        RuntimeIcon(RuntimeIconName.Trash, Modifier.size(14.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("删除模型释放空间")
                    }
                }
                is TranslationModelStatus.Downloading -> {
                    val percent = ((status.progress ?: 0f) * 100).toInt().coerceIn(0, 100)
                    Button(
                        onClick = {},
                        enabled = false,
                    ) {
                        RuntimeCircularProgressIndicator(
                            modifier = Modifier.size(12.dp),
                            strokeWidth = 1.5.dp,
                            color = RuntimeTheme.colorScheme.onPrimary,
                        )
                        Spacer(Modifier.width(6.dp))
                        Text("正在下载 ($percent%)")
                    }
                }
                is TranslationModelStatus.Error -> {
                    Button(
                        onClick = onRetry,
                    ) {
                        RuntimeIcon(RuntimeIconName.Refresh, Modifier.size(14.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("重试下载 (~60MB)")
                    }
                }
                else -> {
                    Button(
                        onClick = onDownload,
                    ) {
                        RuntimeIcon(RuntimeIconName.Download, Modifier.size(14.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("下载离线模型 (~60MB)")
                    }
                }
            }
        }
    }
}
