package top.wkbin.taixu.ui.chat

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import top.wkbin.taixu.ui.components.AppNavigationSection
import top.wkbin.taixu.ui.components.MainDestination

/** One conversation owner and drawer are shared by every root destination. */
@Composable
fun ChatNavigationDrawer(viewModel: ChatViewModel, visible: Boolean, newSessionVisible: Boolean,
    selected: MainDestination, onDismiss: () -> Unit, onNewSession: () -> Unit,
    onDismissNewSession: () -> Unit, onNavigate: (MainDestination) -> Unit,
    onOpenTerminal: () -> Unit, onOpenTools: () -> Unit) {
    if (!visible && !newSessionVisible) return
    val sessions by viewModel.sessions.collectAsStateWithLifecycle()
    val catalog by viewModel.workspaceCatalog.collectAsStateWithLifecycle()
    val currentSessionId by viewModel.currentSessionId.collectAsStateWithLifecycle()
    val runStates by viewModel.sessionRunStates.collectAsStateWithLifecycle()
    SessionsSideDrawer(
        visible = visible, sessions = sessions, currentSessionId = currentSessionId,
        workspaces = catalog.projects, sessionRunStates = runStates, onDismiss = onDismiss,
        onSwitch = { id -> viewModel.switchSession(id); onNavigate(MainDestination.Agent); onDismiss() },
        onNew = { onDismiss(); onNewSession() },
        onCreateInWorkspace = { project ->
            viewModel.createSession("", project.linuxPath, project.projectType.name)
            onNavigate(MainDestination.Agent); onDismiss()
        },
        onDelete = viewModel::deleteSession, onRename = viewModel::renameSession,
        navigationContent = {
            AppNavigationSection(selected, { destination -> onDismiss(); onNavigate(destination) },
                { onDismiss(); onOpenTerminal() }, { onDismiss(); onOpenTools() })
        },
    )
    if (newSessionVisible) NewSessionDialog(catalog.projects, catalog,
        onDismiss = onDismissNewSession, onCreate = { title, workspace, type ->
            viewModel.createSession(title, workspace, type.name)
            onDismissNewSession(); onNavigate(MainDestination.Agent)
        })
}
