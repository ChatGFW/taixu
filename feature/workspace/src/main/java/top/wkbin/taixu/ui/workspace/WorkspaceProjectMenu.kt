package top.wkbin.taixu.ui.workspace

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import top.wkbin.taixu.feature.workspace.R
import top.wkbin.taixu.ui.components.RuntimeIcon
import top.wkbin.taixu.ui.components.RuntimeIconMenu
import top.wkbin.taixu.ui.components.RuntimeIconName
import top.yukonga.miuix.kmp.basic.DropdownEntry
import top.yukonga.miuix.kmp.basic.DropdownItem

@Composable
internal fun WorkspaceProjectMenu(
    enabled: Boolean,
    onCreateFile: () -> Unit,
    onCreateFolder: () -> Unit,
    onOpenGit: (() -> Unit)?,
    onOpenRun: (() -> Unit)?,
) {
    val label = stringResource(R.string.workspace_project_actions)
    val file = stringResource(R.string.workspace_new_file)
    val folder = stringResource(R.string.workspace_new_folder)
    val git = stringResource(R.string.workspace_project_git)
    val run = stringResource(R.string.workspace_run_title)
    Box(Modifier.semantics { contentDescription = label }) {
        RuntimeIconMenu(
            enabled = enabled,
            entries = listOf(DropdownEntry(items = buildList {
                add(projectAction(file, RuntimeIconName.File, onCreateFile))
                add(projectAction(folder, RuntimeIconName.Folder, onCreateFolder))
                onOpenGit?.let { add(projectAction(git, RuntimeIconName.GitBranch, it)) }
                onOpenRun?.let { add(projectAction(run, RuntimeIconName.Play, it)) }
            })),
        ) {
            RuntimeIcon(RuntimeIconName.More, Modifier.size(20.dp))
        }
    }
}

private fun projectAction(label: String, icon: RuntimeIconName, onClick: () -> Unit) = DropdownItem(
    text = label,
    icon = { modifier -> RuntimeIcon(icon, modifier.size(18.dp)) },
    onClick = onClick,
)
