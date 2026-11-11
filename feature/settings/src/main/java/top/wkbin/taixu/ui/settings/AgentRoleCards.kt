package top.wkbin.taixu.ui.settings

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import top.wkbin.taixu.core.model.AgentDepartments
import top.wkbin.taixu.core.model.AgentSubagent
import top.wkbin.taixu.ui.components.RuntimeCard
import top.wkbin.taixu.ui.components.RuntimeExpandChevron
import top.wkbin.taixu.ui.components.RuntimeIcon
import top.wkbin.taixu.ui.components.RuntimeIconButton
import top.wkbin.taixu.ui.components.RuntimeIconName
import top.wkbin.taixu.ui.components.RuntimeSwitch
import top.wkbin.taixu.ui.components.StatusBadge
import top.wkbin.taixu.ui.miuix.RuntimeSpacing
import top.wkbin.taixu.ui.miuix.RuntimeTheme
import top.wkbin.taixu.ui.settings.LocalizedText as Text
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
internal fun AgentDepartmentCard(
    departmentId: String,
    enabledCount: Int,
    agentCount: Int,
    expanded: Boolean,
    onClick: () -> Unit,
) {
    val department = AgentDepartments.find(departmentId)
    RuntimeCard(
        modifier = Modifier.fillMaxWidth(),
        containerColor = RuntimeTheme.colorScheme.surfaceContainer,
        shape = RuntimeTheme.shapes.medium,
        contentPadding = PaddingValues(0.dp),
    ) {
        BasicComponent(
            startAction = {
                RuntimeIcon(
                    RuntimeIconName.Bot,
                    Modifier.padding(end = RuntimeSpacing.rowIconEnd).size(RuntimeSpacing.rowIconSize),
                    RuntimeTheme.colorScheme.onSurface,
                )
            },
            endActions = {
                RuntimeExpandChevron(
                    expanded = expanded,
                    modifier = Modifier.size(18.dp),
                    tint = MiuixTheme.colorScheme.onSurfaceVariantActions,
                    vertical = true,
                )
            },
            onClick = onClick,
        ) {
            Text(
                "${department.localizedName} · ${department.name}",
                fontSize = MiuixTheme.textStyles.headline1.fontSize,
                fontWeight = FontWeight.Medium,
                color = MiuixTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                "$enabledCount/$agentCount 已启用",
                fontSize = MiuixTheme.textStyles.body2.fontSize,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )
        }
    }
}

@Composable
internal fun SubagentProfileCard(
    profile: AgentSubagent,
    modelLabel: String,
    onToggle: (Boolean) -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    RuntimeCard(
        modifier = Modifier.fillMaxWidth(),
        containerColor = RuntimeTheme.colorScheme.surfaceContainer,
        shape = RuntimeTheme.shapes.medium,
        contentPadding = PaddingValues(0.dp),
    ) {
        BasicComponent(
            startAction = {
                RuntimeIcon(
                    RuntimeIconName.Bot,
                    Modifier.padding(end = RuntimeSpacing.rowIconEnd).size(RuntimeSpacing.rowIconSize),
                    MiuixTheme.colorScheme.onSurface,
                )
            },
            endActions = {
                RuntimeIconButton(onClick = onEdit, contentDescription = "编辑角色") {
                    RuntimeIcon(RuntimeIconName.Edit, Modifier.size(20.dp), MiuixTheme.colorScheme.onSurfaceVariantActions)
                }
                RuntimeIconButton(onClick = onDelete, contentDescription = "删除角色") {
                    RuntimeIcon(RuntimeIconName.Trash, Modifier.size(20.dp), RuntimeTheme.colorScheme.error)
                }
                RuntimeSwitch(checked = profile.isEnabled, onCheckedChange = onToggle)
            },
            onClick = onEdit,
        ) {
            Text(
                profile.name,
                fontSize = MiuixTheme.textStyles.headline1.fontSize,
                fontWeight = FontWeight.Medium,
                color = MiuixTheme.colorScheme.onBackground,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                profile.id,
                fontSize = MiuixTheme.textStyles.body2.fontSize,
                fontFamily = FontFamily.Monospace,
                color = RuntimeTheme.colorScheme.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (profile.isBuiltin) StatusBadge("Agency")
            Text(
                profile.description,
                fontSize = MiuixTheme.textStyles.body2.fontSize,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                "默认模型：$modelLabel",
                fontSize = MiuixTheme.textStyles.footnote1.fontSize,
                color = if (profile.defaultModelId == null) {
                    MiuixTheme.colorScheme.onSurfaceVariantSummary
                } else {
                    RuntimeTheme.colorScheme.primary
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
