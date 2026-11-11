package top.wkbin.taixu.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.unit.dp
import top.wkbin.taixu.core.database.AiModelEntity
import top.wkbin.taixu.ui.components.EmptyPanel
import top.wkbin.taixu.ui.components.RuntimeFilterChip
import top.wkbin.taixu.ui.components.RuntimeIconName
import top.wkbin.taixu.ui.components.RuntimeSearchBar
import top.wkbin.taixu.ui.components.RuntimeSectionTitle
import top.wkbin.taixu.ui.components.runtimeContentPadding
import top.wkbin.taixu.ui.components.runtimeListScrollEffects
import top.wkbin.taixu.ui.components.runtimeScrollContentPadding
import top.wkbin.taixu.ui.miuix.RuntimeSpacing
import top.wkbin.taixu.ui.miuix.RuntimeTheme
import top.yukonga.miuix.kmp.basic.Text

@Composable
internal fun ModelProfilesContent(
    modifier: Modifier,
    scaffoldPadding: PaddingValues = PaddingValues(),
    topBarScroll: NestedScrollConnection? = null,
    models: List<AiModelEntity>,
    onEdit: (AiModelEntity) -> Unit,
    onActivate: (String) -> Unit,
    onDelete: (AiModelEntity) -> Unit,
    onExportSingle: (AiModelEntity) -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    var defaultOnly by rememberSaveable { mutableStateOf(false) }
    val filtered = remember(models, query, defaultOnly) { filterModelProfiles(models, query, defaultOnly) }
    LazyColumn(
        modifier.fillMaxSize().imePadding().runtimeListScrollEffects(topBarScroll),
        contentPadding = runtimeScrollContentPadding(
            scaffoldPadding,
            runtimeContentPadding(top = RuntimeSpacing.pageTop, bottom = RuntimeSpacing.pageBottom),
        ),
        verticalArrangement = Arrangement.spacedBy(RuntimeSpacing.cardGap),
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(RuntimeSpacing.cardGap)) {
                RuntimeSectionTitle("${models.size} 个模型档案")
                RuntimeSearchBar(query, { query = it }, Modifier.fillMaxWidth(), placeholder = "搜索档案、供应商或模型")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    RuntimeFilterChip(!defaultOnly, { defaultOnly = false }, label = { Text("全部") }, shape = RuntimeTheme.shapes.small)
                    RuntimeFilterChip(defaultOnly, { defaultOnly = true }, label = { Text("全局默认") }, shape = RuntimeTheme.shapes.small)
                }
            }
        }
        if (filtered.isEmpty()) {
            item {
                EmptyPanel(
                    RuntimeIconName.Model,
                    if (models.isEmpty()) "暂无模型档案" else "没有匹配的档案",
                    if (models.isEmpty()) "点击新增，或从右上角导入模型配置" else "试试其他关键词，或切换到全部档案",
                )
            }
        }
        items(filtered, key = { it.id }) { model ->
            ModelProfileCard(
                model,
                onEdit = { onEdit(model) },
                onActivate = { onActivate(model.id) },
                onDelete = { onDelete(model) },
                onExport = { onExportSingle(model) },
            )
        }
    }
}

internal fun filterModelProfiles(models: List<AiModelEntity>, query: String, defaultOnly: Boolean): List<AiModelEntity> =
    models.filter { model ->
        (!defaultOnly || model.isActive) && (query.isBlank() || listOf(model.name, model.provider, model.model)
            .any { it.contains(query.trim(), ignoreCase = true) })
    }
