package top.wkbin.taixu.ui.navigation

import androidx.compose.runtime.Composable
import top.wkbin.taixu.ui.browser.BrowserPlugin
import top.wkbin.taixu.ui.workspace.WorkspaceExplorerScreen
import top.wkbin.taixu.ui.workspace.WorkspaceRunScreen
import top.wkbin.taixu.ui.workspace.codex.WorkspaceCodexScreen

@Composable
internal fun WorkspaceExplorerRoute(
    destination: WorkspaceExplorerDestination,
    onBack: () -> Unit,
    open: (AppDestination) -> Unit,
) {
    val project = destination.projectName
    val projectActionsAvailable = project != "sdcard"
    WorkspaceExplorerScreen(
        projectName = project,
        initialPath = destination.initialPath,
        onBack = onBack,
        onOpenFile = { open(CodeEditorDestination(project, it)) },
        onOpenTerminal = { open(TerminalDestination(project = it)) },
        onOpenGit = if (projectActionsAvailable) ({ open(GitRepositoryDestination(project, openChanges = true)) }) else null,
        onOpenRun = if (projectActionsAvailable) ({ open(WorkspaceRunDestination(project)) }) else null,
        onOpenCodex = if (projectActionsAvailable) ({ open(WorkspaceCodexDestination(project)) }) else null,
    )
}

@Composable
internal fun WorkspaceCodexRoute(destination: WorkspaceCodexDestination, onBack: () -> Unit) =
    WorkspaceCodexScreen(destination.projectName, onBack)

@Composable
internal fun WorkspaceRunRoute(
    destination: WorkspaceRunDestination,
    onBack: () -> Unit,
    open: (AppDestination) -> Unit,
) {
    WorkspaceRunScreen(
        projectName = destination.projectName,
        onBack = onBack,
        onOpenFile = { open(CodeEditorDestination(destination.projectName, it)) },
        onOpenPreview = { port ->
            open(PluginPageDestination(
                pluginId = BrowserPlugin.page.pluginId,
                pageId = BrowserPlugin.page.extensionId,
                arguments = mapOf(BrowserPlugin.PREVIEW_PORT_ARGUMENT to port.toString()),
            ))
        },
    )
}
