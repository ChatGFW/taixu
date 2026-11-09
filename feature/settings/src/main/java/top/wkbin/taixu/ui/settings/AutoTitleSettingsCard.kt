package top.wkbin.taixu.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import top.wkbin.taixu.ui.miuix.OutlinedTextField
import top.wkbin.taixu.ui.miuix.RuntimeTheme
import top.yukonga.miuix.kmp.basic.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import top.wkbin.taixu.core.database.AiModelEntity
import top.wkbin.taixu.ui.components.RuntimeDropdownField

/**
 * 自动会话标题配置卡片：开关、自定义提示词（空白用内置默认）、指定模型档案
 * （空白 = 跟随会话绑定模型）。偏好读写由调用方代理到 AgentPreferences。
 */
@Composable
internal fun AutoTitleSettingsCard(
    enabled: Boolean,
    onEnabledChange: (Boolean) -> Unit,
    prompt: String,
    onPromptChange: (String) -> Unit,
    modelId: String,
    onModelIdChange: (String) -> Unit,
    models: List<AiModelEntity>,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        top.yukonga.miuix.kmp.preference.SwitchPreference(
            title = "回合结束后自动生成会话标题",
            summary = "仅对仍是「新会话」等默认标题的会话生效；每个会话只尝试一次",
            checked = enabled, onCheckedChange = onEnabledChange,
            insideMargin = PaddingValues(horizontal = 0.dp, vertical = 16.dp),
        )

        if (enabled) {
            OutlinedTextField(
                value = prompt,
                onValueChange = onPromptChange,
                modifier = Modifier.fillMaxWidth(),
                minLines = 2,
                placeholder = { Text("自定义标题提示词（空白 = 内置默认）") },
                supportingText = { Text("提示词需自行要求只输出标题文本（生成结果上限 24 字）") },
            )

            val options = remember(models) {
                listOf("" to "跟随会话绑定模型") + models.map { it.id to it.name }
            }
            val selectedIndex = options.indexOfFirst { it.first == modelId }.let { if (it < 0) 0 else it }
            RuntimeDropdownField(
                title = "标题生成模型",
                items = options.map { it.second },
                selectedIndex = selectedIndex,
                onSelectedIndexChange = { onModelIdChange(options[it].first) },
                summary = "无绑定模型的会话用此处指定档案；两者皆空时跳过标题生成",
            )
        }
    }
}
