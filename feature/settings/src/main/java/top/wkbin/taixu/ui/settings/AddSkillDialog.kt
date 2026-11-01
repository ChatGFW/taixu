package top.wkbin.taixu.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import top.wkbin.taixu.ui.components.*
import top.wkbin.taixu.ui.settings.LocalizedText as Text

@Composable
internal fun AddSkillDialog(
    onDismiss: () -> Unit,
    onConfirm: (name: String, description: String, systemPrompt: String, command: String?) -> Unit,
) {
    var name by rememberSaveable { mutableStateOf("") }
    var description by rememberSaveable { mutableStateOf("") }
    var prompt by rememberSaveable { mutableStateOf("") }
    var command by rememberSaveable { mutableStateOf("") }
    val valid = name.isNotBlank() && prompt.isNotBlank()
    RuntimeSelectionSheet(title = "新增自定义技能", onDismissRequest = onDismiss,
        actions = {
            FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                RuntimeTextButton(onDismiss) { Text("取消") }
                RuntimeButton(onClick = { if (valid) onConfirm(name, description, prompt, command) }, enabled = valid) {
                    Text("添加并启用")
                }
            }
        },
    ) {
        Column(Modifier.fillMaxWidth().weight(1f, fill = false).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)) {
            RuntimeFilledTextField(name, { name = it }, label = { Text("技能名称") },
                placeholder = { Text("例如：Rust 编译专家") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            RuntimeFilledTextField(description, { description = it }, label = { Text("简要描述") },
                singleLine = true, modifier = Modifier.fillMaxWidth())
            RuntimeFilledTextField(command, { command = it }, label = { Text("触发指令（可选）") },
                placeholder = { Text("例如：/rust") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            RuntimeFilledTextField(prompt, { prompt = it }, label = { Text("系统提示词规则") },
                minLines = 4, maxLines = 8, modifier = Modifier.fillMaxWidth())
        }
    }
}
