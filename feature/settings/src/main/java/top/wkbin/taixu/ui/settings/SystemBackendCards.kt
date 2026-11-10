package top.wkbin.taixu.ui.settings

import androidx.compose.foundation.layout.*
import top.wkbin.taixu.ui.miuix.RuntimeTheme
import top.yukonga.miuix.kmp.basic.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import top.wkbin.taixu.core.model.RemoteServer
import top.wkbin.taixu.core.model.SshAuthentication
import top.wkbin.taixu.ui.components.*

@Composable
internal fun BackendChoiceCard(title: String, detail: String, icon: RuntimeIconName, selected: Boolean, onClick: () -> Unit) {
    RuntimeCard(modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(0.dp)) {
        RuntimeRadioPreference(
            title = resolveLegacyString(title), summary = resolveLegacyString(detail),
            selected = selected, onClick = onClick,
            startAction = { SettingsRowIcon(icon) },
        )
    }
}

@Composable
internal fun ConnectionProfileCard(server: RemoteServer, selected: Boolean, onSelect: () -> Unit, onEdit: () -> Unit, onRemove: () -> Unit) {
    RuntimeCard(modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(16.dp), onClick = onSelect,
        containerColor = if (selected) RuntimeTheme.colorScheme.primaryContainer.copy(alpha = 0.35f) else RuntimeTheme.colorScheme.surfaceContainerLow,
        borderColor = if (selected) RuntimeTheme.colorScheme.primary else Color.Transparent) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                IconTile(RuntimeIconName.Server, size = 36.dp, color = RuntimeTheme.colorScheme.onSurface)
                Column(Modifier.weight(1f)) {
                    Text(server.name, style = RuntimeTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("${server.username}@${server.host}:${server.port}", style = RuntimeTheme.typography.bodySmall,
                        color = RuntimeTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                RuntimeIconButton(onClick = onEdit, contentDescription = "编辑 ${server.name}") { RuntimeIcon(RuntimeIconName.Edit) }
                RuntimeIconButton(onClick = onRemove, contentDescription = "移除 ${server.name}") { RuntimeIcon(RuntimeIconName.Trash, tint = RuntimeTheme.colorScheme.error) }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(server.workspace, modifier = Modifier.weight(1f), style = RuntimeTheme.typography.bodySmall,
                    color = RuntimeTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                StatusBadge(if (selected) "主系统" else if (server.authentication == SshAuthentication.PRIVATE_KEY) "密钥认证" else "密码认证")
            }
        }
    }
}
