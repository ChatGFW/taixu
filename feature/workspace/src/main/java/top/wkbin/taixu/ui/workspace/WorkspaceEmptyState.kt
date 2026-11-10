package top.wkbin.taixu.ui.workspace

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import top.wkbin.taixu.feature.workspace.R
import top.wkbin.taixu.ui.components.RuntimeActionRow
import top.wkbin.taixu.ui.components.RuntimeButton
import top.wkbin.taixu.ui.components.RuntimeIcon
import top.wkbin.taixu.ui.components.RuntimeIconName
import top.wkbin.taixu.ui.components.RuntimeOutlinedButton
import top.wkbin.taixu.ui.miuix.RuntimeTheme
import top.yukonga.miuix.kmp.basic.Text
@Composable
internal fun WorkspaceEmptyState(
    enabled: Boolean,
    onCreate: () -> Unit,
    onImport: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        RuntimeIcon(RuntimeIconName.FolderOpen, Modifier.padding(bottom = 8.dp).size(56.dp),
            RuntimeTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f))
        Text(
            text = stringResource(R.string.workspace_no_projects),
            style = RuntimeTheme.typography.titleMedium,
            color = RuntimeTheme.colorScheme.onSurface,
        )
        Text(
            text = stringResource(R.string.workspace_no_projects_description),
            style = RuntimeTheme.typography.bodyMedium,
            color = RuntimeTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        RuntimeActionRow(modifier = Modifier.padding(top = 16.dp)) {
            RuntimeButton(onClick = onCreate, enabled = enabled) {
                Text(stringResource(R.string.workspace_menu_create), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            RuntimeOutlinedButton(onClick = onImport, enabled = enabled) {
                Text(stringResource(R.string.workspace_menu_import), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}
