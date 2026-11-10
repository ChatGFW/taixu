package top.wkbin.taixu.ui.settings

import androidx.compose.foundation.layout.*
import top.wkbin.taixu.ui.miuix.*
import top.yukonga.miuix.kmp.basic.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import top.wkbin.taixu.core.database.AiModelEntity
import top.wkbin.taixu.harness.ModelContextWindows
import top.wkbin.taixu.ui.components.*
import top.yukonga.miuix.kmp.basic.DropdownEntry
import top.yukonga.miuix.kmp.basic.DropdownItem

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
    RuntimeCard(modifier = Modifier.fillMaxWidth(), onClick = onEdit,
        containerColor = if (model.isActive) RuntimeTheme.colorScheme.secondaryContainer else RuntimeTheme.colorScheme.surfaceContainerLow,
        shape = RuntimeTheme.shapes.extraLarge, contentPadding = PaddingValues(16.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                ModelBadge(modelId = model.model, providerIdOrName = model.provider, size = 40.dp)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(model.name.ifBlank { model.provider }, style = RuntimeTheme.typography.titleMedium,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(model.provider, style = RuntimeTheme.typography.bodyMedium,
                        color = RuntimeTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
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
                        RuntimeIcon(RuntimeIconName.More)
                }
            }
            if (modelIds.isNotEmpty()) {
                Text(modelIds.take(3).joinToString("\n") + if (modelIds.size > 3) "\n另有 ${modelIds.size - 3} 个模型" else "",
                    style = RuntimeTheme.typography.bodyMedium, maxLines = 4, overflow = TextOverflow.Ellipsis)
            }
            val tokens = model.contextTokens ?: ModelContextWindows.resolve(model.model, model.provider)
            val details = buildList {
                tokens?.let { add("${formatModelContext(it)} 上下文") }
                if (model.apiKeyCount > 0) add("${model.apiKeyCount} 个 Key")
                if (model.requestsPerMinutePerKey > 0) add("${model.requestsPerMinutePerKey} RPM/Key")
            }.joinToString(" · ")
            if (details.isNotEmpty()) Text(details, style = RuntimeTheme.typography.bodyMedium,
                color = RuntimeTheme.colorScheme.onSurfaceVariant)
            if (model.baseUrl.isNotBlank()) Text(model.baseUrl, style = RuntimeTheme.typography.bodySmall,
                color = RuntimeTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (model.isActive) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    RuntimeIcon(RuntimeIconName.Check, Modifier.size(20.dp), RuntimeTheme.colorScheme.onSurface)
                    Text("全局默认", style = RuntimeTheme.typography.labelLarge, color = RuntimeTheme.colorScheme.primary)
                }
            }
            RuntimeActionRow {
                if (!model.isActive) RuntimeFilledTonalButton(onActivate) { Text("设为默认") }
                RuntimeTextButton(onEdit) { Text("编辑") }
            }
        }
    }
}
