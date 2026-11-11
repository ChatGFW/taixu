package top.wkbin.taixu.ui.chat

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.compose.koinInject
import top.wkbin.taixu.core.datastore.AppearancePreferences
import top.wkbin.taixu.core.model.ChatTopToolbarAction
import top.wkbin.taixu.core.model.RunMode
import top.wkbin.taixu.feature.chat.R
import top.wkbin.taixu.feature.components.R as ComponentsR
import top.wkbin.taixu.harness.events.HarnessEvent
import top.wkbin.taixu.harness.session.ConversationBranch
import top.wkbin.taixu.ui.chat.floating.rememberFloatingChatLauncher
import top.wkbin.taixu.ui.components.*
import top.wkbin.taixu.ui.miuix.RuntimeTheme
import top.yukonga.miuix.kmp.basic.Text

/** Floating navigation and actions; advanced controls unfold on demand. */
@Composable
internal fun ChatTopBar(
    runMode: RunMode, currentBranch: ConversationBranch?,
    runtimeEvents: List<HarnessEvent>, running: Boolean,
    onOpenSessions: () -> Unit, onOpenBots: () -> Unit, onOpenWorkshop: () -> Unit,
    onOpenRunModes: () -> Unit, onOpenBranches: () -> Unit,
    onOpenRuntime: () -> Unit, onOpenBrowser: (() -> Unit)? = null, browserHighlight: Boolean = false,
    onOpenRepository: (() -> Unit)? = null, repositoryHighlight: Boolean = false,
    toolchainHighlight: Boolean = false, onOpenToolchain: () -> Unit,
    onOpenReasoning: (() -> Unit)? = null, companion: ChatCompanionToggle? = null,
    speech: ChatSpeechToggle? = null,
    onOpenMemory: () -> Unit = {},
    onOpenOutline: () -> Unit = {},
    onExportMarkdown: (() -> Unit)? = null,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val openFloatingChat = rememberFloatingChatLauncher()
    val appearance = previewOrNull { koinInject<AppearancePreferences>() }
    val pinned by appearance?.chatTopToolbarPinned
        ?.collectAsStateWithLifecycle(ChatTopToolbarAction.DEFAULT_PINNED)
        ?: previewStateOf(ChatTopToolbarAction.DEFAULT_PINNED)
    val pinnedSet = remember(pinned) { pinned.toSet() }
    val toolbarEntries = rememberChatTopToolbarEntries(
        pinned = pinned,
        runMode = runMode,
        onOpenBots = onOpenBots,
        onOpenWorkshop = onOpenWorkshop,
        onOpenRunModes = onOpenRunModes,
        onOpenBranches = onOpenBranches,
        onOpenRuntime = onOpenRuntime,
        onOpenToolchain = onOpenToolchain,
        toolchainHighlight = toolchainHighlight,
        onOpenBrowser = onOpenBrowser,
        browserHighlight = browserHighlight,
        onOpenRepository = onOpenRepository,
        repositoryHighlight = repositoryHighlight,
        onOpenReasoning = onOpenReasoning,
        onOpenMemory = onOpenMemory,
        onOpenOutline = onOpenOutline,
        onExportMarkdown = onExportMarkdown,
        onFloatingChat = openFloatingChat,
        speech = speech,
    )
    Row(
        Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        if (!LocalRuntimeWidthClass.current.hasPersistentNavigation) {
            RuntimeFloatingToolbar {
                RuntimeIconButton(onClick = onOpenSessions, modifier = Modifier.size(40.dp),
                    contentDescription = stringResource(R.string.chat_open_session_list)) {
                    RuntimeIcon(RuntimeIconName.Menu, Modifier.size(22.dp))
                }
            }
        } else Spacer(Modifier.width(48.dp))
        RuntimeFloatingToolbar {
            Row(verticalAlignment = Alignment.CenterVertically) {
                toolbarEntries.forEach { entry -> ChatTopToolbarButton(entry) }
                RuntimeIconButton(onClick = { expanded = true }, modifier = Modifier.size(40.dp),
                    contentDescription = stringResource(R.string.chat_workbench_controls)) {
                    RuntimeIcon(RuntimeIconName.More, Modifier.size(22.dp), RuntimeTheme.colorScheme.onSurface)
                }
            }
        }
    }
    if (expanded) {
        val closeThen: (() -> Unit) -> () -> Unit = { action -> { expanded = false; action() } }
        PlainBackdropModalBottomSheet(onDismissRequest = { expanded = false }) {
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp).padding(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.chat_workbench_controls), style = RuntimeTheme.typography.titleLarge)
                companion?.let { toggle ->
                    Row {
                        RuntimeIconButton(onClick = closeThen(toggle.onToggle),
                            contentDescription = stringResource(if (toggle.open) ComponentsR.string.components_companion_collapse
                                else ComponentsR.string.components_companion_open)) {
                            RuntimeIcon(RuntimeIconName.PanelRightOpen, Modifier.size(24.dp))
                        }
                    }
                }
                val sheetBrowser = onOpenBrowser.takeUnless { ChatTopToolbarAction.BROWSER in pinnedSet }?.let(closeThen)
                val sheetRepository = onOpenRepository.takeUnless { ChatTopToolbarAction.REPOSITORY in pinnedSet }?.let(closeThen)
                ChatWorkspaceBar(
                    onOpenBrowser = sheetBrowser, browserHighlight = browserHighlight,
                    onOpenRepository = sheetRepository, repositoryHighlight = repositoryHighlight)
                val showSessionNav = ChatTopToolbarAction.BOTS !in pinnedSet ||
                    ChatTopToolbarAction.WORKSPACE !in pinnedSet ||
                    ChatTopToolbarAction.MEMORY !in pinnedSet ||
                    ChatTopToolbarAction.OUTLINE !in pinnedSet ||
                    (ChatTopToolbarAction.EXPORT_MARKDOWN !in pinnedSet && onExportMarkdown != null)
                if (showSessionNav) {
                    Column {
                        RuntimeSectionTitle(stringResource(R.string.chat_workbench_session))
                        RuntimeCard(containerColor = RuntimeTheme.colorScheme.surfaceContainerHigh, contentPadding = PaddingValues(0.dp)) {
                            if (ChatTopToolbarAction.BOTS !in pinnedSet) {
                                WorkbenchRow(MainDestination.Bots.icon, stringResource(MainDestination.Bots.labelRes), closeThen(onOpenBots))
                            }
                            if (ChatTopToolbarAction.WORKSPACE !in pinnedSet) {
                                WorkbenchRow(MainDestination.Workspace.icon, stringResource(MainDestination.Workspace.labelRes), closeThen(onOpenWorkshop))
                            }
                            if (ChatTopToolbarAction.MEMORY !in pinnedSet) {
                                WorkbenchRow(RuntimeIconName.Brain, stringResource(R.string.chat_memory_sheet_title), closeThen(onOpenMemory))
                            }
                            if (ChatTopToolbarAction.OUTLINE !in pinnedSet) {
                                WorkbenchRow(RuntimeIconName.List, stringResource(R.string.chat_outline_title), closeThen(onOpenOutline))
                            }
                            if (ChatTopToolbarAction.EXPORT_MARKDOWN !in pinnedSet) {
                                onExportMarkdown?.let { handler ->
                                    WorkbenchRow(RuntimeIconName.Save, stringResource(R.string.chat_export_markdown), closeThen(handler))
                                }
                            }
                        }
                    }
                }
                ChatWorkbenchControls(
                    runMode, currentBranch, runtimeEvents, running,
                    closeThen(onOpenRunModes), closeThen(onOpenBranches),
                    closeThen(onOpenRuntime), closeThen(onOpenToolchain), toolchainHighlight,
                    onReasoning = onOpenReasoning.takeUnless { ChatTopToolbarAction.REASONING in pinnedSet }?.let(closeThen),
                    onFloatingChat = openFloatingChat.takeUnless { ChatTopToolbarAction.FLOATING_CHAT in pinnedSet }?.let(closeThen),
                    speech = speech.takeUnless { ChatTopToolbarAction.SPEECH in pinnedSet }
                        ?.let { toggle -> toggle.copy(onToggle = closeThen(toggle.onToggle)) },
                    hideRunMode = ChatTopToolbarAction.RUN_MODE in pinnedSet,
                    hideBranches = ChatTopToolbarAction.BRANCHES in pinnedSet,
                    hideRuntime = ChatTopToolbarAction.RUNTIME in pinnedSet,
                    hideToolchain = ChatTopToolbarAction.TOOLCHAIN in pinnedSet,
                )
            }
        }
    }
}

@Composable
private fun WorkbenchRow(icon: RuntimeIconName, title: String, onClick: () -> Unit) {
    top.wkbin.taixu.ui.components.RuntimeArrowPreference(
        title = title,
        startAction = { RuntimeIcon(icon, Modifier.padding(end = 16.dp).size(24.dp), RuntimeTheme.colorScheme.onSurfaceVariant) },
        onClick = onClick,
    )
}
