package top.wkbin.taixu.ui.workspace

import androidx.compose.runtime.Composable
import top.wkbin.taixu.ui.components.FoldablePreviews
import top.wkbin.taixu.ui.components.RuntimePreview

@FoldablePreviews
@Composable
private fun WorkspaceScreenPreview() {
    RuntimePreview {
        LocalWorkspaceScreen(onNavigate = { _ -> }, onOpenExplorer = { _ -> }, onOpenTerminal = { _ -> })
    }
}

@FoldablePreviews
@Composable
private fun RemoteWorkspaceScreenPreview() {
    RuntimePreview {
        RemoteWorkspaceScreen(serverId = "preview", onNavigate = { _ -> })
    }
}
