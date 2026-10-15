package top.wkbin.taixu.ui.navigation

import org.koin.compose.viewmodel.koinViewModel
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import top.wkbin.taixu.ui.chat.ChatScreen
import top.wkbin.taixu.ui.chat.ChatViewModel
import top.wkbin.taixu.ui.components.MainDestination
import top.wkbin.taixu.ui.chat.ChatNavigationDrawer
import top.wkbin.taixu.ui.developer.DeveloperScreen
import top.wkbin.taixu.ui.developer.AdbLogcatScreen
import top.wkbin.taixu.ui.home.HomeScreen
import top.wkbin.taixu.ui.settings.AgentSettingsScreen
import top.wkbin.taixu.ui.settings.ModelEditorScreen
import top.wkbin.taixu.ui.settings.ModelProfilesScreen
import top.wkbin.taixu.ui.settings.LocalLlmScreen
import top.wkbin.taixu.ui.settings.SettingsScreen
import top.wkbin.taixu.ui.settings.SettingsViewModel
import top.wkbin.taixu.ui.iteration.CustomIterationScreen
import top.wkbin.taixu.plugin.ui.PluginPages
import top.wkbin.taixu.plugin.runtime.PluginController
import org.koin.compose.koinInject
import top.wkbin.taixu.ui.workspace.CodeEditorScreen
import top.wkbin.taixu.ui.workspace.WorkspaceExplorerScreen
import top.wkbin.taixu.ui.workspace.WorkspaceScreen
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import top.wkbin.taixu.core.common.navigation.AppNavigationTarget
import top.wkbin.taixu.core.common.navigation.GlobalNavigationBus
import top.wkbin.taixu.feature.a2uipoc.A2uiPocScreen
import top.wkbin.taixu.ui.browser.BrowserViewModel
import top.wkbin.taixu.ui.git.GitScreen
import top.wkbin.taixu.ui.settings.AboutCommunityScreen
import top.wkbin.taixu.ui.settings.AgentEcoSettingsScreen
import top.wkbin.taixu.ui.settings.AgentSettingsCategory
import top.wkbin.taixu.ui.settings.AppManagementScreen
import top.wkbin.taixu.ui.settings.AppearanceSettingsScreen
import top.wkbin.taixu.ui.settings.CcSwitchScreen
import top.wkbin.taixu.ui.settings.DistroManagementScreen
import top.wkbin.taixu.ui.settings.EnvironmentVariableSettingsScreen
import top.wkbin.taixu.ui.settings.FeatureHubScreen
import top.wkbin.taixu.ui.settings.FtpSettingsScreen
import top.wkbin.taixu.ui.settings.LinuxEnvironmentSettingsScreen
import top.wkbin.taixu.ui.settings.McpSettingsScreen
import top.wkbin.taixu.ui.settings.PhoneAgentSettingsScreen
import top.wkbin.taixu.ui.settings.QuickPhrasesScreen
import top.wkbin.taixu.ui.settings.SponsorScreen
import top.wkbin.taixu.ui.settings.SshSettingsScreen
import top.wkbin.taixu.ui.settings.StorageMountSettingsScreen
import top.wkbin.taixu.ui.settings.StorageUsageScreen
import top.wkbin.taixu.ui.settings.SystemDevSettingsScreen
import top.wkbin.taixu.ui.settings.ToolCenterScreen
import top.wkbin.taixu.ui.settings.ToolDetailScreen
import top.wkbin.taixu.ui.settings.ToolSelfHealingHelper
import top.wkbin.taixu.ui.settings.permission.PermissionGuideScreen
import top.wkbin.taixu.ui.settings.search.SettingsSearchScreen
import top.wkbin.taixu.ui.settings.search.SettingsSearchTarget
import top.wkbin.taixu.ui.settings.stats.StatsScreen
import top.wkbin.taixu.ui.workflow.WorkflowScreen
import top.wkbin.taixu.ui.workspace.WorkshopEnvironmentSettingsScreen
import top.wkbin.taixu.ui.workspace.WorkshopScriptEditorScreen
import top.wkbin.taixu.ui.workspace.WorkshopScriptType
import top.wkbin.taixu.ui.workspace.WorkshopSettingsScreen
import top.wkbin.taixu.ui.workspace.WorkshopSigningScreen

