package top.wkbin.taixu.ui.bot

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import top.wkbin.taixu.ui.components.RuntimeFilterChip
import top.wkbin.taixu.ui.miuix.RuntimeTheme
import top.yukonga.miuix.kmp.basic.Text

@Composable
internal fun BotCapabilityExpiryPanel(expiresAt: Long?, enabled: Boolean, onChange: (Long?) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("MCP 授权有效期", style = RuntimeTheme.typography.bodyMedium)
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            RuntimeFilterChip(selected = expiresAt == null, onClick = { onChange(null) }, enabled = enabled,
                label = { Text("不限时") })
            listOf(15 to "15 分钟", 60 to "1 小时", 1440 to "24 小时").forEach { (minutes, label) ->
                RuntimeFilterChip(selected = false,
                    onClick = { onChange(System.currentTimeMillis() + minutes * 60_000L) }, enabled = enabled,
                    label = { Text(label) })
            }
        }
        val deadline = expiresAt?.let(::formatCapabilityDeadline)
        Text(deadline?.let { "截止时间 $it，保存后生效。任务沿用创建时的期限上限，续办保留原期限。" }
            ?: "保存后允许长期使用所选 MCP 工具。新委派任务同时受派单成员与父任务权限约束。",
            style = RuntimeTheme.typography.labelSmall, color = RuntimeTheme.colorScheme.onSurfaceVariant)
    }
}

internal fun formatCapabilityDeadline(expiresAt: Long): String =
    DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.systemDefault()).format(Instant.ofEpochMilli(expiresAt))
