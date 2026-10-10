package top.wkbin.taixu.ui.workspace

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import top.wkbin.taixu.feature.workspace.R
import top.wkbin.taixu.ui.components.EmptyPanel
import top.wkbin.taixu.ui.components.RuntimeIconName
import top.wkbin.taixu.ui.components.RuntimeTextButton as TextButton

@Composable
internal fun WorkspaceEmptyState(
    enabled: Boolean,
    onCreate: () -> Unit,
    onImport: () -> Unit,
) {
    EmptyPanel(
        icon = RuntimeIconName.Workspace,
        title = stringResource(R.string.workspace_no_projects),
        description = stringResource(R.string.workspace_no_projects_description),
        modifier = Modifier.padding(top = 24.dp),
    )
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
    ) {
        TextButton(onClick = onCreate, enabled = enabled) {
            Text(stringResource(R.string.workspace_menu_create), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        TextButton(onClick = onImport, enabled = enabled) {
            Text(stringResource(R.string.workspace_menu_import), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}
