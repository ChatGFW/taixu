package top.wkbin.taixu.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SuggestionChip
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import top.wkbin.taixu.ui.components.*
import top.wkbin.taixu.ui.settings.LocalizedText as Text

@Composable
internal fun SystemPromptCustomCard(
    enabled: Boolean,
    onEnabledChange: (Boolean) -> Unit,
    prompt: String,
    onPromptChange: (String) -> Unit,
    charName: String,
    onCharNameChange: (String) -> Unit,
    userName: String,
    onUserNameChange: (String) -> Unit,
) {
    // 仅在首次进入时以持久化值初始化缓冲，避免 remember(prompt) 键在每次 Datastore 回写时重置输入；
    // 输入经 400ms 防抖后再写入 Datastore，避免每次按键都落盘
    var textBuffer by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(prompt) }
    androidx.compose.runtime.LaunchedEffect(textBuffer) {
        if (textBuffer != prompt) {
            delay(400)
            onPromptChange(textBuffer)
        }
    }

    // 称呼输入同样走 400ms 防抖落盘；留空即回退默认值
    var charNameBuffer by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(charName) }
    androidx.compose.runtime.LaunchedEffect(charNameBuffer) {
        if (charNameBuffer != charName) {
            delay(400)
            onCharNameChange(charNameBuffer)
        }
    }
    var userNameBuffer by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(userName) }
    androidx.compose.runtime.LaunchedEffect(userNameBuffer) {
        if (userNameBuffer != userName) {
            delay(400)
            onUserNameChange(userNameBuffer)
        }
    }

    var showDefaultPromptDialog by remember { mutableStateOf(false) }
    var defaultPromptAsset by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf("") }
    val androidContext = androidx.compose.ui.platform.LocalContext.current
    if (showDefaultPromptDialog) {
        androidx.compose.runtime.LaunchedEffect(showDefaultPromptDialog) {
            if (defaultPromptAsset.isBlank()) {
                defaultPromptAsset = runCatching {
                    androidContext.assets.open("prompts/system/core.md").bufferedReader().use { it.readText() }
                }.getOrDefault("（默认系统提示词加载失败）")
            }
        }
        AgentPromptSheet(title = "默认系统提示词", prompt = defaultPromptAsset,
            onDismiss = { showDefaultPromptDialog = false })
    }

    val defaultPromptTemplate = """
You are a helpful and expert AI assistant called {{char}}, based on model {{model_name}}.

## Persona
- You refer to yourself as {{char}} in conversations.
- You address the user as {{user}}.

## System & Device Context
- Time: {{cur_datetime}}
- Locale: {{locale}}
- Timezone: {{timezone}}
- Device: {{device_info}}
- System: {{system_version}}
- Battery: {{battery_level}}

## Instructions
- Always think carefully before answering.
- Follow user instructions precisely and write clean code.
    """.trimIndent()

    val availableVariables = listOf(
        "{{cur_date}}" to "日期",
        "{{cur_time}}" to "时间",
        "{{cur_datetime}}" to "日期时间",
        "{{model_name}}" to "模型名称",
        "{{model_id}}" to "模型ID",
        "{{locale}}" to "语言环境",
        "{{timezone}}" to "时区",
        "{{device_info}}" to "设备信息",
        "{{system_version}}" to "系统版本",
        "{{battery_level}}" to "电池电量",
        "{{char}}" to "助手名称",
        "{{user}}" to "用户名称",
    )

    RuntimeCard(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.extraLarge) {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                RuntimeIcon(RuntimeIconName.Prompt, Modifier.size(24.dp), MaterialTheme.colorScheme.primary)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("自定义系统提示词", style = MaterialTheme.typography.bodyLarge)
                    Text("覆盖默认提示词，支持动态宏变量", style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                RuntimeSwitch(enabled, onEnabledChange,
                    modifier = Modifier.semantics { contentDescription = "启用自定义系统提示词" })
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("称呼与人设", style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.align(Alignment.CenterVertically))
                RuntimeTextButton(onClick = { showDefaultPromptDialog = true }) {
                    RuntimeIcon(RuntimeIconName.Visibility, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("查看默认系统提示词")
                }
            }
            RuntimeFilledTextField(charNameBuffer, { charNameBuffer = it }, label = { Text("模型自称") },
                placeholder = { Text("太墟智枢") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            RuntimeFilledTextField(userNameBuffer, { userNameBuffer = it }, label = { Text("对用户的称呼") },
                placeholder = { Text("用户") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Text("称呼对所有对话生效，留空恢复默认；模板中的 {{char}} 和 {{user}} 也使用这两个值。",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (enabled) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("提示词模板", style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.align(Alignment.CenterVertically))
                    RuntimeTextButton(onClick = {
                        textBuffer = defaultPromptTemplate
                        onPromptChange(defaultPromptTemplate)
                    }) {
                        RuntimeIcon(RuntimeIconName.Refresh, Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("重置模板")
                    }
                }
                RuntimeFilledTextField(textBuffer, { textBuffer = it }, label = { Text("自定义提示词") },
                    placeholder = { Text("支持 {{cur_datetime}} 等宏变量") }, minLines = 5, maxLines = 10,
                    modifier = Modifier.fillMaxWidth(),
                    textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace))
                Text("插入动态宏变量", style = MaterialTheme.typography.titleSmall)
                FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    availableVariables.forEach { (variable, label) ->
                        SuggestionChip(onClick = {
                            val updated = if (textBuffer.isBlank()) variable else "$textBuffer $variable"
                            textBuffer = updated
                            onPromptChange(updated)
                        }, label = { Text("$label · $variable", style = MaterialTheme.typography.labelMedium) },
                            shape = MaterialTheme.shapes.small)
                    }
                }
            }
        }
    }
}
