package top.wkbin.taixu.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import top.wkbin.taixu.ui.miuix.RuntimeTheme
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.theme.MiuixTheme
import androidx.compose.ui.text.font.FontWeight
import top.yukonga.miuix.kmp.basic.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import top.wkbin.taixu.core.model.McpConnectionState
import top.wkbin.taixu.ui.theme.semanticColors

/**
 * 设置页与聊天能力面板共用的服务行。
 * 状态、工具数和授权信息可换行，说明与错误最多展示两行。
 */
@Composable
fun McpServerRow(
    name: String,
    enabled: Boolean,
    connectionState: McpConnectionState,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    toolCount: Int? = null,
    authLabel: String? = null,
    authOk: Boolean = false,
    notice: String? = null,
    error: String? = null,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable RowScope.() -> Unit)? = null,
    bottomActions: (@Composable () -> Unit)? = null,
) {
    val stateLabel = mcpStateLabel(enabled, connectionState)
    RuntimeCard(modifier = modifier.fillMaxWidth(), onClick = onClick,
        containerColor = RuntimeTheme.colorScheme.surfaceContainerLow,
        shape = RuntimeTheme.shapes.medium, contentPadding = PaddingValues(0.dp)) {
        BasicComponent(
            startAction = {
                RuntimeIcon(RuntimeIconName.Network, Modifier.padding(end = 6.dp).size(24.dp), MiuixTheme.colorScheme.onSurface)
            },
            endActions = trailing,
            bottomAction = if (bottomActions != null) {
                { FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                    verticalArrangement = Arrangement.spacedBy(8.dp), itemVerticalAlignment = Alignment.CenterVertically) {
                    bottomActions()
                } }
            } else null,
        ) {
            Text(name, style = MiuixTheme.textStyles.headline1, fontWeight = FontWeight.Medium,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(stateLabel, style = MiuixTheme.textStyles.footnote1, color = mcpStateColor(enabled, connectionState))
                if (toolCount != null && toolCount > 0) Text("$toolCount 工具", style = MiuixTheme.textStyles.footnote1,
                    color = RuntimeTheme.colorScheme.onSurfaceVariant)
                authLabel?.let { label -> Text(label, style = MiuixTheme.textStyles.footnote1,
                    color = if (authOk) RuntimeTheme.colorScheme.onSurfaceVariant else RuntimeTheme.colorScheme.error) }
            }
            val secondary = error?.takeIf { it.isNotBlank() } ?: notice?.takeIf { it.isNotBlank() }
                ?: subtitle?.takeIf { it.isNotBlank() }
            if (secondary != null) Text(secondary.replace('\n', ' '), style = MiuixTheme.textStyles.body2,
                color = when {
                    !error.isNullOrBlank() -> RuntimeTheme.colorScheme.error
                    !notice.isNullOrBlank() -> RuntimeTheme.colorScheme.tertiary
                    else -> RuntimeTheme.colorScheme.onSurfaceVariant
                }, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun mcpStateColor(enabled: Boolean, state: McpConnectionState): Color = when {
    !enabled -> RuntimeTheme.colorScheme.outlineVariant
    state == McpConnectionState.ONLINE || state == McpConnectionState.SLEEPING -> semanticColors().success
    state == McpConnectionState.CHECKING || state == McpConnectionState.WAKING -> RuntimeTheme.colorScheme.tertiary
    state == McpConnectionState.OFFLINE -> RuntimeTheme.colorScheme.error
    else -> RuntimeTheme.colorScheme.outline
}

private fun mcpStateLabel(enabled: Boolean, state: McpConnectionState): String = when {
    !enabled -> "已停用"
    state == McpConnectionState.ONLINE -> "在线"
    state == McpConnectionState.SLEEPING -> "已就绪（休眠）"
    state == McpConnectionState.WAKING -> "正在唤醒"
    state == McpConnectionState.CHECKING -> "检测中"
    state == McpConnectionState.OFFLINE -> "离线"
    else -> "状态未知"
}
