package top.wkbin.taixu.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import top.wkbin.taixu.core.database.AiModelEntity
import top.wkbin.taixu.harness.ModelContextWindows
import top.wkbin.taixu.ui.components.ModelBadge
import top.wkbin.taixu.ui.components.RuntimeActionRow
import top.wkbin.taixu.ui.components.RuntimeCard
import top.wkbin.taixu.ui.components.RuntimeFilledTonalButton
import top.wkbin.taixu.ui.components.RuntimeIcon
import top.wkbin.taixu.ui.components.RuntimeIconMenu
import top.wkbin.taixu.ui.components.RuntimeIconName
import top.wkbin.taixu.ui.components.RuntimeTextButton
import top.wkbin.taixu.ui.components.StatusBadge
import top.wkbin.taixu.ui.components.formatModelContext
import top.wkbin.taixu.ui.miuix.RuntimeTheme
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.DropdownEntry
import top.yukonga.miuix.kmp.basic.DropdownItem
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** 档案管理的默认状态属于全局配置，与聊天页的会话模型选择区分。 */
@Composable
internal fun ModelProfileCard(
    model: AiModelEntity,
    onEdit: () -> Unit,
    onActivate: () -> Unit,
    onDelete: () -> Unit,
    onExport: () -> Unit,
) {
    val modelIds = remember(model.model) { model.model.split(',').map { it.trim() }.filter { it.isNotEmpty() } }
    val summary = remember(model.provider, modelIds) {
        buildString {
            append(model.provider)
            if (modelIds.isNotEmpty()) {
                append(" · ")
                append(modelIds.take(2).joinToString("、"))
                if (modelIds.size > 2) append(" · 另有 ${modelIds.size - 2} 个")
            }
        }
    }
    val tokens = model.contextTokens ?: ModelContextWindows.resolve(model.model, model.provider)
    val details = buildList {
        tokens?.let { add("${formatModelContext(it)} 上下文") }
        if (model.apiKeyCount > 0) add("${model.apiKeyCount} 个 Key")
        if (model.requestsPerMinutePerKey > 0) add("${model.requestsPerMinutePerKey} RPM/Key")
    }.joinToString(" · ")
    // 选中态走 miuix：同色卡片 + 实色 primary 描边，不用 M3 secondaryContainer 填色。
    RuntimeCard(
        modifier = Modifier.fillMaxWidth(),
        containerColor = RuntimeTheme.colorScheme.surfaceContainerLow,
        borderColor = if (model.isActive) RuntimeTheme.colorScheme.primary else Color.Transparent,
        shape = RuntimeTheme.shapes.medium,
        contentPadding = PaddingValues(0.dp),
    ) {
        BasicComponent(
            startAction = {
                Box(Modifier.padding(end = 12.dp)) {
                    ModelBadge(modelId = model.model, providerIdOrName = model.provider, size = 36.dp)
                }
            },
            endActions = {
                RuntimeIconMenu(
                    entries = listOf(
                        DropdownEntry(
                            items = buildList {
                                add(DropdownItem(text = "编辑档案", icon = { mod -> RuntimeIcon(RuntimeIconName.Edit, mod) }, onClick = onEdit))
                                add(DropdownItem(text = "导出配置", icon = { mod -> RuntimeIcon(RuntimeIconName.OpenInNew, mod) }, onClick = onExport))
                                if (!model.isActive) add(DropdownItem(text = "设为全局默认", icon = { mod -> RuntimeIcon(RuntimeIconName.Check, mod) }, onClick = onActivate))
                                add(DropdownItem(text = "删除档案", icon = { mod -> RuntimeIcon(RuntimeIconName.Trash, mod, RuntimeTheme.colorScheme.error) }, onClick = onDelete))
                            },
                        ),
                    ),
                    modifier = Modifier.size(40.dp),
                ) {
                    RuntimeIcon(RuntimeIconName.More, Modifier.size(20.dp))
                }
            },
            bottomAction = {
                RuntimeActionRow {
                    if (!model.isActive) RuntimeFilledTonalButton(onActivate) { Text("设为默认") }
                    RuntimeTextButton(onEdit) { Text("编辑") }
                }
            },
            onClick = onEdit,
        ) {
            Text(
                model.name.ifBlank { model.provider },
                fontSize = MiuixTheme.textStyles.headline1.fontSize,
                fontWeight = FontWeight.Medium,
                color = MiuixTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (summary.isNotBlank()) {
                Text(
                    summary,
                    fontSize = MiuixTheme.textStyles.body2.fontSize,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (model.isActive || details.isNotEmpty()) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    itemVerticalAlignment = Alignment.CenterVertically,
                ) {
                    if (model.isActive) StatusBadge("全局默认")
                    if (details.isNotEmpty()) {
                        Text(
                            details,
                            style = MiuixTheme.textStyles.footnote1,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
            if (model.baseUrl.isNotBlank()) {
                Text(
                    model.baseUrl,
                    style = MiuixTheme.textStyles.footnote1,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
