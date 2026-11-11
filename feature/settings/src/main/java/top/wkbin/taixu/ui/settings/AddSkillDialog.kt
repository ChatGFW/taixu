package top.wkbin.taixu.ui.settings

import top.wkbin.taixu.ui.miuix.RuntimeSpacing
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
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
            RuntimeActionRow(modifier = Modifier.fillMaxWidth(), spacing = RuntimeSpacing.dialogActionGap, scrollable = true) {
                RuntimeTextButton(onDismiss) { Text("取消") }
                RuntimeButton(onClick = { if (valid) onConfirm(name, description, prompt, command) }, enabled = valid) {
                    Text("添加并启用")
                }
            }
        },
    ) {
        Column(Modifier.fillMaxWidth().weight(1f, fill = false).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(RuntimeSpacing.cardGap)) {
            RuntimeFilledTextField(name, { name = it },
 labelText = resolveLegacyString("技能名称"),
                placeholder = { Text("例如：Rust 编译专家") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            RuntimeFilledTextField(description, { description = it },
 labelText = resolveLegacyString("简要描述"),
                singleLine = true, modifier = Modifier.fillMaxWidth())
            RuntimeFilledTextField(command, { command = it },
 labelText = resolveLegacyString("触发指令（可选）"),
                placeholder = { Text("例如：/rust") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            RuntimeFilledTextField(prompt, { prompt = it },
 labelText = resolveLegacyString("系统提示词规则"),
                minLines = 4, maxLines = 8, modifier = Modifier.fillMaxWidth())
        }
    }
}
