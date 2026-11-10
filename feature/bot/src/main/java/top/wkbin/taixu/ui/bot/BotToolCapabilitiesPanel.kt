package top.wkbin.taixu.ui.bot

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import top.wkbin.taixu.core.model.McpServerConfig
import top.wkbin.taixu.ui.components.RuntimeFilledTextField
import top.wkbin.taixu.ui.components.RuntimeSwitch
import top.wkbin.taixu.ui.miuix.RuntimeTheme
import top.yukonga.miuix.kmp.basic.Text

/** 使用精确工具名配置权限，无需为编辑权限启动 MCP 进程。 */
@Composable
internal fun BotToolCapabilitiesPanel(
    servers: List<McpServerConfig>,
    selectedServers: List<String>?,
    tools: Map<String, List<String>>,
    enabled: Boolean,
    onChange: (Map<String, List<String>>) -> Unit,
) {
    val selected = selectedServers?.map { it.lowercase() }?.toSet()
    servers.filter { it.isEnabled && (selected == null || it.id.lowercase() in selected || it.name.lowercase() in selected) }
        .forEach { server ->
            key(server.id) {
                val allowed = tools[server.id]
                var text by remember(server.id, allowed == null) { mutableStateOf(allowed.orEmpty().joinToString(", ")) }
                LaunchedEffect(allowed) {
                    if (parseCapabilityTools(text) != allowed.orEmpty()) text = allowed.orEmpty().joinToString(", ")
                }
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("${server.name} · 全部工具", Modifier.weight(1f), style = RuntimeTheme.typography.bodyMedium)
                        RuntimeSwitch(
                            checked = allowed == null,
                            onCheckedChange = { all ->
                                onChange(if (all) tools - server.id else tools + (server.id to emptyList()))
                            },
                            enabled = enabled,
                        )
                    }
                    if (allowed != null) {
                        RuntimeFilledTextField(
                            value = text,
                            onValueChange = {
                                text = it
                                onChange(tools + (server.id to parseCapabilityTools(it)))
                            },
                            enabled = enabled,
                            labelText = "允许的工具名",
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Text(
                            "从 MCP 服务详情复制精确工具名，以逗号或换行分隔，区分大小写。留空禁用该服务全部工具，保存后生效。",
                            style = RuntimeTheme.typography.labelSmall,
                            color = RuntimeTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
}

internal fun parseCapabilityTools(text: String): List<String> =
    text.split(',', '，', '\n').map(String::trim).filter(String::isNotEmpty).distinct()
