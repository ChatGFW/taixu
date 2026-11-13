package top.wkbin.taixu.ui.chat

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

import android.view.KeyEvent
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import top.wkbin.taixu.ui.miuix.RuntimeTheme
import top.wkbin.taixu.ui.miuix.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.movableContentOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import top.wkbin.taixu.feature.chat.R
import top.wkbin.taixu.runtime.virtualdisplay.TwinPhonePresence
import top.wkbin.taixu.runtime.virtualdisplay.VirtualDisplayCoordinator
import top.wkbin.taixu.ui.components.LocalRuntimeWidthClass
import top.wkbin.taixu.ui.components.RuntimeAdaptiveMotion
import top.wkbin.taixu.ui.components.RuntimeFloatingToolbar
import top.wkbin.taixu.ui.components.RuntimeIcon
import top.wkbin.taixu.ui.components.RuntimeIconButton
import top.wkbin.taixu.ui.components.RuntimeIconName
import top.wkbin.taixu.ui.components.RuntimeWidthClass

/** Chat area needed before the companion pane can sit beside the conversation. */
private val DockedCompanionMinWidth = 720.dp
private const val MIN_COMPANION_RATIO = 0.35f
private const val MAX_COMPANION_RATIO = 0.65f

private enum class CompanionMode { None, Pager, Overlay, Docked }

/** 分身 SurfaceView 是否应显示（聊天页 false=INVISIBLE 保活）。 */
internal val LocalTwinPreviewVisible = compositionLocalOf { true }

/** 手机大窗是否展开；小窗内不画「我来」/等待底栏。 */
internal val LocalTwinPhoneExpanded = compositionLocalOf { false }

/** Top-bar entry that shows or hides the terminal/browser companion pane. */
internal data class ChatCompanionToggle(val open: Boolean, val onToggle: () -> Unit)

/**
 * 紧凑态顶栏主切换。
 * [phoneOpen]=true 时展示虚拟屏伴随页，对话与输入栏常驻不销毁。
 */
internal data class ChatSurfaceSwitch(
    val phoneOpen: Boolean,
    val onSelectPhone: (Boolean) -> Unit,
    /** 手机页右侧「全屏」：隐藏顶栏，虚拟屏铺满。 */
    val onFullscreen: (() -> Unit)? = null,
)

/**
 * Lays out the conversation with its terminal/browser companion per window width:
 * - Compact: 分身|手机整页切换（无遮罩），对话与虚拟屏均 keep-alive。
 * - Medium: the companion slides over from the end edge.
 * - Expanded: the companion docks beside the conversation, resizable and collapsible.
 */
