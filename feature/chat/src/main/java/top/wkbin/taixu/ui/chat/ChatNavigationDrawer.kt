package top.wkbin.taixu.ui.chat

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import top.wkbin.taixu.ui.components.AppNavigationSection
import top.wkbin.taixu.ui.components.MainDestination
import top.wkbin.taixu.ui.components.RuntimeRevealNavigationDrawer

@Composable
fun ChatNavigationDrawer(
    viewModel: ChatViewModel,
    visible: Boolean,
    onDismiss: () -> Unit,
    onOpen: (() -> Unit)? = null,
    newSessionVisible: Boolean,
    selected: MainDestination,
    onNewSession: () -> Unit,
    onDismissNewSession: () -> Unit,
    onNavigate: (MainDestination) -> Unit,
    onOpenTerminal: () -> Unit,
    onOpenTools: () -> Unit,
    content: @Composable () -> Unit,
) {
    val sessions by viewModel.sessions.collectAsStateWithLifecycle()
    val catalog by viewModel.workspaceCatalog.collectAsStateWithLifecycle()
    val currentSessionId by viewModel.currentSessionId.collectAsStateWithLifecycle()
    val runStates by viewModel.sessionRunStates.collectAsStateWithLifecycle()

    RuntimeRevealNavigationDrawer(
        visible = visible,
        onDismiss = onDismiss,
        onOpen = onOpen,
        drawerContent = {
            SessionsSideDrawer(
                visible = true,
                sessions = sessions,
                currentSessionId = currentSessionId,
                workspaces = catalog.projects,
                sessionRunStates = runStates,
                onDismiss = onDismiss,
                onSettings = { onDismiss(); onNavigate(MainDestination.Settings) },
                onSwitch = { id -> viewModel.switchSession(id); onNavigate(MainDestination.Agent); onDismiss() },
                onNew = { onDismiss(); onNewSession() },
                onCreateInWorkspace = { project ->
                    viewModel.createSession("", project.linuxPath, project.projectType.name)
                    onNavigate(MainDestination.Agent); onDismiss()
                },
                onDelete = viewModel::deleteSession,
                onRename = viewModel::renameSession,
                navigationContent = {
                    AppNavigationSection(
                        selected = selected,
                        onNavigate = { destination -> onDismiss(); onNavigate(destination) },
                        onOpenTerminal = { onDismiss(); onOpenTerminal() },
                        onOpenTools = { onDismiss(); onOpenTools() },
                    )
                },
            )
        },
        content = content,
    )

    if (newSessionVisible) {
        NewSessionDialog(
            catalog.projects,
            catalog,
            onDismiss = onDismissNewSession,
            onCreate = { title, workspace, type ->
                viewModel.createSession(title, workspace, type.name)
                onDismissNewSession()
                onNavigate(MainDestination.Agent)
            }
        )
    }
}
