package top.wkbin.taixu.ui.workspace

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import top.wkbin.taixu.feature.workspace.R
import top.wkbin.taixu.runtime.ProjectType
import top.wkbin.taixu.runtime.WorkspaceProject
import top.wkbin.taixu.ui.components.IconTile
import top.wkbin.taixu.ui.components.RuntimeActionRow
import top.wkbin.taixu.ui.components.RuntimeCard
import top.wkbin.taixu.ui.components.RuntimeCircularProgressIndicator
import top.wkbin.taixu.ui.components.RuntimeFilledTonalButton as FilledTonalButton
import top.wkbin.taixu.ui.components.RuntimeIcon
import top.wkbin.taixu.ui.components.RuntimeIconMenu
import top.wkbin.taixu.ui.components.RuntimeIconName
import top.wkbin.taixu.ui.components.RuntimeLinearProgressIndicator as LinearProgressIndicator
import top.wkbin.taixu.ui.components.RuntimeTextButton as TextButton
import top.wkbin.taixu.ui.miuix.RuntimeTheme
import top.wkbin.taixu.ui.miuix.Surface
import top.wkbin.taixu.ui.miuix.minimumInteractiveComponentSize
import top.yukonga.miuix.kmp.basic.DropdownEntry
import top.yukonga.miuix.kmp.basic.DropdownItem
import top.yukonga.miuix.kmp.basic.Text