/** 太墟核心导航分发系统：采用 Navigation 3，为每个 Tab 独立维护持久回退栈与状态生命周期 */
@Composable
fun TaiXuNavHost(
    globalNavigationBus: GlobalNavigationBus? = null,
) {
    // Root tab entries are removed from composition when another tab becomes active. Keep the
    // conversation owner at the Activity scope so switching back to 智枢 does not rebuild Koin's
    // graph, restore the latest session, and restart its initialization skeleton on every visit.
    val chatViewModel: ChatViewModel = koinViewModel()
    val pluginPages: PluginPages = koinInject()
    val pluginPlatform: NativePluginPlatform = koinInject()
    val pluginController: PluginController = koinInject()
    // 浏览器引擎是全局单例：BrowserViewModel 同样挂到 Activity 作用域，
    // 使智枢内嵌浏览器面板与独立浏览器页共享同一份 tab/URL/共浏览状态。
    val browserViewModel: BrowserViewModel = koinViewModel()
    val browserUiState by browserViewModel.uiState.collectAsStateWithLifecycle()
    // SettingsViewModel owns dozens of eagerly shared DataStore/database streams and performs
    // repository initialization. Let the settings navigation graph share the Activity-scoped
    // instance instead of constructing that whole graph once for every Navigation3 entry.
    val settingsViewModel: SettingsViewModel = koinViewModel()
    val homeStack = rememberNavBackStack(HomeDestination)
    val agentStack = rememberNavBackStack(AgentDestination)
    val workspaceStack = rememberNavBackStack(WorkspaceDestination)
    val settingsStack = rememberNavBackStack(SettingsDestination)
    var pendingHealingTask by remember { mutableStateOf<HealingTask?>(null) }
    var selectedMain by rememberSaveable { mutableStateOf(MainDestination.Agent) }
    var showNavigation by rememberSaveable { mutableStateOf(false) }; var sidebarDocked by rememberSaveable { mutableStateOf(true) }
    var showNewSession by rememberSaveable { mutableStateOf(false) }
    /** Programmatic stack mutation (bus / workflow). */
    fun NavBackStack<NavKey>.pushRaw(destination: NavKey) {
        if (lastOrNull() != destination) add(destination)
    }

    val activeStack = when (selectedMain) {
        MainDestination.Home -> homeStack
        MainDestination.Agent -> agentStack
        MainDestination.Workspace -> workspaceStack
        MainDestination.Settings -> settingsStack
    }
    fun navigateMain(destination: MainDestination) {
        if (activeStack.openMainOverlay(destination)) return
        if (destination == MainDestination.Agent) while (agentStack.size > 1) agentStack.removeLastOrNull()
        if (destination == MainDestination.Settings) while (settingsStack.size > 1) settingsStack.removeLastOrNull()
        selectedMain = destination
    }

    LaunchedEffect(chatViewModel) {
        chatViewModel.workflowLaunchRequests.collect { request ->
            navigateMain(MainDestination.Agent)
            agentStack.pushRaw(WorkflowDestination(request.projectName, request.workflowId, request.initialVariables))
        }
    }

    LaunchedEffect(globalNavigationBus) {
        globalNavigationBus?.events?.collect { target ->
            when (target) {
                AppNavigationTarget.AdbLogcat -> {
                    selectedMain = MainDestination.Settings
                    if (settingsStack.lastOrNull() != AdbLogcatDestination) {
                        if (settingsStack.lastOrNull() == SettingsDestination) {
                            settingsStack.pushRaw(SystemDevSettingsDestination)
                        }
                        if (settingsStack.lastOrNull() == SystemDevSettingsDestination) {
                            settingsStack.pushRaw(AdbLogcatDestination)
                        } else if (settingsStack.lastOrNull() != AdbLogcatDestination) {
                            settingsStack.pushRaw(AdbLogcatDestination)
                        }
                    }
                    globalNavigationBus.clearLatest(target)
                }
                is AppNavigationTarget.WorkflowRun -> {
                    // 工作流通知点入：切到智枢栈并打开运行页
                    navigateMain(MainDestination.Agent)
                    agentStack.pushRaw(WorkflowDestination(executionId = target.executionId))
                    globalNavigationBus.clearLatest(target)
                }
                AppNavigationTarget.AgentSettings -> {
                    selectedMain = MainDestination.Settings
                    if (settingsStack.lastOrNull() != AgentSettingsDestination) {
                        settingsStack.pushRaw(AgentSettingsDestination)
                    }
                    globalNavigationBus.clearLatest(target)
                }
                is AppNavigationTarget.SharedText -> {
                    // 系统分享入口：切到智枢并预填输入框（不自动发送，由用户选择快捷指令或直接发送）
                    navigateMain(MainDestination.Agent)
                    chatViewModel.onSharedTextReceived(target.text)
                    globalNavigationBus.clearLatest(target)
                }
            }
        }
    }

    fun NavBackStack<NavKey>.push(from: NavKey, destination: NavKey) {
        if (lastOrNull() == from && lastOrNull() != destination) {
            add(destination)
        }
    }

    fun popBack() {
        if (activeStack.size > 1) activeStack.removeLastOrNull()
    }
    BackHandler(enabled = activeStack.size == 1 && selectedMain != MainDestination.Agent && !showNavigation && !showNewSession) {
        navigateMain(MainDestination.Agent)
    }

    @Composable
    fun GuardedEntry(
        destination: NavKey,
        content: @Composable () -> Unit,
    ) {
        val isActive = destination == activeStack.lastOrNull()
        Box(
            modifier = Modifier
                .fillMaxSize()
                .then(
                    if (isActive) Modifier
                    else Modifier.pointerInput(Unit) {
                        awaitPointerEventScope {
                            while (true) {
                                val event = awaitPointerEvent(PointerEventPass.Initial)
                                event.changes.forEach { it.consume() }
                            }
                        }
                    }
                )
        ) {
            content()
        }
    }

    val appEntryProvider: (NavKey) -> NavEntry<NavKey> = entryProvider {
            entry<HomeDestination> {
                GuardedEntry(HomeDestination) {
                    HomeScreen(
                        onNavigate = ::navigateMain, onBack = { if (activeStack.size > 1) popBack() else navigateMain(MainDestination.Agent) },
                        onOpenTerminal = { activeStack.push(HomeDestination, TerminalDestination()) },
                        onOpenToolCenter = { activeStack.push(HomeDestination, ToolCenterDestination) },
                    )
                }
            }
            entry<AgentDestination> {
                GuardedEntry(AgentDestination) {
                    LaunchedEffect(pendingHealingTask) {
                        pendingHealingTask?.let { task ->
                            chatViewModel.startHealingTask(task.title, task.prompt)
                            pendingHealingTask = null
                        }
                    }
                    ChatScreen(
                        viewModel = chatViewModel,
                        onNavigate = ::navigateMain,
                        onOpenNavigation = { showNavigation = true }, onInstallMcp = { activeStack.pushRaw(ToolCenterDestination) }, onCreateModelProfile = { agentStack.pushRaw(ModelEditorDestination()) },
                        // 内嵌终端面板非独立导航节点，无返回目标：隐藏顶栏返回箭头，避免点击无反馈
                        terminalPane = { project -> TerminalPluginPage(pluginPages, project = project, embedded = true, onBack = {}) },
                        // 内嵌浏览器面板：与独立浏览器页共享 Activity 级 BrowserViewModel，
                        // 手机端左右滑动切换对话/浏览器，宽屏双栏可切"终端/浏览器"
                        browserPane = { onExit -> BrowserPluginPage(pluginPages, embedded = true, onBack = onExit) },
                        browserActivityTick = browserUiState.activityTick,
                        browserBackPressed = { browserPluginBack(pluginController, browserViewModel) },
                        onOpenFile = { projectName, relativePath ->
                            agentStack.push(AgentDestination, CodeEditorDestination(projectName, relativePath))
                        },
                        onOpenRepository = { projectName ->
                            agentStack.push(AgentDestination, GitRepositoryDestination(projectName))
                        },
                    )
                }
            }
            entry<WorkspaceDestination> {
                GuardedEntry(WorkspaceDestination) {
                    WorkspaceScreen(
                        onNavigate = ::navigateMain, onBack = { if (activeStack.size > 1) popBack() else navigateMain(MainDestination.Agent) },
                        onOpenExplorer = { projectName -> activeStack.push(WorkspaceDestination, WorkspaceExplorerDestination(projectName)) },
                        onOpenTerminal = { project -> activeStack.push(WorkspaceDestination, TerminalDestination(project = project)) },
                        onOpenToolCenter = { activeStack.push(WorkspaceDestination, ToolCenterDestination) },
                        onOpenWorkshopSettings = { activeStack.push(WorkspaceDestination, WorkshopSettingsDestination) },
                        onOpenWorkflows = { projectName -> activeStack.push(WorkspaceDestination, WorkflowDestination(projectName)) },
                    )
                }
            }
            entry<WorkflowDestination> { destination ->
                GuardedEntry(destination) {
                    WorkflowScreen(
                        projectName = destination.projectName,
                        initialWorkflowId = destination.workflowId,
                        initialVariables = destination.initialVariables,
                        initialExecutionId = destination.executionId,
                        onBack = ::popBack,
                    )
                }
            }
            entry<WorkshopSettingsDestination> {
                GuardedEntry(WorkshopSettingsDestination) {
                    WorkshopSettingsScreen(
                        onBack = ::popBack,
                        onOpenEnvironment = { activeStack.push(WorkshopSettingsDestination, WorkshopEnvironmentSettingsDestination) },
                        onOpenSigning = { activeStack.push(WorkshopSettingsDestination, WorkshopSigningSettingsDestination) },
                        onEditScript = { type -> activeStack.push(WorkshopSettingsDestination, WorkshopScriptEditorDestination(type.name)) },
                    )
                }
            }
            entry<WorkshopEnvironmentSettingsDestination> {
                GuardedEntry(WorkshopEnvironmentSettingsDestination) {
                    WorkshopEnvironmentSettingsScreen(onBack = ::popBack)
                }
            }
            entry<WorkshopSigningSettingsDestination> {
                GuardedEntry(WorkshopSigningSettingsDestination) {
                    WorkshopSigningScreen(onBack = ::popBack)
                }
            }
            entry<WorkshopScriptEditorDestination> { destination ->
                GuardedEntry(destination) {
                    WorkshopScriptEditorScreen(
                        type = WorkshopScriptType.valueOf(destination.type),
                        onBack = ::popBack,
                    )
                }
            }
            entry<WorkspaceExplorerDestination> { destination ->
                GuardedEntry(destination) {
                    WorkspaceExplorerScreen(
                        projectName = destination.projectName,
                        initialPath = destination.initialPath,
                        onBack = ::popBack,
                        onOpenFile = { relativePath ->
                            activeStack.push(destination, CodeEditorDestination(destination.projectName, relativePath))
                        },
                        onOpenTerminal = { project ->
                            activeStack.push(destination, TerminalDestination(project = project))
                        },
                    )
                }
            }
            entry<CodeEditorDestination> { destination ->
                GuardedEntry(destination) {
                    CodeEditorScreen(
                        projectName = destination.projectName,
                        relativePath = destination.relativePath,
                        onBack = ::popBack,
                    )
                }
            }
            entry<SettingsDestination> {
                GuardedEntry(SettingsDestination) {
                    SettingsScreen(
                        onNavigate = ::navigateMain,
                        onOpenAgentEco = { settingsStack.push(SettingsDestination, AgentEcoSettingsDestination) },
                        onOpenLinuxEnv = { settingsStack.push(SettingsDestination, LinuxEnvSettingsDestination) },
                        onOpenAppearance = { settingsStack.push(SettingsDestination, AppearanceSettingsDestination) },
                        onOpenSystemDev = { settingsStack.push(SettingsDestination, SystemDevSettingsDestination) },
                        onOpenAboutCommunity = { settingsStack.push(SettingsDestination, AboutCommunityDestination) },
                        onOpenSearch = { settingsStack.push(SettingsDestination, SettingsSearchDestination) },
                        onOpenFeatureHub = { settingsStack.push(SettingsDestination, FeatureHubDestination) },
                        viewModel = settingsViewModel,
                    )
                }
            }
            entry<SettingsSearchDestination> {
                GuardedEntry(SettingsSearchDestination) {
                    SettingsSearchScreen(
                        onBack = ::popBack,
                        onNavigateToTarget = { target ->
                            when (target) {
                                SettingsSearchTarget.MODEL_PROFILES -> settingsStack.push(SettingsSearchDestination, ModelProfilesDestination)
                                SettingsSearchTarget.MODEL_EDITOR_NEW -> settingsStack.push(SettingsSearchDestination, ModelEditorDestination())
                                SettingsSearchTarget.LOCAL_LLM -> settingsStack.push(SettingsSearchDestination, LocalLlmDestination)
                                SettingsSearchTarget.QUICK_PHRASES -> settingsStack.push(SettingsSearchDestination, QuickPhrasesDestination)
                                SettingsSearchTarget.STATS -> settingsStack.push(SettingsSearchDestination, StatsDestination)
                                SettingsSearchTarget.TOOL_CENTER -> settingsStack.push(SettingsSearchDestination, ToolCenterDestination)
                                SettingsSearchTarget.CC_SWITCH -> settingsStack.push(SettingsSearchDestination, CcSwitchDestination)
                                SettingsSearchTarget.AGENT_EXECUTION -> settingsStack.push(SettingsSearchDestination, AgentSettingsDestination)
                                SettingsSearchTarget.AGENT_SUBAGENTS -> settingsStack.push(SettingsSearchDestination, AgentSubagentSettingsDestination)
                                SettingsSearchTarget.AGENT_SKILLS -> settingsStack.push(SettingsSearchDestination, AgentSkillSettingsDestination)
                                SettingsSearchTarget.MCP_SETTINGS -> settingsStack.push(SettingsSearchDestination, McpSettingsDestination)
                                SettingsSearchTarget.DISTRO_MANAGEMENT -> settingsStack.push(SettingsSearchDestination, DistroManagementDestination)
                                SettingsSearchTarget.STORAGE_USAGE -> settingsStack.push(SettingsSearchDestination, StorageUsageDestination)
                                SettingsSearchTarget.STORAGE_MOUNTS -> settingsStack.push(SettingsSearchDestination, StorageMountSettingsDestination)
                                SettingsSearchTarget.ENV_VARS -> settingsStack.push(SettingsSearchDestination, EnvironmentVariableSettingsDestination)
                                SettingsSearchTarget.SSH_SETTINGS -> settingsStack.push(SettingsSearchDestination, SshSettingsDestination)
                                SettingsSearchTarget.FTP_SETTINGS -> settingsStack.push(SettingsSearchDestination, FtpSettingsDestination)
                                SettingsSearchTarget.WEB_CHAT,
                                SettingsSearchTarget.PRIVILEGE_MODE -> settingsStack.push(SettingsSearchDestination, LinuxEnvSettingsDestination)
                                SettingsSearchTarget.APP_MANAGEMENT -> settingsStack.push(SettingsSearchDestination, AppManagementDestination)
                                SettingsSearchTarget.APPEARANCE_SETTINGS,
                                SettingsSearchTarget.THEME_MODE,
                                SettingsSearchTarget.DYNAMIC_COLOR,
                                SettingsSearchTarget.FONT_SCALE,
                                SettingsSearchTarget.TERMINAL_SETTINGS,
                                SettingsSearchTarget.LANGUAGE_SETTINGS -> settingsStack.push(SettingsSearchDestination, AppearanceSettingsDestination)
                                SettingsSearchTarget.BATTERY_OPTIMIZATION,
                                SettingsSearchTarget.PHANTOM_PROCESS -> settingsStack.push(SettingsSearchDestination, SystemDevSettingsDestination)
                                SettingsSearchTarget.DEVELOPER_OPTIONS -> settingsStack.push(SettingsSearchDestination, DeveloperDestination)
                                SettingsSearchTarget.ADB_LOGCAT -> settingsStack.push(SettingsSearchDestination, AdbLogcatDestination)
                                SettingsSearchTarget.CUSTOM_ITERATION -> settingsStack.push(SettingsSearchDestination, CustomIterationDestination)
                                SettingsSearchTarget.PERMISSION_GUIDE -> settingsStack.push(SettingsSearchDestination, PermissionGuideDestination)
                                SettingsSearchTarget.WORKSHOP_SETTINGS -> {
                                    navigateMain(MainDestination.Workspace)
                                    activeStack.pushRaw(WorkshopSettingsDestination)
                                }
                                SettingsSearchTarget.WORKSHOP_ENVIRONMENT -> {
                                    navigateMain(MainDestination.Workspace)
                                    activeStack.pushRaw(WorkshopSettingsDestination)
                                    activeStack.pushRaw(WorkshopEnvironmentSettingsDestination)
                                }
                                SettingsSearchTarget.WORKSHOP_SIGNING -> {
                                    navigateMain(MainDestination.Workspace)
                                    activeStack.pushRaw(WorkshopSettingsDestination)
                                    activeStack.pushRaw(WorkshopSigningSettingsDestination)
                                }
                                SettingsSearchTarget.WORKFLOWS -> {
                                    navigateMain(MainDestination.Workspace)
                                    activeStack.pushRaw(WorkflowDestination())
                                }
                                SettingsSearchTarget.NAV_HOME ->
                                    navigateMain(MainDestination.Home)
                                SettingsSearchTarget.NAV_AGENT_CHAT ->
                                    navigateMain(MainDestination.Agent)
                                SettingsSearchTarget.NAV_WORKSPACE ->
                                    navigateMain(MainDestination.Workspace)
                                SettingsSearchTarget.NAV_TERMINAL -> {
                                    activeStack.pushRaw(TerminalDestination())
                                }
                                SettingsSearchTarget.NAV_BROWSER -> {
                                    activeStack.pushRaw(BrowserDestination)
                                }
                                SettingsSearchTarget.ABOUT_COMMUNITY,
                                SettingsSearchTarget.ABOUT_UPDATE -> settingsStack.push(SettingsSearchDestination, AboutCommunityDestination)
                                SettingsSearchTarget.ABOUT_SPONSOR -> settingsStack.push(SettingsSearchDestination, SponsorDestination)
                            }
                        }
                    )
                }
            }
            entry<AppearanceSettingsDestination> {
                GuardedEntry(AppearanceSettingsDestination) {
                    AppearanceSettingsScreen(
                        onBack = ::popBack,
                        viewModel = settingsViewModel,
                    )
                }
            }
            entry<AgentEcoSettingsDestination> {
                GuardedEntry(AgentEcoSettingsDestination) {
                    AgentEcoSettingsScreen(
                        onBack = ::popBack,
                        onOpenModelProfiles = { settingsStack.push(AgentEcoSettingsDestination, ModelProfilesDestination) },
                        onOpenLocalLlm = { settingsStack.push(AgentEcoSettingsDestination, LocalLlmDestination) },
                        onOpenCcSwitch = { settingsStack.push(AgentEcoSettingsDestination, CcSwitchDestination) },
                        onOpenToolCenter = { settingsStack.push(AgentEcoSettingsDestination, ToolCenterDestination) },
                        onOpenAgentSettings = { settingsStack.push(AgentEcoSettingsDestination, AgentSettingsDestination) },
                        onOpenSubagentSettings = { settingsStack.push(AgentEcoSettingsDestination, AgentSubagentSettingsDestination) },
                        onOpenSkillSettings = { settingsStack.push(AgentEcoSettingsDestination, AgentSkillSettingsDestination) },
                        onOpenMcpSettings = { settingsStack.push(AgentEcoSettingsDestination, McpSettingsDestination) },
                        onOpenQuickPhrases = { settingsStack.push(AgentEcoSettingsDestination, QuickPhrasesDestination) },
                        onOpenStats = { settingsStack.push(AgentEcoSettingsDestination, StatsDestination) },
                        onOpenPhoneAgent = { settingsStack.push(AgentEcoSettingsDestination, PhoneAgentSettingsDestination) },
                        viewModel = settingsViewModel,
                    )
                }
            }
            entry<LinuxEnvSettingsDestination> {
                GuardedEntry(LinuxEnvSettingsDestination) {
                    LinuxEnvironmentSettingsScreen(
                        onBack = ::popBack,
                        onOpenDistroManagement = { settingsStack.push(LinuxEnvSettingsDestination, DistroManagementDestination) },
                        onOpenStorageMounts = { settingsStack.push(LinuxEnvSettingsDestination, StorageMountSettingsDestination) },
                        onOpenStorageUsage = { settingsStack.push(LinuxEnvSettingsDestination, StorageUsageDestination) },
                        onOpenAppManagement = { settingsStack.push(LinuxEnvSettingsDestination, AppManagementDestination) },
                        onOpenEnvironmentVariables = { settingsStack.push(LinuxEnvSettingsDestination, EnvironmentVariableSettingsDestination) },
                        onOpenSshSettings = { settingsStack.push(LinuxEnvSettingsDestination, SshSettingsDestination) },
                        onOpenFtpSettings = { settingsStack.push(LinuxEnvSettingsDestination, FtpSettingsDestination) },
                        viewModel = settingsViewModel,
                    )
                }
            }
            entry<SystemDevSettingsDestination> {
                GuardedEntry(SystemDevSettingsDestination) {
                    SystemDevSettingsScreen(
                        onBack = ::popBack,
                        onOpenDeveloper = { settingsStack.push(SystemDevSettingsDestination, DeveloperDestination) },
                        onOpenAdbLogcat = { settingsStack.push(SystemDevSettingsDestination, AdbLogcatDestination) },
                        onOpenCustomIteration = { settingsStack.push(SystemDevSettingsDestination, CustomIterationDestination) },
                        onOpenPermissionGuide = { settingsStack.push(SystemDevSettingsDestination, PermissionGuideDestination) },
                        viewModel = settingsViewModel,
                    )
                }
            }
            entry<PermissionGuideDestination> {
                GuardedEntry(PermissionGuideDestination) {
                    PermissionGuideScreen(
                        onBack = ::popBack,
                    )
                }
            }
            entry<AboutCommunityDestination> {
                GuardedEntry(AboutCommunityDestination) {
                    AboutCommunityScreen(
                        onBack = ::popBack,
                        onOpenSponsor = { settingsStack.push(AboutCommunityDestination, SponsorDestination) },
                        viewModel = settingsViewModel,
                    )
                }
            }
            entry<SponsorDestination> {
                GuardedEntry(SponsorDestination) {
                    SponsorScreen(onBack = ::popBack)
                }
            }
            entry<DistroManagementDestination> {
                GuardedEntry(DistroManagementDestination) {
                    DistroManagementScreen(
                        onBack = ::popBack,
                        viewModel = settingsViewModel,
                    )
                }
            }
            entry<AgentSettingsDestination> {
                GuardedEntry(AgentSettingsDestination) {
                    AgentSettingsScreen(
                        onBack = ::popBack,
                        category = AgentSettingsCategory.EXECUTION,
                        viewModel = settingsViewModel,
                    )
                }
            }
            entry<AgentSubagentSettingsDestination> {
                GuardedEntry(AgentSubagentSettingsDestination) {
                    AgentSettingsScreen(onBack = ::popBack, category = AgentSettingsCategory.SUBAGENTS, viewModel = settingsViewModel)
                }
            }
            entry<AgentSkillSettingsDestination> {
                GuardedEntry(AgentSkillSettingsDestination) {
                    AgentSettingsScreen(onBack = ::popBack, category = AgentSettingsCategory.SKILLS, viewModel = settingsViewModel)
                }
            }
            entry<McpSettingsDestination> {
                GuardedEntry(McpSettingsDestination) {
                    McpSettingsScreen(
                        onBack = ::popBack,
                        viewModel = settingsViewModel,
                    )
                }
            }
            entry<ToolCenterDestination> {
                GuardedEntry(ToolCenterDestination) {
                    ToolCenterScreen(
                        onBack = ::popBack,
                        onLaunchPty = { toolId -> activeStack.push(ToolCenterDestination, TerminalDestination(toolId = toolId)) },
                        onOpenToolDetail = { toolId -> activeStack.push(ToolCenterDestination, ToolDetailDestination(toolId = toolId)) },
                        onStartAiHealing = { toolId, toolName, logs ->
                            val prompt = ToolSelfHealingHelper.buildHealingPrompt(toolId, toolName, logs)
                            pendingHealingTask = HealingTask("🔧 自愈: $toolName", prompt)
                            navigateMain(MainDestination.Agent)
                        },
                    )
                }
            }
            entry<CcSwitchDestination> {
                GuardedEntry(CcSwitchDestination) {
                    val context = androidx.compose.ui.platform.LocalContext.current
                    CcSwitchScreen(
                        onBack = ::popBack,
                        onLaunchTerminal = { executable -> activeStack.push(CcSwitchDestination, TerminalDestination(toolId = executable)) },
                        onOpenBrowser = { url ->
                            val targetUrl = url.ifBlank { "http://127.0.0.1:19870" }
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(targetUrl)).apply {
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                            runCatching {
                                context.startActivity(intent)
                            }.onFailure {
                                Toast.makeText(context, "无法唤起外部浏览器: ${it.localizedMessage}", Toast.LENGTH_SHORT).show()
                            }
                        },
                    )
                }
            }
            entry<ToolDetailDestination> { destination ->
                GuardedEntry(destination) {
                    ToolDetailScreen(
                        toolId = destination.toolId,
                        onBack = ::popBack,
                        onLaunchTerminal = { toolId -> activeStack.push(destination, TerminalDestination(toolId = toolId)) },
                        onStartAiHealing = { toolId, toolName, logs ->
                            val prompt = ToolSelfHealingHelper.buildHealingPrompt(toolId, toolName, logs)
                            pendingHealingTask = HealingTask("🔧 自愈: $toolName", prompt)
                            navigateMain(MainDestination.Agent)
                        },
                    )
                }
            }
            entry<StorageMountSettingsDestination> {
                GuardedEntry(StorageMountSettingsDestination) {
                    StorageMountSettingsScreen(
                        onBack = ::popBack,
                        viewModel = settingsViewModel,
                    )
                }
            }
            entry<StorageUsageDestination> {
                GuardedEntry(StorageUsageDestination) {
                    StorageUsageScreen(onBack = ::popBack)
                }
            }
            entry<AppManagementDestination> {
                GuardedEntry(AppManagementDestination) {
                    AppManagementScreen(onBack = ::popBack)
                }
            }
            entry<EnvironmentVariableSettingsDestination> {
                GuardedEntry(EnvironmentVariableSettingsDestination) {
                    EnvironmentVariableSettingsScreen(
                        onBack = ::popBack,
                        viewModel = settingsViewModel,
                    )
                }
            }
            entry<SshSettingsDestination> {
                GuardedEntry(SshSettingsDestination) {
                    SshSettingsScreen(onBack = ::popBack)
                }
            }
            entry<FtpSettingsDestination> {
                GuardedEntry(FtpSettingsDestination) {
                    FtpSettingsScreen(onBack = ::popBack)
                }
            }
            entry<ModelProfilesDestination> {
                GuardedEntry(ModelProfilesDestination) {
                    ModelProfilesScreen(
                        onBack = ::popBack,
                        onCreate = { settingsStack.push(ModelProfilesDestination, ModelEditorDestination()) },
                        onEdit = { modelId -> settingsStack.push(ModelProfilesDestination, ModelEditorDestination(modelId)) },
                        viewModel = settingsViewModel,
                    )
                }
            }
            entry<LocalLlmDestination> {
                GuardedEntry(LocalLlmDestination) {
                    LocalLlmScreen(
                        onBack = ::popBack,
                        onOpenEngine = { settingsStack.push(LocalLlmDestination, ToolDetailDestination("llama-cpp")) },
                    )
                }
            }
            entry<PhoneAgentSettingsDestination> {
                GuardedEntry(PhoneAgentSettingsDestination) {
                    PhoneAgentSettingsScreen(onBack = ::popBack)
                }
            }
            entry<ModelEditorDestination> { destination ->
                GuardedEntry(destination) {
                    ModelEditorScreen(
                        modelId = destination.modelId,
                        onBack = ::popBack,
                        onSaved = ::popBack,
                        viewModel = settingsViewModel,
                    )
                }
            }
            entry<QuickPhrasesDestination> {
                GuardedEntry(QuickPhrasesDestination) {
                    QuickPhrasesScreen(
                        onBack = ::popBack,
                        viewModel = settingsViewModel,
                    )
                }
            }
            entry<StatsDestination> {
                GuardedEntry(StatsDestination) {
                    StatsScreen(onBack = ::popBack)
                }
            }
            entry<DeveloperDestination> {
                GuardedEntry(DeveloperDestination) {
                    DeveloperScreen(onBack = ::popBack)
                }
            }
            entry<AdbLogcatDestination> {
                GuardedEntry(AdbLogcatDestination) {
                    AdbLogcatScreen(onBack = ::popBack)
                }
            }
            entry<A2uiPocDestination> {
                GuardedEntry(A2uiPocDestination) {
                    A2uiPocScreen(onBack = ::popBack)
                }
            }
            entry<FeatureHubDestination> {
                GuardedEntry(FeatureHubDestination) {
                    FeatureHubScreen(
                        onBack = ::popBack,
                        onOpenCustomIteration = { settingsStack.push(FeatureHubDestination, CustomIterationDestination) },
                        onStartRoundtable = { pendingHealingTask = HealingTask(AgentPresets.ROUNDTABLE_TITLE, AgentPresets.ROUNDTABLE_PROMPT); navigateMain(MainDestination.Agent) },
                        onOpenA2uiPoc = { settingsStack.push(FeatureHubDestination, A2uiPocDestination) },
                    )
                }
            }
            entry<CustomIterationDestination> {
                GuardedEntry(CustomIterationDestination) {
                    CustomIterationScreen(
                        onBack = ::popBack,
                        onNavigateToChat = { prompt ->
                            pendingHealingTask = HealingTask("🚀 自定义迭代", prompt)
                            navigateMain(MainDestination.Agent)
                        },
                    )
                }
            }
            entry<PluginManagerDestination> {
                GuardedEntry(PluginManagerDestination) { ManagedPluginsScreen(pluginPlatform, pluginController, { activeStack.pushRaw(PluginPageDestination(it.pluginId, it.extensionId)) }, { activeStack.pushRaw(ToolDetailDestination(it)) }, ::popBack) }
            }
            entry<PluginPageDestination> { destination ->
                GuardedEntry(destination) { RegisteredPluginPage(pluginPages, destination, ::popBack) }
            }
            entry<TerminalDestination> { destination ->
                GuardedEntry(destination) { TerminalPluginPage(pluginPages, project = destination.project, onBack = ::popBack) }
            }
            entry<BrowserDestination> {
                GuardedEntry(BrowserDestination) { BrowserPluginPage(pluginPages, onBack = ::popBack) }
            }
            entry<GitRepositoryDestination> { destination ->
                GuardedEntry(destination) {
                    GitScreen(projectName = destination.projectName, onBack = ::popBack)
                }
            }
    }

    // Hoist decorators so tab switches (key below) do not drop entry Saveable/ViewModel state.
    // Explicit <NavKey>: outside NavDisplay's parameter context, listOf cannot infer T.
    val entryDecorators = listOf(rememberSaveableStateHolderNavEntryDecorator<NavKey>(), rememberViewModelStoreNavEntryDecorator<NavKey>())
    PluginNavigationShell(pluginPlatform, pluginController, browserViewModel, selectedPage = selectedPluginPage(activeStack.lastOrNull()),
        onOpen = { showNavigation = false; activeStack.pushRaw(PluginPageDestination(it.pluginId, it.extensionId)) },
        onOpenManager = { showNavigation = false; activeStack.pushRaw(PluginManagerDestination) }, onOpenNavigation = { showNavigation = true },
    ) {
        ChatNavigationDrawer(
            viewModel = chatViewModel, visible = showNavigation, onDismiss = { showNavigation = false },
            onOpen = { showNavigation = true }, docked = sidebarDocked, onDockedChange = { sidebarDocked = it }, newSessionVisible = showNewSession, selected = when (activeStack.lastOrNull()) { WorkspaceDestination -> MainDestination.Workspace; HomeDestination -> MainDestination.Home; else -> selectedMain },
            onNewSession = { showNewSession = true }, onDismissNewSession = { showNewSession = false },
            onNavigate = ::navigateMain, onOpenTerminal = { activeStack.pushRaw(TerminalDestination()) },
            onOpenTools = { activeStack.pushRaw(ToolCenterDestination) },
        ) {
            Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                key(selectedMain) {
                    NavDisplay(
                        backStack = activeStack, modifier = Modifier.fillMaxSize(), onBack = ::popBack,
                        entryDecorators = entryDecorators, entryProvider = appEntryProvider,
                    )
                }
            }
        }
    }
}

private data class HealingTask(val title: String, val prompt: String)
