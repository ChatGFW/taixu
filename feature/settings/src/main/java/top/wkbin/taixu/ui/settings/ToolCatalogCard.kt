package top.wkbin.taixu.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import top.wkbin.taixu.core.database.ToolEntity
import top.wkbin.taixu.core.model.ToolState
import top.wkbin.taixu.core.tools.ToolInstallProgress
import top.wkbin.taixu.core.tools.ToolVerification
import top.wkbin.taixu.ui.components.NoticeBanner
import top.wkbin.taixu.ui.components.RuntimeActionRow
import top.wkbin.taixu.ui.components.RuntimeButton as Button
import top.wkbin.taixu.ui.components.RuntimeCard
import top.wkbin.taixu.ui.components.RuntimeFilledTonalButton as FilledTonalButton
import top.wkbin.taixu.ui.components.RuntimeIcon
import top.wkbin.taixu.ui.components.RuntimeIconName
import top.wkbin.taixu.ui.components.RuntimeLinearProgressIndicator as LinearProgressIndicator
import top.wkbin.taixu.ui.components.RuntimeTextButton as TextButton
import top.wkbin.taixu.ui.components.StatusBadge
import top.wkbin.taixu.ui.miuix.HorizontalDivider
import top.wkbin.taixu.ui.miuix.RuntimeTheme
import top.wkbin.taixu.ui.miuix.Surface
import top.wkbin.taixu.ui.settings.LocalizedText as Text

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ToolCard(
    tool: ToolEntity,
    source: String,
    progress: ToolInstallProgress?,
    verification: ToolVerification?,
    onInstall: () -> Unit,
    onUpdate: () -> Unit,
    onUninstall: () -> Unit,
    onVerify: () -> Unit,
    onLaunch: () -> Unit,
    onViewLogs: () -> Unit,
    onOpenDetail: () -> Unit,
    onStartAiHealing: () -> Unit = {},
) {
    val isInstalled = tool.state == ToolState.INSTALLED.name || tool.state == ToolState.UPDATE_AVAILABLE.name
    val isInstalling = tool.state == ToolState.INSTALLING.name
    val isUpdateAvailable = tool.state == ToolState.UPDATE_AVAILABLE.name
    val isFailed = tool.state == ToolState.FAILED.name

    RuntimeCard(
        modifier = Modifier.fillMaxWidth(),
        onClick = onOpenDetail,
        containerColor = RuntimeTheme.colorScheme.surfaceContainer,
        shape = RuntimeTheme.shapes.medium,
        contentPadding = PaddingValues(SettingsDensity.cardPadding),
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Header: Avatar + Title/Publisher + Status Badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.weight(1f),
                ) {
                    ToolBrandAvatar(
                        toolId = tool.id,
                        category = tool.category,
                        size = 46.dp,
                    )

                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Text(
                                text = tool.name,
                                style = RuntimeTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Medium,
                                    fontSize = 16.sp,
                                ),
                                color = RuntimeTheme.colorScheme.onSurface,
                                modifier = Modifier.fillMaxWidth(),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            val displayVersion = tool.installedVersion ?: tool.manifestVersion
                            if (displayVersion.isNotBlank()) {
                                Surface(
                                    color = RuntimeTheme.colorScheme.surfaceContainerHighest,
                                    shape = CircleShape,
                                ) {
                                    Text(
                                        text = "v$displayVersion",
                                        style = RuntimeTheme.typography.labelSmall.copy(fontSize = 12.sp),
                                        color = RuntimeTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp),
                                        maxLines = 1,
                                        softWrap = false, overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                            Surface(
                                color = if (source == "LOCAL") {
                                    RuntimeTheme.colorScheme.tertiaryContainer
                                } else {
                                    RuntimeTheme.colorScheme.secondaryContainer
                                },
                                shape = CircleShape,
                            ) {
                                Text(
                                    text = if (source == "LOCAL") "本地插件" else "在线插件",
                                    style = RuntimeTheme.typography.labelSmall.copy(fontSize = 12.sp),
                                    color = if (source == "LOCAL") {
                                        RuntimeTheme.colorScheme.onTertiaryContainer
                                    } else {
                                        RuntimeTheme.colorScheme.onSecondaryContainer
                                    },
                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp),
                                    maxLines = 1,
                                    softWrap = false,
                                )
                            }
                        }

                        Text(
                            text = "${tool.publisher} · ${when (tool.category) {
                                "CODING_AGENT" -> "编程智能体"
                                "AI_AGENT" -> "AI 智能体"
                                "DEVELOPER_TOOL" -> "开发加速包"
                                else -> tool.category
                            }}",
                            style = RuntimeTheme.typography.labelSmall,
                            color = RuntimeTheme.colorScheme.outline,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }

                Spacer(Modifier.width(8.dp))

                // Status Badge
                StatusBadge(
                    text = when {
                        isInstalling -> "安装中"
                        isUpdateAvailable -> "可更新"
                        isInstalled -> "已就绪"
                        isFailed -> "安装失败"
                        else -> "未安装"
                    },
                    color = when {
                        isUpdateAvailable -> RuntimeTheme.colorScheme.primary
                        isInstalled -> successStatusColor()
                        isInstalling -> RuntimeTheme.colorScheme.tertiary
                        isFailed -> RuntimeTheme.colorScheme.error
                        else -> RuntimeTheme.colorScheme.outline
                    },
                )
            }

            // Description with comfortable reading line height
            Text(
                text = tool.description,
                style = settingsSupportingStyle(),
                color = RuntimeTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )

            // Installing Progress Bar
            if (isInstalling && progress != null) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = progress.message,
                            style = RuntimeTheme.typography.labelSmall,
                            color = RuntimeTheme.colorScheme.primary,
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        progress.progress?.let { p ->
                            Text(
                                text = "${(p * 100).toInt()}%",
                                style = RuntimeTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                                color = RuntimeTheme.colorScheme.primary,
                                modifier = Modifier.padding(start = 8.dp),
                                maxLines = 1,
                                softWrap = false,
                            )
                        }
                    }
                    LinearProgressIndicator(
                        progress = { progress.progress ?: 0.5f },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RuntimeTheme.shapes.tiny),
                        color = RuntimeTheme.colorScheme.primary,
                    )
                }
            }

            // Verification info (if any)
            if (verification != null && !verification.healthy) {
                NoticeBanner(text = "自检异常: ${verification.detail}", isError = true)
            }


            // 整卡可点进详情，操作区只留日志 / 卸载 / 安装类动作。
            RuntimeActionRow(Modifier.fillMaxWidth()) {
                TextButton(onClick = onViewLogs) {
                    Text("日志", maxLines = 1, softWrap = false)
                }
                if (isInstalled) {
                    TextButton(onClick = onUninstall) {
                        Text("卸载", color = RuntimeTheme.colorScheme.error, maxLines = 1, softWrap = false)
                    }
                }
                if (isUpdateAvailable) {
                    Button(onClick = onUpdate, shape = CircleShape) {
                        Text("更新", fontWeight = FontWeight.Medium, maxLines = 1, softWrap = false)
                    }
                } else if (isFailed) {
                    Button(onClick = onStartAiHealing, shape = CircleShape) {
                        RuntimeIcon(RuntimeIconName.Brain, Modifier.size(14.dp))
                        Spacer(Modifier.width(3.dp))
                        Text("AI 自愈", fontWeight = FontWeight.Medium, maxLines = 1, softWrap = false)
                    }
                    FilledTonalButton(onClick = onInstall, shape = CircleShape) {
                        RuntimeIcon(RuntimeIconName.Download, Modifier.size(13.dp))
                        Spacer(Modifier.width(3.dp))
                        Text("重试", fontWeight = FontWeight.Medium, maxLines = 1, softWrap = false)
                    }
                } else if (!isInstalled && !isInstalling) {
                    Button(onClick = onInstall, shape = CircleShape) {
                        RuntimeIcon(RuntimeIconName.Download, Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("安装", fontWeight = FontWeight.Medium, maxLines = 1, softWrap = false)
                    }
                }
            }
        }
    }
}