/** 工坊项目卡片：类型徽章 + 路径/体积 + 快捷操作栏。 */
@Composable
internal fun ProjectCard(
    project: WorkspaceProject,
    busy: Boolean,
    isBuilding: Boolean,
    onOpenExplorer: () -> Unit,
    onOpenTerminal: () -> Unit,
    onOpenAgent: () -> Unit,
    onOpenWorkflows: () -> Unit,
    onRunProject: () -> Unit,
    onShowBuildLog: () -> Unit,
    onExport: () -> Unit,
    onDelete: () -> Unit,
) {
    // 颜色收敛：项目类型标识改用主题色轮替，不再引入主题外硬编码色
    val typeBadgeColor = when (project.projectType) {
        ProjectType.ANDROID -> RuntimeTheme.colorScheme.primary
        ProjectType.FLUTTER -> RuntimeTheme.colorScheme.secondary
        ProjectType.REVERSE -> RuntimeTheme.colorScheme.tertiary
        ProjectType.GENERAL, ProjectType.TAIXU_PLUGIN -> RuntimeTheme.colorScheme.primary
    }

    val typeIcon = when (project.projectType) {
        ProjectType.ANDROID -> RuntimeIconName.Android
        ProjectType.FLUTTER -> RuntimeIconName.Flutter
        ProjectType.REVERSE -> RuntimeIconName.Reverse
        ProjectType.GENERAL, ProjectType.TAIXU_PLUGIN -> RuntimeIconName.Code
    }

    RuntimeCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RuntimeTheme.shapes.extraLarge,
        containerColor = RuntimeTheme.colorScheme.surfaceContainer,
        contentPadding = PaddingValues(16.dp),
        // 减法：常态无边框，仅构建中保留主色描边作为状态提示
        borderColor = if (isBuilding) RuntimeTheme.colorScheme.primary else Color.Transparent,
        onClick = onOpenExplorer,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                IconTile(typeIcon, size = 40.dp, color = typeBadgeColor)
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(
                            text = project.name,
                            style = RuntimeTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Surface(
                            color = if (isBuilding) RuntimeTheme.colorScheme.primaryContainer else typeBadgeColor.copy(alpha = 0.15f),
                            shape = RuntimeTheme.shapes.tiny,
                        ) {
                            Text(
                                text = if (isBuilding) stringResource(R.string.workspace_building_badge) else project.projectType.displayName,
                                style = RuntimeTheme.typography.labelSmall.copy(fontSize = 11.sp, fontWeight = FontWeight.Medium),
                                color = if (isBuilding) RuntimeTheme.colorScheme.primary else typeBadgeColor,
                                maxLines = 1,
                                softWrap = false,
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp),
                            )
                        }
                    }
                    Text(
                        text = "${project.linuxPath} · ${project.sizeBytes.toReadableSize()}",
                        style = RuntimeTheme.typography.bodySmall.copy(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp,
                        ),
                        color = RuntimeTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                RuntimeIconMenu(
                    entries = listOf(
                        DropdownEntry(
                            items = listOf(
                                DropdownItem(
                                    text = stringResource(R.string.workspace_open_terminal),
                                    icon = { mod -> RuntimeIcon(RuntimeIconName.Terminal, mod.size(17.dp)) },
                                    onClick = onOpenTerminal,
                                ),
                                DropdownItem(
                                    text = "运行工作流",
                                    icon = { mod -> RuntimeIcon(RuntimeIconName.Hub, mod.size(17.dp)) },
                                    onClick = onOpenWorkflows,
                                ),
                                DropdownItem(
                                    text = stringResource(R.string.workspace_export_project),
                                    icon = { mod -> RuntimeIcon(RuntimeIconName.Compress, mod.size(17.dp)) },
                                    onClick = onExport,
                                ),
                                DropdownItem(
                                    text = stringResource(R.string.workspace_delete_project),
                                    icon = { mod -> RuntimeIcon(RuntimeIconName.Trash, mod.size(17.dp), RuntimeTheme.colorScheme.error) },
                                    onClick = onDelete,
                                ),
                            ),
                        ),
                    ),
                    enabled = !busy,
                    modifier = Modifier
                        .size(48.dp)
                        .minimumInteractiveComponentSize(),
                ) {
                    RuntimeIcon(
                        RuntimeIconName.More,
                        Modifier.size(18.dp),
                        RuntimeTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            if (isBuilding) {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth().height(3.dp).clip(RuntimeTheme.shapes.hairline),
                    color = RuntimeTheme.colorScheme.primary,
                )
            }

            // 快捷操作栏：精简去噪，只保留最核心的直达动作
            RuntimeActionRow(modifier = Modifier.fillMaxWidth()) {
                // 插件工程直达打包与导入，Android / Flutter 仍进入构建配置。
                if (project.projectType == ProjectType.ANDROID ||
                    project.projectType == ProjectType.FLUTTER || project.projectType == ProjectType.TAIXU_PLUGIN
                ) {
                    FilledTonalButton(
                        onClick = if (isBuilding) onShowBuildLog else onRunProject,
                        enabled = !busy || isBuilding,
                        shape = CircleShape,
                        modifier = Modifier.padding(end = 8.dp),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            if (isBuilding) {
                                RuntimeCircularProgressIndicator(
                                    modifier = Modifier.size(13.dp),
                                    strokeWidth = 2.dp,
                                )
                                Text(
                                    stringResource(R.string.workspace_compiling),
                                    maxLines = 1,
                                    softWrap = false,
                                )
                            } else {
                                RuntimeIcon(RuntimeIconName.Play, Modifier.size(14.dp), tint = typeBadgeColor)
                                Text(
                                    stringResource(if (project.projectType == ProjectType.TAIXU_PLUGIN)
                                        R.string.workspace_plugin_debug_import else R.string.workspace_run_on_device),
                                    color = typeBadgeColor,
                                    maxLines = 1,
                                    softWrap = false,
                                )
                            }
                        }
                    }
                } else {
                    TextButton(
                        onClick = onOpenTerminal,
                        enabled = !busy,
                        modifier = Modifier.padding(end = 4.dp),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            RuntimeIcon(RuntimeIconName.Terminal, Modifier.size(15.dp), RuntimeTheme.colorScheme.onSurface)
                            Text(
                                stringResource(R.string.workspace_terminal),
                                maxLines = 1,
                                softWrap = false,
                            )
                        }
                    }
                }

            }
        }
    }
}

private fun Long.toReadableSize(): String = when {
    this < 1024 -> "$this B"
    this < 1024 * 1024 -> "%.1f KB".format(this / 1024.0)
    this < 1024 * 1024 * 1024 -> "%.1f MB".format(this / (1024.0 * 1024))
    else -> "%.1f GB".format(this / (1024.0 * 1024 * 1024))
}