@Composable
internal fun ChatAdaptiveLayout(
    workspace: String,
    terminalPane: (@Composable (project: String) -> Unit)?,
    browserPane: (@Composable (onExit: (() -> Unit)?) -> Unit)?,
    twinPane: (@Composable () -> Unit)? = null,
    browserActivityTick: Long,
    browserBackPressed: (() -> Boolean)?,
    bottomInsets: WindowInsets,
    topBar: @Composable (
        onOpenBrowser: (() -> Unit)?,
        browserHighlight: Boolean,
        companion: ChatCompanionToggle?,
        surface: ChatSurfaceSwitch?,
    ) -> Unit,
    chatContent: @Composable (Modifier) -> Unit,
) {
    val widthClass = LocalRuntimeWidthClass.current
    val latestTerminal by rememberUpdatedState(terminalPane)
    val latestBrowser by rememberUpdatedState(browserPane)
    val latestTwin by rememberUpdatedState(twinPane)
    val terminal = remember { movableContentOf { project: String -> latestTerminal?.invoke(project) } }
    val browser = remember { movableContentOf { onExit: (() -> Unit)? -> latestBrowser?.invoke(onExit) } }
    val twin = remember { movableContentOf { latestTwin?.invoke() } }
    var selectedTab by rememberSaveable { mutableStateOf(ChatCompanionTab.Terminal) }
    var companionRatio by rememberSaveable { mutableFloatStateOf(0.52f) }
    var lastSeenBrowserTick by remember { mutableLongStateOf(0L) }
    val twinExpandGeneration by TwinPhonePresence.expandGeneration.collectAsStateWithLifecycle()
    val twinCoordinator = koinInject<VirtualDisplayCoordinator>()
    val twinRunning by twinCoordinator.phoneTasks.runningSession.collectAsStateWithLifecycle()
    val twinWaiting by twinCoordinator.phoneTasks.waiting.collectAsStateWithLifecycle()
    val twinSticky by TwinPhonePresence.stickyResult.collectAsStateWithLifecycle()
    val twinInPip by TwinPhonePresence.inPip.collectAsStateWithLifecycle()
    LaunchedEffect(twinSticky?.untilEpochMs) {
        val sticky = twinSticky ?: return@LaunchedEffect
        val remain = sticky.untilEpochMs - System.currentTimeMillis()
        if (remain > 0L) delay(remain)
        TwinPhonePresence.clearStickyResult()
    }
    // 新任务开始时清掉上一次结果 sticky。
    LaunchedEffect(twinRunning, twinWaiting) {
        if (twinRunning != null || twinWaiting != null) TwinPhonePresence.clearStickyResult()
    }
    val phoneTaskActive = twinRunning != null || twinWaiting != null || twinSticky != null || twinInPip
    val scope = rememberCoroutineScope()

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val mode = when {
            terminalPane != null && widthClass == RuntimeWidthClass.Expanded &&
                maxWidth >= DockedCompanionMinWidth -> CompanionMode.Docked
            terminalPane != null && widthClass != RuntimeWidthClass.Compact -> CompanionMode.Overlay
            browserPane != null || twinPane != null -> CompanionMode.Pager
            else -> CompanionMode.None
        }
        var companionShown by rememberSaveable { mutableStateOf(mode == CompanionMode.Docked) }
        // 仅通知点入 / 显式 expand 才切大窗；任务开始只出小窗。
        LaunchedEffect(twinExpandGeneration, twinPane != null) {
            if (twinExpandGeneration > 0L && twinPane != null) {
                selectedTab = ChatCompanionTab.Twin
                companionShown = true
            }
        }
        val dockedFraction by animateFloatAsState(
            if (mode == CompanionMode.Docked && companionShown) 1f else 0f, RuntimeAdaptiveMotion.float)
        val overlayProgress by animateFloatAsState(
            if (mode == CompanionMode.Overlay && companionShown) 1f else 0f, RuntimeAdaptiveMotion.float)
        val browserSelected = selectedTab == ChatCompanionTab.Browser
        val browserPage = mode == CompanionMode.Pager && companionShown && browserSelected && browserPane != null
        val phonePage = mode == CompanionMode.Pager && twinPane != null &&
            (twinInPip || (companionShown && !browserSelected))
        var phoneFullscreen by rememberSaveable { mutableStateOf(false) }
        // 画中画：只留分身画面，强制展开全屏。
        LaunchedEffect(twinInPip) {
            if (twinInPip && twinPane != null) {
                selectedTab = ChatCompanionTab.Twin
                companionShown = true
                phoneFullscreen = true
                TwinPhonePresence.previewVisible = true
            }
        }
        LaunchedEffect(phonePage, phoneTaskActive) {
            TwinPhonePresence.setPhoneSurfaceOpen(phonePage || twinInPip)
            if (!phonePage && !twinInPip) {
                phoneFullscreen = false
                // 大窗收起时若任务仍在：钉住应用内小窗。
                if (phoneTaskActive) TwinPhonePresence.pinMini()
            }
        }
        val overlayOpen = mode == CompanionMode.Overlay && companionShown
        val browserVisible = browserPane != null && companionShown && browserSelected && mode != CompanionMode.None
        LaunchedEffect(browserVisible, browserActivityTick) {
            if (browserVisible) lastSeenBrowserTick = browserActivityTick
        }
        BackHandler(enabled = browserPage || phonePage || overlayOpen) {
            when {
                phoneFullscreen -> phoneFullscreen = false
                phonePage -> scope.launch {
                    runCatching {
                        twinCoordinator.injectKey(keyCode = KeyEvent.KEYCODE_BACK)
                    }
                }
                browserSelected && browserBackPressed?.invoke() == true -> Unit
                else -> companionShown = false
            }
        }
        val openBrowser: (() -> Unit)? = browserPane?.let {
            { selectedTab = ChatCompanionTab.Browser; companionShown = true }
        }
        val toggle = when (mode) {
            CompanionMode.Docked, CompanionMode.Overlay ->
                ChatCompanionToggle(companionShown) { companionShown = !companionShown }
            CompanionMode.Pager -> if (twinPane != null || terminalPane != null) {
                ChatCompanionToggle(open = companionShown && !browserSelected) {
                    if (companionShown && !browserSelected) {
                        companionShown = false
                    } else {
                        if (selectedTab == ChatCompanionTab.Browser) {
                            selectedTab = if (twinPane != null) ChatCompanionTab.Twin else ChatCompanionTab.Terminal
                        } else if (twinPane != null) {
                            selectedTab = ChatCompanionTab.Twin
                        }
                        companionShown = true
                    }
                }
            } else {
                null
            }
            CompanionMode.None -> null
        }
        val surfaceSwitch: ChatSurfaceSwitch? =
            if (mode == CompanionMode.Pager && twinPane != null && !phoneFullscreen) {
                ChatSurfaceSwitch(
                    phoneOpen = companionShown && !browserSelected,
                    onSelectPhone = { phone ->
                        if (phone) {
                            selectedTab = ChatCompanionTab.Twin
                            companionShown = true
                        } else {
                            companionShown = false
                        }
                    },
                    onFullscreen = if (companionShown && !browserSelected) {
                        { phoneFullscreen = true }
                    } else {
                        null
                    },
                )
            } else {
                null
            }
        val companion: @Composable (Modifier) -> Unit = { modifier ->
            // 手机页只要分身，不再套「终端|浏览器|分身」分段。
            if (twinPane != null && (mode == CompanionMode.Pager || selectedTab == ChatCompanionTab.Twin)) {
                Box(modifier.fillMaxSize()) { twin() }
            } else {
                ChatCompanionPane(
                    selectedTab = selectedTab,
                    onSelectTab = { selectedTab = it },
                    workspace = workspace,
                    terminal = { terminal(it) },
                    browser = if (browserPane != null) ({ onExit -> browser(onExit) }) else null,
                    twin = if (twinPane != null) ({ twin() }) else null,
                    modifier = modifier,
                )
            }
        }

        val screenWidth = maxWidth
        val coverChrome = browserPage || phonePage || overlayProgress > 0.5f
        val presenceAspect by TwinPhonePresence.contentAspectFlow.collectAsStateWithLifecycle()
        val twinSize = twinCoordinator.getVideoSize(VirtualDisplayCoordinator.DEFAULT_SESSION_ID)
        val twinAspect = twinSize?.let { it.first.toFloat() / it.second.toFloat() }
            ?.takeIf { it > 0.05f }
            ?: presenceAspect.takeIf { it > 0.05f }
            ?: (9f / 19.5f)
        // 对话层先画；分身层后叠（聊天小窗 / 手机大窗）。
        if (!phonePage) {
            Box(
                Modifier.fillMaxSize()
                    .then(if (coverChrome) Modifier.clearAndSetSemantics {} else Modifier),
            ) {
                val innerWidth = screenWidth - 24.dp - ResizeHandleWidth
                Row(Modifier.fillMaxSize().padding(horizontal = 12.dp)) {
                    chatContent(Modifier.weight(1f).fillMaxHeight())
                    if (mode == CompanionMode.Docked && dockedFraction > 0f) {
                        CompanionResizeHandle(innerWidth) { delta ->
                            companionRatio = (companionRatio - delta)
                                .coerceIn(MIN_COMPANION_RATIO, MAX_COMPANION_RATIO)
                        }
                        companion(
                            Modifier.width(innerWidth * companionRatio * dockedFraction)
                                .fillMaxHeight().statusBarsPadding().padding(top = 64.dp)
                                .windowInsetsPadding(bottomInsets).padding(vertical = 4.dp),
                        )
                    }
                }
            }
        }
        // TwinPhoneLayer 常挂以保 SurfaceView；无任务时 miniVisible=false 不画小窗。
        if (mode == CompanionMode.Pager && twinPane != null) {
            ChatTwinHost(
                bottomInsets = bottomInsets,
                phonePage = phonePage,
                phoneFullscreen = phoneFullscreen,
                phoneTaskActive = phoneTaskActive,
                twinAspect = twinAspect,
                onExpand = {
                    selectedTab = ChatCompanionTab.Twin
                    companionShown = true
                },
                onCollapse = {
                    phoneFullscreen = false
                    companionShown = false
                },
                twin = { twin() },
            )
        }
        if (mode == CompanionMode.Overlay && (overlayOpen || overlayProgress > 0f)) {
            CompanionOverlay(
                overlayProgress,
                (maxWidth * 0.55f).coerceIn(360.dp, 480.dp).coerceAtMost(maxWidth - 48.dp),
                bottomInsets,
                onDismiss = { companionShown = false },
            ) { companion(Modifier.fillMaxSize()) }
        }
        if (mode == CompanionMode.Pager && twinPane == null && terminalPane != null) {
            CompanionFullPage(
                visible = companionShown && !browserSelected,
                keepAlive = false,
                bottomInsets = bottomInsets,
            ) {
                companion(Modifier.fillMaxSize())
            }
        }
        if (browserPage) {
            Surface(Modifier.fillMaxSize(), color = RuntimeTheme.colorScheme.background) {
                Box(Modifier.fillMaxSize().statusBarsPadding().windowInsetsPadding(bottomInsets)) {
                    browser { companionShown = false }
                }
            }
        }
        if (phoneFullscreen) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                contentAlignment = Alignment.CenterEnd,
            ) {
                RuntimeFloatingToolbar {
                    RuntimeIconButton(
                        onClick = { phoneFullscreen = false },
                        modifier = Modifier.size(40.dp),
                        contentDescription = stringResource(R.string.chat_surface_exit_fullscreen),
                    ) {
                        RuntimeIcon(
                            RuntimeIconName.Compress,
                            Modifier.size(22.dp),
                            RuntimeTheme.colorScheme.onSurface,
                        )
                    }
                }
            }
        } else {
            Box(
                Modifier
                    .fillMaxWidth()
                    .then(if (browserPage) Modifier.clearAndSetSemantics {} else Modifier),
            ) {
                topBar(openBrowser, browserActivityTick > lastSeenBrowserTick, toggle, surfaceSwitch)
            }
        }
    }
}
