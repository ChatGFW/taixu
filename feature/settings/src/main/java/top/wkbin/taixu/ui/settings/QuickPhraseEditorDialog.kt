package top.wkbin.taixu.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import top.wkbin.taixu.core.model.QuickPhrase
import top.wkbin.taixu.ui.components.*
import top.wkbin.taixu.ui.settings.LocalizedText as Text

@Composable
internal fun QuickPhraseEditorDialog(
    phrase: QuickPhrase?,
    onDismiss: () -> Unit,
    onSave: (String?, String, String, String, String, String?, Boolean) -> Unit,
) {
    var title by rememberSaveable(phrase?.id) { mutableStateOf(phrase?.title.orEmpty()) }
    var description by rememberSaveable(phrase?.id) { mutableStateOf(phrase?.description.orEmpty()) }
    var content by rememberSaveable(phrase?.id) { mutableStateOf(phrase?.content.orEmpty()) }
    var iconName by rememberSaveable(phrase?.id) { mutableStateOf(phrase?.iconName ?: "Play") }
    var targetProjectType by rememberSaveable(phrase?.id) { mutableStateOf(phrase?.targetProjectType) }
    val isEnabled = phrase?.isEnabled ?: true
    val valid = title.isNotBlank() && content.isNotBlank()
    val iconOptions = listOf("Play" to "运行", "Check" to "检查", "Alert" to "提醒", "Code" to "代码",
        "Plus" to "新增", "Package" to "组件", "Search" to "搜索", "Brain" to "思考", "Bot" to "智能体",
        "Chat" to "对话", "Refresh" to "刷新", "Terminal" to "终端", "Tool" to "工具")
    RuntimeSelectionSheet(
        title = if (phrase == null) "新增快捷短语" else "编辑快捷短语",
        onDismissRequest = onDismiss,
        actions = {
            FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                RuntimeTextButton(onDismiss) { Text("取消") }
                RuntimeButton(onClick = {
                    if (valid) onSave(phrase?.id, title, content, description, iconName, targetProjectType, isEnabled)
                }, enabled = valid) { Text("保存") }
            }
        },
    ) {
        Column(Modifier.fillMaxWidth().weight(1f, fill = false).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)) {
            RuntimeFilledTextField(title, { title = it }, label = { Text("短语标题") },
                placeholder = { Text("例如：运行代码 / 代码审查") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            RuntimeFilledTextField(description, { description = it }, label = { Text("简短说明（可选）") },
                singleLine = true, modifier = Modifier.fillMaxWidth())
            RuntimeFilledTextField(content, { content = it }, label = { Text("提示词 / 命令模板") },
                placeholder = { Text("输入要填入的提示词或命令") }, minLines = 3, maxLines = 8, modifier = Modifier.fillMaxWidth())
            Text("适用工程类型", style = MaterialTheme.typography.titleSmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(null to "通用", "ANDROID" to "Android", "FLUTTER" to "Flutter", "REVERSE" to "逆向分析").forEach { (key, label) ->
                    RuntimeFilterChip(targetProjectType == key, { targetProjectType = key }, label = { Text(label) },
                        shape = MaterialTheme.shapes.small)
                }
            }
            Text("选择图标", style = MaterialTheme.typography.titleSmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                iconOptions.forEach { (key, label) ->
                    RuntimeFilterChip(iconName == key, { iconName = key }, label = { Text(label) },
                        leadingIcon = { RuntimeIcon(parseIconName(key), Modifier.size(18.dp)) }, shape = MaterialTheme.shapes.small)
                }
            }
        }
    }
}
