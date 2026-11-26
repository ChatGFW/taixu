package top.wkbin.taixu.ui.chat

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.compose.koinInject
import top.wkbin.taixu.core.datastore.AppearancePreferences
import top.wkbin.taixu.core.model.ChatTopToolbarAction
import top.wkbin.taixu.core.model.RunMode
import top.wkbin.taixu.feature.chat.R
import top.wkbin.taixu.harness.events.HarnessEvent
import top.wkbin.taixu.harness.session.ConversationBranch
import top.wkbin.taixu.ui.chat.floating.rememberFloatingChatLauncher
import top.wkbin.taixu.ui.components.*
import top.wkbin.taixu.ui.miuix.RuntimeTheme

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
    surface: ChatSurfaceSwitch? = null,
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
    val phoneChrome = surface?.phoneOpen == true
    Row(
        Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        // 手机页：去掉左侧抽屉按钮，避免与虚拟屏手势抢触控。
        if (phoneChrome) {
            Spacer(Modifier.width(48.dp))
        } else if (!LocalRuntimeWidthClass.current.hasPersistentNavigation) {
            RuntimeFloatingToolbar {
                RuntimeIconButton(onClick = onOpenSessions, modifier = Modifier.size(40.dp),
                    contentDescription = stringResource(R.string.chat_open_session_list)) {
                    RuntimeIcon(RuntimeIconName.Menu, Modifier.size(22.dp))
                }
            }
        } else Spacer(Modifier.width(48.dp))
        // 分身 / 手机切换占用两侧操作之间的空间。
        Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
            if (surface != null) {
                ChatSurfacePill(
                    phoneOpen = surface.phoneOpen,
                    onSelectPhone = surface.onSelectPhone,
                )
            }
        }
        RuntimeFloatingToolbar {
            Row(verticalAlignment = Alignment.CenterVertically) {
                when {
                    phoneChrome -> {
                        // 手机页：右侧只留全屏。
                        val onFullscreen = surface?.onFullscreen
                        if (onFullscreen != null) {
                            RuntimeIconButton(
                                onClick = onFullscreen,
                                modifier = Modifier.size(40.dp),
                                contentDescription = stringResource(R.string.chat_surface_fullscreen),
                            ) {
                                RuntimeIcon(
                                    RuntimeIconName.OpenInNew,
                                    Modifier.size(22.dp),
                                    RuntimeTheme.colorScheme.onSurface,
                                )
                            }
                        } else {
                            Spacer(Modifier.width(40.dp))
                        }
                    }
                    else -> {
                        // 有分身/手机切换 pill 时仍展示用户固定入口；仅手机页全屏态收进右侧全屏键。
                        toolbarEntries.forEach { entry -> ChatTopToolbarButton(entry) }
                        RuntimeIconButton(onClick = { expanded = true }, modifier = Modifier.size(40.dp),
                            contentDescription = stringResource(R.string.chat_workbench_controls)) {
                            RuntimeIcon(RuntimeIconName.More, Modifier.size(22.dp), RuntimeTheme.colorScheme.onSurface)
                        }
                    }
                }
            }
        }
    }
    if (expanded) {
        val closeThen: (() -> Unit) -> () -> Unit = { action -> { expanded = false; action() } }
        PlainBackdropModalBottomSheet(onDismissRequest = { expanded = false }) {
            ChatWorkbenchSheetBody(
                pinned = pinnedSet,
                companion = companion,
                speech = speech,
                browserHighlight = browserHighlight,
                repositoryHighlight = repositoryHighlight,
                toolchainHighlight = toolchainHighlight,
                runMode = runMode,
                branch = currentBranch,
                events = runtimeEvents,
                running = running,
                onOpenBrowser = onOpenBrowser,
                onOpenRepository = onOpenRepository,
                onOpenBots = onOpenBots,
                onOpenWorkshop = onOpenWorkshop,
                onOpenMemory = onOpenMemory,
                onOpenOutline = onOpenOutline,
                onExportMarkdown = onExportMarkdown,
                onOpenRunModes = onOpenRunModes,
                onOpenBranches = onOpenBranches,
                onOpenRuntime = onOpenRuntime,
                onOpenToolchain = onOpenToolchain,
                onOpenReasoning = onOpenReasoning,
                onFloatingChat = openFloatingChat,
                closeThen = closeThen,
            )
        }
    }
}

@Composable
private fun ChatSurfacePill(
    phoneOpen: Boolean,
    onSelectPhone: (Boolean) -> Unit,
) {
    RuntimeSegmentedControl(
        options = listOf(
            stringResource(R.string.chat_surface_avatar),
            stringResource(R.string.chat_surface_phone),
        ),
        selectedIndex = if (phoneOpen) 1 else 0,
        onSelect = { onSelectPhone(it == 1) },
        modifier = Modifier.widthIn(max = 136.dp * LocalDensity.current.fontScale.coerceAtLeast(1f)),
    )
}
