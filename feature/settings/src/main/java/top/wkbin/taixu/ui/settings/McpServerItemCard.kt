package top.wkbin.taixu.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text as MaterialText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import top.wkbin.taixu.core.model.McpConnectionState
import top.wkbin.taixu.core.model.McpServerConfig
import top.wkbin.taixu.ui.components.RuntimeCard
import top.wkbin.taixu.ui.components.RuntimeSwitch as Switch
import top.wkbin.taixu.ui.settings.LocalizedText as Text

/**
 * 系统核心 / 自定义 MCP 服务的列表卡片与连通性状态点。
 *
 * 独立成文件的原因有二：McpSettingsScreen 已超出架构棘轮基线，新增样式必须外置；
 * 且「需先安装组件」这一状态需要与「已装但未启用」在视觉上区分，属于卡片自身的职责。
 */

@Composable
internal fun McpServerItemCard(
    server: McpServerConfig,
    connectionState: McpConnectionState,
    error: String?,
    togglePending: Boolean,
    requiredComponentName: String?,
    onToggle: (Boolean) -> Unit,
    onClickDetail: () -> Unit,
) {
    RuntimeCard(
        modifier = Modifier.fillMaxWidth(),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
        onClick = onClickDetail,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(
                modifier = Modifier.weight(1f).padding(end = 12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        server.name,
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f, fill = false),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    McpConnectionDot(state = connectionState, enabled = server.isEnabled)
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = MaterialTheme.colorScheme.primaryContainer,
                    ) {
                        MaterialText(
                            server.transportType.name,
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp, fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.5.dp),
                            maxLines = 1,
                            overflow = TextOverflow.Visible,
                            softWrap = false,
                        )
                    }
                }
                if (server.description.isNotBlank()) {
                    Text(
                        server.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (server.isEnabled && connectionState in setOf(McpConnectionState.SLEEPING, McpConnectionState.WAKING)) {
                    Text(androidx.compose.ui.res.stringResource(if (connectionState == McpConnectionState.SLEEPING)
                        top.wkbin.taixu.feature.settings.R.string.mcp_client_sleeping else top.wkbin.taixu.feature.settings.R.string.mcp_client_waking),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                // 离线原因（含远程 MCP 可执行文件缺失等环境相关失败），在线时 error 为空不展示
                if (connectionState == McpConnectionState.OFFLINE && !error.isNullOrBlank()) {
                    Text(
                        error,
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                        color = MaterialTheme.colorScheme.error,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                // 按需能力尚未安装：这里必须与"已装但没开"区分开，否则用户会反复点开关
                if (requiredComponentName != null) {
                    Text(
                        "需先安装「$requiredComponentName」：设置 → 工具中心 → MCP 能力仓库",
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                        color = MaterialTheme.colorScheme.tertiary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            Switch(
                checked = server.isEnabled,
                onCheckedChange = onToggle,
                // 未安装时锁死开关：打开只会让启动期工具发现每次白等一个超时
                enabled = !togglePending && (server.isEnabled || requiredComponentName == null),
            )
        }
    }
}

/**
 * MCP 服务连通性状态小圆点：绿=在线 / 红=离线 / 转圈=检测中 / 灰=未知或未启用。
 */
@Composable
internal fun McpConnectionDot(state: McpConnectionState, enabled: Boolean) {
    val (color, label) = when {
        !enabled -> MaterialTheme.colorScheme.outlineVariant to "未启用"
        state == McpConnectionState.CHECKING -> MaterialTheme.colorScheme.tertiary to "检测中"
        state == McpConnectionState.ONLINE -> successStatusColor() to "已连通"
        state == McpConnectionState.SLEEPING -> MaterialTheme.colorScheme.secondary to "已就绪（休眠）"
        state == McpConnectionState.WAKING -> MaterialTheme.colorScheme.tertiary to "正在唤醒"
        state == McpConnectionState.OFFLINE -> MaterialTheme.colorScheme.error to "离线"
        else -> MaterialTheme.colorScheme.outline to "未检测"
    }
    Box(
        modifier = Modifier
            .size(10.dp)
            .semantics { contentDescription = label }
            .clip(CircleShape)
            .background(color),
    )
}
