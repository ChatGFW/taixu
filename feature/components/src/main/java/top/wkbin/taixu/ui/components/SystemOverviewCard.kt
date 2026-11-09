package top.wkbin.taixu.ui.components

import androidx.compose.foundation.layout.*
import top.wkbin.taixu.ui.miuix.RuntimeTheme
import top.yukonga.miuix.kmp.basic.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/** Shared presentation only; metrics and connection state always come from the selected backend. */
@Composable
fun SystemOverviewCard(name: String, address: String, workspace: String, loading: Boolean,
    error: String?, facts: List<Pair<String, String>>) {
    RuntimeCard(modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(24.dp),
        shape = RuntimeTheme.shapes.extraLarge, containerColor = RuntimeTheme.colorScheme.surfaceContainer) {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                IconTile(icon = RuntimeIconName.Server, size = 44.dp, color = RuntimeTheme.colorScheme.primary)
                Column(Modifier.weight(1f)) {
                    Text(name, style = RuntimeTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(address, style = RuntimeTheme.typography.bodySmall, color = RuntimeTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Text(if (loading) "连接中" else if (error != null) "连接失败" else "已连接",
                    style = RuntimeTheme.typography.labelMedium, color = if (error != null) RuntimeTheme.colorScheme.error else RuntimeTheme.colorScheme.primary)
            }
            Text(workspace, style = RuntimeTheme.typography.bodySmall, color = RuntimeTheme.colorScheme.onSurfaceVariant,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (loading) RuntimeLinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            if (error != null) Text(error, color = RuntimeTheme.colorScheme.error, style = RuntimeTheme.typography.bodyMedium)
            facts.chunked(2).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    row.forEach { (label, value) -> Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(label, style = RuntimeTheme.typography.labelMedium, color = RuntimeTheme.colorScheme.onSurfaceVariant)
                        Text(value, style = RuntimeTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    } }
                }
            }
        }
    }
}
