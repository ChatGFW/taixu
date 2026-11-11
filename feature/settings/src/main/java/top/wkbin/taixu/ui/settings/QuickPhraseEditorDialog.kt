package top.wkbin.taixu.ui.settings

import top.wkbin.taixu.ui.miuix.RuntimeSpacing
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import top.wkbin.taixu.ui.miuix.RuntimeTheme
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
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
    val iconOptions = listOf(
        "Play" to "运行", "Check" to "检查", "Alert" to "提醒", "Code" to "代码",
        "Plus" to "新增", "Package" to "组件", "Search" to "搜索", "Brain" to "思考", "Bot" to "智能体",
        "Chat" to "对话", "Refresh" to "刷新", "Terminal" to "终端", "Tool" to "工具",
    )
    RuntimeSelectionSheet(
        title = if (phrase == null) "新增快捷短语" else "编辑快捷短语",
        onDismissRequest = onDismiss,
        actions = {
            RuntimeActionRow(modifier = Modifier.fillMaxWidth(), spacing = RuntimeSpacing.dialogActionGap, scrollable = true) {
                RuntimeTextButton(onDismiss) { Text("取消") }
                RuntimeButton(
                    onClick = {
                        if (valid) onSave(phrase?.id, title, content, description, iconName, targetProjectType, isEnabled)
                    },
                    enabled = valid,
                ) { Text("保存") }
            }
        },
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .weight(1f, fill = false)
                .verticalScroll(rememberScrollState())
                .padding(bottom = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            RuntimeFilledTextField(
                title,
                { title = it },
                labelText = resolveLegacyString("短语标题"),
                placeholder = { Text("例如：运行代码 / 代码审查") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            RuntimeFilledTextField(
                description,
                { description = it },
                labelText = resolveLegacyString("简短说明（可选）"),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            RuntimeFilledTextField(
                content,
                { content = it },
                labelText = resolveLegacyString("提示词 / 命令模板"),
                placeholder = { Text("输入要填入的提示词或命令") },
                minLines = 3,
                maxLines = 8,
                modifier = Modifier.fillMaxWidth(),
            )
            // 分段：标题贴近芯片，两段之间用外层 16.dp 拉开。
            Column(
                Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("适用工程类型", style = RuntimeTheme.typography.titleSmall)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    listOf(
                        null to "通用",
                        "ANDROID" to "Android",
                        "FLUTTER" to "Flutter",
                        "REVERSE" to "逆向分析",
                    ).forEach { (key, label) ->
                        RuntimeFilterChip(
                            selected = targetProjectType == key,
                            onClick = { targetProjectType = key },
                            label = { Text(label) },
                            shape = RuntimeTheme.shapes.small,
                        )
                    }
                }
            }
            Column(
                Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("选择图标", style = RuntimeTheme.typography.titleSmall)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    iconOptions.forEach { (key, label) ->
                        RuntimeFilterChip(
                            selected = iconName == key,
                            onClick = { iconName = key },
                            label = { Text(label) },
                            leadingIcon = { RuntimeIcon(parseIconName(key), Modifier.size(18.dp)) },
                            shape = RuntimeTheme.shapes.small,
                        )
                    }
                }
            }
        }
    }
}
