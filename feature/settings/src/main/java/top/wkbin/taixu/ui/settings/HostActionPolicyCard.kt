package top.wkbin.taixu.ui.settings

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import top.wkbin.taixu.ui.miuix.FilterChip
import top.wkbin.taixu.ui.miuix.FilterChipDefaults
import top.wkbin.taixu.ui.miuix.RuntimeTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import top.wkbin.taixu.core.browser.ActionPolicyDocument
import top.wkbin.taixu.core.browser.HostActionPolicy
import top.wkbin.taixu.core.browser.HostActionPolicyPresets
import top.wkbin.taixu.ui.components.RuntimeButton as Button
import top.wkbin.taixu.ui.components.RuntimeCard
import top.wkbin.taixu.ui.components.RuntimeFilledTextField
import top.wkbin.taixu.ui.components.RuntimeOutlinedButton as OutlinedButton
import top.wkbin.taixu.ui.components.previewStateOf
import top.wkbin.taixu.ui.settings.LocalizedText as Text

/**
 * CEL-lite 主机动作策略编辑：deny→allow，失败封闭；保存即时生效并写入 DataStore。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun HostActionPolicyCard(viewModel: SettingsViewModel?) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val stored by viewModel?.hostActionPolicyJson?.collectAsStateWithLifecycle()
        ?: previewStateOf(HostActionPolicy.encodePretty(ActionPolicyDocument.DEFAULT))
    var draft by remember(stored) { mutableStateOf(stored) }
    var error by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 10.dp, bottom = 2.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            "主机动作策略（CEL-lite）",
            style = RuntimeTheme.typography.labelLarge.copy(fontWeight = FontWeight.Medium),
            color = RuntimeTheme.colorScheme.primary,
        )
        Text(
            "Bot 浏览器工具在人机接管之后再评估 deny→allow；云元数据始终硬拒。保存后立即生效，无需重启。",
            style = RuntimeTheme.typography.bodySmall,
            color = RuntimeTheme.colorScheme.onSurfaceVariant,
        )
    }
    RuntimeCard(
        modifier = Modifier.fillMaxWidth(),
        containerColor = RuntimeTheme.colorScheme.surfaceContainerLow,
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                "字段：tool.name / page.url / page.host / initiator.kind / intent；" +
                    "运算：== != && || ! contains matches。示例 deny：contains(page.host, 'evil')",
                style = RuntimeTheme.typography.bodySmall,
                color = RuntimeTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                "常用预设（填入草稿，仍需点保存）",
                style = RuntimeTheme.typography.labelMedium,
                color = RuntimeTheme.colorScheme.onSurfaceVariant,
            )
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                HostActionPolicyPresets.ALL.forEach { preset ->
                    FilterChip(
                        selected = false,
                        onClick = {
                            draft = HostActionPolicy.encodePretty(preset.document)
                            error = null
                        },
                        label = { Text(preset.title) },
                        colors = FilterChipDefaults.filterChipColors(),
                    )
                }
            }
            RuntimeFilledTextField(
                value = draft,
                onValueChange = {
                    draft = it
                    error = null
                },
                labelText = resolveLegacyString("策略 JSON"),
                minLines = 3,
                maxLines = 10,
                textStyle = RuntimeTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                isError = error != null,
                supportingText = {
                    Text(
                        error ?: "出厂默认 deny:[] + allow:[\"true\"]；allow 不得为空",
                        style = RuntimeTheme.typography.bodySmall,
                        color = if (error != null) {
                            RuntimeTheme.colorScheme.error
                        } else {
                            RuntimeTheme.colorScheme.onSurfaceVariant
                        },
                    )
                },
                modifier = Modifier.fillMaxWidth(),
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButton(
                    onClick = {
                        val result = HostActionPolicy.validateAndParse(draft)
                        if (result.isSuccess) {
                            error = null
                            Toast.makeText(context, "策略语法有效", Toast.LENGTH_SHORT).show()
                        } else {
                            error = result.exceptionOrNull()?.message ?: "校验失败"
                        }
                    },
                    enabled = !saving,
                    modifier = Modifier.weight(1f),
                ) { Text("校验") }
                OutlinedButton(
                    onClick = {
                        viewModel?.resetHostActionPolicy()
                        error = null
                        Toast.makeText(context, "已恢复出厂默认", Toast.LENGTH_SHORT).show()
                    },
                    enabled = viewModel != null && !saving,
                    modifier = Modifier.weight(1f),
                ) { Text("默认") }
                Button(
                    onClick = {
                        val vm = viewModel ?: return@Button
                        saving = true
                        scope.launch {
                            val result = vm.saveHostActionPolicy(draft)
                            saving = false
                            if (result.isSuccess) {
                                error = null
                                Toast.makeText(context, "策略已保存并生效", Toast.LENGTH_SHORT).show()
                            } else {
                                error = result.exceptionOrNull()?.message ?: "保存失败"
                            }
                        }
                    },
                    enabled = viewModel != null && !saving,
                    modifier = Modifier.weight(1f),
                ) { Text(if (saving) "…" else "保存") }
            }
        }
    }
}
