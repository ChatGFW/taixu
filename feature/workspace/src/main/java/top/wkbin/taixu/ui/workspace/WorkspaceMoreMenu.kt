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
import top.wkbin.taixu.ui.components.RuntimeIconName
import top.wkbin.taixu.ui.components.SpotlightAnchor
import top.wkbin.taixu.ui.components.spotlightAnchor
import top.wkbin.taixu.ui.miuix.RuntimeTheme
import top.yukonga.miuix.kmp.basic.DropdownEntry
import top.yukonga.miuix.kmp.basic.DropdownItem
import top.yukonga.miuix.kmp.menu.WindowIconDropdownMenu

@Composable
internal fun WorkspaceMoreMenu(
    enabled: Boolean,
    moreAnchor: SpotlightAnchor,
    onCreate: () -> Unit,
    onImport: () -> Unit,
    onTemplates: () -> Unit,
    onPlugins: () -> Unit,
    onWorkflows: () -> Unit,
    onSettings: () -> Unit,
) {
    val moreLabel = stringResource(R.string.workspace_cd_more)
    val menuEntries = listOf(
        DropdownEntry(
            items = listOf(
                DropdownItem(
                    text = stringResource(R.string.workspace_menu_create),
                    icon = { mod -> RuntimeIcon(RuntimeIconName.Plus, mod.size(18.dp)) },
                    onClick = onCreate,
                ),
                DropdownItem(
                    text = stringResource(R.string.workspace_menu_import),
                    icon = { mod -> RuntimeIcon(RuntimeIconName.FolderDownload, mod.size(18.dp)) },
                    onClick = onImport,
                ),
                DropdownItem(
                    text = stringResource(R.string.workspace_menu_templates),
                    icon = { mod -> RuntimeIcon(RuntimeIconName.Package, mod.size(18.dp)) },
                    onClick = onTemplates,
                ),
                DropdownItem(
                    text = stringResource(R.string.workspace_menu_plugins),
                    icon = { mod -> RuntimeIcon(RuntimeIconName.Package, mod.size(18.dp)) },
                    onClick = onPlugins,
                ),
                DropdownItem(
                    text = stringResource(R.string.workspace_menu_workflows),
                    icon = { mod -> RuntimeIcon(RuntimeIconName.Hub, mod.size(18.dp)) },
                    onClick = onWorkflows,
                ),
                DropdownItem(
                    text = stringResource(R.string.workspace_menu_settings),
                    icon = { mod -> RuntimeIcon(RuntimeIconName.Settings, mod.size(18.dp)) },
                    onClick = onSettings,
                ),
            ),
        ),
    )
    Box(
        Modifier
            .spotlightAnchor(moreAnchor)
            .semantics { contentDescription = moreLabel },
    ) {
        WindowIconDropdownMenu(
            entries = menuEntries,
            enabled = enabled,
            content = {
                RuntimeIcon(RuntimeIconName.More, Modifier.size(20.dp), tint = RuntimeTheme.colorScheme.primary)
            },
        )
    }
}
