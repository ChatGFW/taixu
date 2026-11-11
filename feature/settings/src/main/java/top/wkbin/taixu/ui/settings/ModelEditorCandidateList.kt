package top.wkbin.taixu.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import top.wkbin.taixu.harness.model.ModelMetadataRegistry
import top.wkbin.taixu.ui.components.AutoModelIcon
import top.wkbin.taixu.ui.components.ModelCapabilityTags
import top.wkbin.taixu.ui.components.ModelCapabilityUi
import top.wkbin.taixu.ui.components.RuntimeFilledTextField
import top.wkbin.taixu.ui.components.RuntimeSearchBar
import top.wkbin.taixu.ui.components.RuntimeSectionTitle
import top.wkbin.taixu.ui.components.RuntimeSelectionRow
import top.wkbin.taixu.ui.miuix.RuntimeSpacing
import top.wkbin.taixu.ui.miuix.RuntimeTheme
import top.yukonga.miuix.kmp.basic.Text

/**
 * 候选模型跟页面同一条滚动；搜索与手动 ID 不包 Card，符合表单区约定。
 */
internal fun LazyListScope.modelSelectionItems(
    selectedModels: Set<String>,
    customModelInput: String,
    modelSearchQuery: String,
    candidateModels: List<String>,
    filteredCandidateModels: List<String>,
    onSearchQueryChange: (String) -> Unit,
    onCustomModelInputChange: (String) -> Unit,
    onToggle: (String) -> Unit,
) {
    val totalSelectedCount = (selectedModels + customModelInput.split(",").map { it.trim() })
        .filter { it.isNotBlank() }.distinct().size
    val title = if (totalSelectedCount > 0) "模型选择与定制 · 已选 $totalSelectedCount 个" else "模型选择与定制"
    item(key = "model-selection-header") {
        Column(verticalArrangement = Arrangement.spacedBy(RuntimeSpacing.cardGap)) {
            RuntimeSectionTitle(title)
            if (candidateModels.isNotEmpty()) {
                RuntimeSearchBar(
                    query = modelSearchQuery,
                    onQueryChange = onSearchQueryChange,
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = "搜索模型名称或 ID",
                )
                if (modelSearchQuery.isNotBlank()) {
                    Text(
                        "找到 ${filteredCandidateModels.size} / ${candidateModels.size} 个模型",
                        style = RuntimeTheme.typography.labelSmall,
                        color = RuntimeTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (filteredCandidateModels.isEmpty()) {
                    Text(
                        "没有匹配的模型，可清空搜索或在下方手动填写模型 ID",
                        style = RuntimeTheme.typography.bodySmall,
                        color = RuntimeTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            RuntimeFilledTextField(
                value = customModelInput,
                onValueChange = onCustomModelInputChange,
                modifier = Modifier.fillMaxWidth(),
                labelText = "模型 ID（可选，逗号分隔额外模型）",
                placeholder = { Text("例如：gpt-4.5-preview, claude-3-7-sonnet") },
                singleLine = true,
            )
        }
    }
    if (candidateModels.isNotEmpty()) {
        items(filteredCandidateModels, key = { "candidate:$it" }) { model ->
            RuntimeSelectionRow(
                headline = model,
                selected = model in selectedModels,
                multiple = true,
                onClick = { onToggle(model) },
                leadingContent = { AutoModelIcon(model, size = 32.dp) },
                supportingContent = {
                    val capability = remember(model) { ModelMetadataRegistry.capabilityOf(model) }
                    if (capability.known) {
                        ModelCapabilityTags(
                            ModelCapabilityUi(
                                capability.visionInput,
                                capability.tool,
                                capability.reasoning,
                                capability.contextTokens,
                            ),
                        )
                    }
                },
            )
        }
    }
}
