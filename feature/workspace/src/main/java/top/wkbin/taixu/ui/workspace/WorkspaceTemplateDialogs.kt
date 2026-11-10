package top.wkbin.taixu.ui.workspace

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import top.wkbin.taixu.feature.workspace.R
import top.wkbin.taixu.template.InstalledProjectTemplate
import top.wkbin.taixu.ui.components.RuntimeActionRow
import top.wkbin.taixu.ui.components.RuntimeAlertDialog
import top.wkbin.taixu.ui.components.RuntimeButton
import top.wkbin.taixu.ui.components.RuntimeCard
import top.wkbin.taixu.ui.components.RuntimeIcon
import top.wkbin.taixu.ui.components.RuntimeIconButton as IconButton
import top.wkbin.taixu.ui.components.RuntimeIconName
import top.wkbin.taixu.ui.components.RuntimeOutlinedButton
import top.wkbin.taixu.ui.components.RuntimeTextButton as TextButton
import top.wkbin.taixu.ui.miuix.RuntimeTheme
import top.wkbin.taixu.ui.miuix.Surface
import top.yukonga.miuix.kmp.basic.Text

/** 项目模板管理弹窗：导入 / 导出 / 删除 / 规格说明。 */
@Composable
internal fun TemplateManagerDialog(
    templates: List<InstalledProjectTemplate>,
    busy: Boolean,
    onDismiss: () -> Unit,
    onImport: () -> Unit,
    onExport: (InstalledProjectTemplate) -> Unit,
    onDelete: (InstalledProjectTemplate) -> Unit,
    onShowSpec: () -> Unit,
) {
    RuntimeAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.workspace_menu_templates), fontWeight = FontWeight.Medium) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth().heightIn(max = 560.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                RuntimeActionRow() {
                    RuntimeButton(onClick = onImport, enabled = !busy, modifier = Modifier) {
                        Text(stringResource(R.string.workspace_template_import_zip))
                    }
                    RuntimeOutlinedButton(onClick = onShowSpec, modifier = Modifier) {
                        Text(stringResource(R.string.workspace_template_spec))
                    }
                }
                templates.groupBy { it.manifest.projectType }.forEach { (type, typeTemplates) ->
                    Text(
                        type.name.lowercase().replaceFirstChar { it.uppercase() },
                        style = RuntimeTheme.typography.labelLarge,
                        color = RuntimeTheme.colorScheme.primary,
                    )
                    typeTemplates.forEach { template ->
                        RuntimeCard(containerColor = RuntimeTheme.colorScheme.surfaceContainer) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Surface(
                                    modifier = Modifier.size(56.dp),
                                    shape = RuntimeTheme.shapes.chip,
                                    color = RuntimeTheme.colorScheme.surfaceContainerHighest,
                                ) {
                                    template.previewFile?.let { TemplatePreviewImage(it, Modifier.fillMaxSize()) }
                                }
                                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Text(template.manifest.name, fontWeight = FontWeight.Medium, maxLines = 1)
                                    Text(
                                        "${template.manifest.category.name} · ${if (template.isBundled) stringResource(R.string.workspace_template_bundled) else stringResource(R.string.workspace_template_user)}",
                                        style = RuntimeTheme.typography.labelSmall,
                                        color = RuntimeTheme.colorScheme.onSurfaceVariant,
                                    )
                                    Text(
                                        template.manifest.id,
                                        style = RuntimeTheme.typography.labelSmall,
                                        fontFamily = FontFamily.Monospace,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                                TextButton(onClick = { onExport(template) }, enabled = !busy) { Text(stringResource(R.string.workspace_action_export)) }
                                if (!template.isBundled) {
                                    IconButton(
                                        onClick = { onDelete(template) },
                                        enabled = !busy,
                                        contentDescription = stringResource(R.string.workspace_cd_delete_template),
                                    ) {
                                        RuntimeIcon(RuntimeIconName.Trash, Modifier.size(18.dp), RuntimeTheme.colorScheme.error)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.workspace_action_done)) } },
    )
}

/** 项目模板规格说明弹窗（manifest 结构 / 变量 / 预览 / hooks / 校验）。 */
@Composable
internal fun ProjectTemplateSpecDialog(onDismiss: () -> Unit) {
    RuntimeAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.workspace_spec_title), fontWeight = FontWeight.Medium) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth().heightIn(max = 560.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(stringResource(R.string.workspace_spec_intro))
                Text(stringResource(R.string.workspace_spec_min_structure), fontWeight = FontWeight.Medium)
                Text(
                    stringResource(R.string.workspace_spec_min_structure_body),
                    fontFamily = FontFamily.Monospace,
                    style = RuntimeTheme.typography.bodySmall,
                )
                Text(stringResource(R.string.workspace_spec_manifest), fontWeight = FontWeight.Medium)
                Text(stringResource(R.string.workspace_spec_manifest_body), style = RuntimeTheme.typography.bodySmall)
                Text(stringResource(R.string.workspace_spec_variables), fontWeight = FontWeight.Medium)
                Text(stringResource(R.string.workspace_spec_variables_body), style = RuntimeTheme.typography.bodySmall)
                Text(stringResource(R.string.workspace_spec_preview), fontWeight = FontWeight.Medium)
                Text(stringResource(R.string.workspace_spec_preview_body), style = RuntimeTheme.typography.bodySmall)
                Text(stringResource(R.string.workspace_spec_hooks), fontWeight = FontWeight.Medium)
                Text(stringResource(R.string.workspace_spec_hooks_body), style = RuntimeTheme.typography.bodySmall)
                Text(stringResource(R.string.workspace_spec_validation), fontWeight = FontWeight.Medium)
                Text(stringResource(R.string.workspace_spec_validation_body), style = RuntimeTheme.typography.bodySmall)
                Text(stringResource(R.string.workspace_spec_footer), color = RuntimeTheme.colorScheme.primary)
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.workspace_spec_got_it)) } },
    )
}
