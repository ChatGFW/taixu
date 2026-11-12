package top.wkbin.taixu.ui.chat

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import top.wkbin.taixu.core.database.repository.PersonaBotRepository
import top.wkbin.taixu.core.datastore.RemoteServerRepository
import top.wkbin.taixu.core.datastore.hiddenBotSessionIdsPreference
import top.wkbin.taixu.core.datastore.setBotSessionHidden
import top.wkbin.taixu.feature.chat.R
import top.wkbin.taixu.feature.components.R as ComponentsR
import top.wkbin.taixu.ui.components.AppNavigationSection
import top.wkbin.taixu.ui.components.LocalPluginDestinationActive
import top.wkbin.taixu.ui.components.MainDestination
import top.wkbin.taixu.ui.components.RuntimeAdaptiveNavigationScaffold
import top.wkbin.taixu.ui.components.RuntimeIconName
import top.wkbin.taixu.ui.components.RuntimeNavigationRail
import top.wkbin.taixu.ui.components.RuntimeRailItem

@Composable
fun ChatNavigationDrawer(
    viewModel: ChatViewModel,
    visible: Boolean,
    onDismiss: () -> Unit,
    onOpen: (() -> Unit)? = null,
    docked: Boolean = true,
    onDockedChange: (Boolean) -> Unit = {},
    newSessionVisible: Boolean,
    selected: MainDestination,
    onNewSession: () -> Unit,
    onDismissNewSession: () -> Unit,
    onNavigate: (MainDestination) -> Unit,
    onOpenTerminal: () -> Unit,
    onOpenTools: () -> Unit,
    onOpenRemoteHome: (serverId: String) -> Unit = {},
    gesturesEnabled: Boolean = true,
    content: @Composable () -> Unit,
) {
    val sessions by viewModel.sessions.collectAsStateWithLifecycle()
    val botRepository = koinInject<PersonaBotRepository>()
    val botSessionIds by remember(botRepository) { botRepository.observeBotSessionIds() }
        .collectAsStateWithLifecycle(emptySet())
    val botSessionGroups by remember(botRepository) { botRepository.observeGroupSessionRefs() }
        .collectAsStateWithLifecycle(emptyMap())
    val context = LocalContext.current
    val hiddenScope = rememberCoroutineScope()
    val hiddenBotSessionIds by remember(context) { context.hiddenBotSessionIdsPreference }
        .collectAsStateWithLifecycle(emptySet())
    val (botSessions, normalSessions) = remember(sessions, botSessionIds) {
        sessions.partition { it.id in botSessionIds }
    }
    val catalog by viewModel.workspaceCatalog.collectAsStateWithLifecycle()
    val currentSessionId by viewModel.currentSessionId.collectAsStateWithLifecycle()
    val runStates by viewModel.sessionRunStates.collectAsStateWithLifecycle()
    val remoteServers by koinInject<RemoteServerRepository>().servers.collectAsStateWithLifecycle()
    val remoteProjectsByServer by viewModel.remoteProjectsByServer.collectAsStateWithLifecycle()
    val pluginActive = LocalPluginDestinationActive.current

    ChatIdentityHost {
        RuntimeAdaptiveNavigationScaffold(
            visible = visible,
            onDismiss = onDismiss,
            onOpen = onOpen,
            gesturesEnabled = gesturesEnabled,
            docked = docked,
            onDockedChange = onDockedChange,
            rail = { onOpenSidebar ->
                fun destination(target: MainDestination, label: String) = RuntimeRailItem(label, target.icon,
                    selected = selected == target && !pluginActive) { onDismiss(); onNavigate(target) }
                RuntimeNavigationRail(
                    header = listOf(
                        RuntimeRailItem(stringResource(ComponentsR.string.components_rail_sessions), RuntimeIconName.Sessions, onClick = onOpenSidebar),
                        RuntimeRailItem(stringResource(ComponentsR.string.components_rail_new_session), RuntimeIconName.Plus) { onDismiss(); onNewSession() },
                    ),
                    items = listOf(
                        destination(MainDestination.Agent, stringResource(ComponentsR.string.components_nav_agent)),
                        destination(MainDestination.Workspace, stringResource(ComponentsR.string.components_drawer_tab_workspace)),
                        destination(MainDestination.Home, stringResource(ComponentsR.string.components_drawer_tab_system)),
                        RuntimeRailItem(stringResource(ComponentsR.string.components_rail_terminal), RuntimeIconName.Terminal) { onDismiss(); onOpenTerminal() },
                        RuntimeRailItem(stringResource(ComponentsR.string.components_rail_tools), RuntimeIconName.Wrench) { onDismiss(); onOpenTools() },
                    ),
                    footer = listOf(destination(MainDestination.Settings, stringResource(ComponentsR.string.components_drawer_settings))),
                )
            },
            drawerContent = { onCollapse ->
                SessionsSideDrawer(
                    visible = true,
                    sessions = normalSessions,
                    botSessions = botSessions,
                    botSessionGroups = botSessionGroups,
                    hiddenBotSessionIds = hiddenBotSessionIds,
                    onHideBotSession = { id, hidden ->
                        hiddenScope.launch { context.setBotSessionHidden(id, hidden) }
                    },
                    currentSessionId = currentSessionId,
                    workspaces = catalog.projects,
                    remoteServers = remoteServers,
                    remoteProjectsByServer = remoteProjectsByServer,
                    sessionRunStates = runStates,
                    onDismiss = onDismiss,
                    onSettings = { onDismiss(); onNavigate(MainDestination.Settings) },
                    onSwitch = { id -> viewModel.switchSession(id); onNavigate(MainDestination.Agent); onDismiss() },
                    searchMessages = viewModel::searchMessages,
                    onTogglePin = viewModel::setSessionPinned,
                    onNew = { onDismiss(); onNewSession() },
                    onCreateInWorkspace = { project ->
                        viewModel.createSession("", project.linuxPath, project.projectType.name)
                        onNavigate(MainDestination.Agent); onDismiss()
                    },
                    onCreateOnServer = { group ->
                        if (!group.profilePresent) {
                            viewModel.showNotice(context.getString(R.string.chat_drawer_remote_profile_unavailable))
                        } else {
                            viewModel.createSession("", "/workspace", "", group.distribution)
                            onNavigate(MainDestination.Agent); onDismiss()
                        }
                    },
                    onCreateInRemoteProject = { group, bucket ->
                        if (!group.profilePresent) {
                            viewModel.showNotice(context.getString(R.string.chat_drawer_remote_profile_unavailable))
                        } else {
                            viewModel.createSession("", bucket.linuxPath, "", group.distribution)
                            onNavigate(MainDestination.Agent); onDismiss()
                        }
                    },
                    onCreateTerminalOnServer = { group ->
                        if (!group.profilePresent) {
                            viewModel.showNotice(context.getString(R.string.chat_drawer_remote_profile_unavailable))
                        } else {
                            viewModel.createRemoteTerminal(group.distribution, group.serverName) { ok ->
                                onDismiss()
                                if (ok) onOpenTerminal()
                            }
                        }
                    },
                    onOpenRemoteHome = { group ->
                        if (!group.profilePresent) {
                            viewModel.showNotice(context.getString(R.string.chat_drawer_remote_profile_unavailable))
                        } else {
                            onDismiss(); onOpenRemoteHome(group.serverId)
                        }
                    },
                    onWatchRemoteProjects = viewModel::watchRemoteProjects,
                    onDelete = viewModel::deleteSession,
                    onRename = viewModel::renameSession,
                    onCollapse = onCollapse,
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
    }

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
