package top.wkbin.taixu.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.compose.koinInject
import top.wkbin.taixu.core.model.ExecutionMode
import top.wkbin.taixu.runtime.privilege.PrivilegeAvailability
import top.wkbin.taixu.runtime.privilege.PrivilegeManager
import top.wkbin.taixu.runtime.virtualdisplay.PhoneTaskWaitMode
import top.wkbin.taixu.runtime.virtualdisplay.TwinPhonePresence
import top.wkbin.taixu.runtime.virtualdisplay.TwinPhoneSurfaceHost
import top.wkbin.taixu.runtime.virtualdisplay.VirtualDisplayCoordinator
import top.wkbin.taixu.ui.components.RuntimeButton
import top.wkbin.taixu.ui.components.RuntimeTextButton
import top.wkbin.taixu.ui.miuix.RuntimeTheme
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.squircle.squircleSurface

/**
 * 分身预览内容：SurfaceView 铺满外层圆角容器。
 * 「我来」/等待条由 [TwinPhoneLayer] 放在圆角区域下方，不叠在画面上。
 */
@Composable
internal fun TwinPhoneCompanionPane(modifier: Modifier = Modifier) {
    val coordinator = koinInject<VirtualDisplayCoordinator>()
    val privilegeManager = koinInject<PrivilegeManager>()
    val privilege by privilegeManager.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var status by remember { mutableStateOf("正在准备分身…") }
    var ready by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var bootToken by remember { mutableIntStateOf(0) }
    val previewVisible = LocalTwinPreviewVisible.current
    val runningSession by coordinator.phoneTasks.runningSession.collectAsStateWithLifecycle()
    val waiting by coordinator.phoneTasks.waiting.collectAsStateWithLifecycle()
    // 工作流可能用 workflow-${id}；预览/触控必须跟当前任务 session，勿写死 default。
    val previewSession = waiting?.sessionId
        ?: runningSession
        ?: VirtualDisplayCoordinator.DEFAULT_SESSION_ID
    val privileged =
        privilege.effectiveMode == ExecutionMode.SHIZUKU ||
            privilege.effectiveMode == ExecutionMode.ROOT

    DisposableEffect(Unit) {
        TwinPhonePresence.embeddedVisible = true
        coordinator.hideOverlay()
        onDispose {
            TwinPhonePresence.embeddedVisible = false
        }
    }
    SideEffect {
        TwinPhonePresence.previewVisible = previewVisible
    }

    LaunchedEffect(
        bootToken,
        privilege.availability,
        privilege.effectiveMode,
        privilege.shizukuAvailable,
        privilege.rootAvailable,
        privilege.preferredMode,
        previewSession,
    ) {
        // 冷启动 reconcile 未完成前勿误判「无权限」；等 CHECKING 结束再自动建屏。
        if (privilege.availability == PrivilegeAvailability.CHECKING) {
            status = "正在检查权限…"
            ready = false
            return@LaunchedEffect
        }
        // 已有 / 可自动拿到 Shizuku·Root 则切模式并建屏，首次进「手机」无需再点启用。
        if (!privileged) {
            val autoMode = when {
                privilege.shizukuAvailable || privilege.preferredMode == ExecutionMode.SHIZUKU ->
                    ExecutionMode.SHIZUKU
                privilege.rootAvailable || privilege.preferredMode == ExecutionMode.ROOT ->
                    ExecutionMode.ROOT
                else -> null
            }
            if (autoMode == null) {
                status = "分身需要 Shizuku 或 Root。可在本页一键启用 / 刷新。"
                ready = false
                return@LaunchedEffect
            }
            status = if (autoMode == ExecutionMode.SHIZUKU) "正在启用 Shizuku…" else "正在启用 Root…"
            val switched = withContext(Dispatchers.IO) { privilegeManager.switchMode(autoMode) }
            if (switched.getOrNull() == null) {
                status = switched.errorOrNull()?.message
                    ?: if (autoMode == ExecutionMode.SHIZUKU) {
                        "Shizuku 已安装，点下方启用即可。"
                    } else {
                        "Root 启用失败，可点下方重试。"
                    }
                ready = false
                return@LaunchedEffect
            }
        }
        status = "正在创建虚拟屏…"
        var displayId: Int? = null
        repeat(3) { attempt ->
            displayId = withContext(Dispatchers.IO) {
                coordinator.ensureVirtualDisplay(previewSession)
            }
            if (displayId != null) return@repeat
            if (attempt < 2) delay(400L * (attempt + 1))
        }
        if (displayId == null) {
            status = coordinator.creationFailure(previewSession)
            ready = false
        } else {
            coordinator.getVideoSize(previewSession)?.let { (w, h) ->
                if (w > 0 && h > 0) TwinPhonePresence.updateContentAspect(w.toFloat() / h.toFloat())
            }
            // ensureDisplay 已 am start TwinLauncher；勿再 warm 盖 Settings/系统桌面导致黑屏。
            status = "分身已就绪"
            ready = true
        }
    }

    Box(
        modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        if (!ready) {
            Column(
                Modifier
                    .align(Alignment.Center)
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    status,
                    color = Color.White.copy(alpha = 0.85f),
                    style = RuntimeTheme.typography.bodyMedium,
                )
                if (!privileged) {
                    RuntimeButton(
                        onClick = {
                            if (busy) return@RuntimeButton
                            busy = true
                            scope.launch {
                                try {
                                    status = "正在启用 Shizuku…"
                                    val result = privilegeManager.switchMode(ExecutionMode.SHIZUKU)
                                    status = result.getOrNull()?.details
                                        ?: result.errorOrNull()?.message
                                        ?: "Shizuku 启用失败"
                                    bootToken++
                                } finally {
                                    busy = false
                                }
                            }
                        },
                        enabled = !busy,
                    ) {
                        Text(if (busy) "请稍候…" else "启用 Shizuku")
                    }
                    RuntimeTextButton(
                        onClick = {
                            if (busy) return@RuntimeTextButton
                            busy = true
                            scope.launch {
                                try {
                                    status = "正在刷新权限…"
                                    val target = when {
                                        privilege.preferredMode == ExecutionMode.ROOT ->
                                            ExecutionMode.ROOT
                                        else -> ExecutionMode.SHIZUKU
                                    }
                                    val result = privilegeManager.switchMode(target)
                                    status = result.getOrNull()?.details
                                        ?: result.errorOrNull()?.message
                                        ?: "刷新完成"
                                    bootToken++
                                } finally {
                                    busy = false
                                }
                            }
                        },
                        enabled = !busy,
                    ) {
                        Text("刷新并启用")
                    }
                }
            }
        } else {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { context -> TwinPhoneSurfaceHost(context) },
                update = { view ->
                    TwinPhonePresence.previewVisible = previewVisible
                    view.bind(
                        sessionId = previewSession,
                        coordinator = coordinator,
                        scope = scope,
                        statusText = status,
                    )
                    view.applyPreviewVisibility()
                },
                onRelease = { view -> view.unbind() },
            )
        }
    }
}

/** 大窗底栏右侧操作区；[compact]=true 时嵌在 [TwinPhoneChromeBar] 同一排。 */
@Composable
internal fun TwinPhoneExpandedChrome(
    waiting: top.wkbin.taixu.runtime.virtualdisplay.PhoneTaskWait?,
    runningSession: String?,
    onTakeOver: (String) -> Unit,
    onResume: (String) -> Unit,
    onCancel: (String) -> Unit,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    when {
        waiting != null -> TwinPhoneWaitBar(
            mode = waiting.mode,
            onResume = { onResume(waiting.sessionId) },
            onCancel = { onCancel(waiting.sessionId) },
            modifier = modifier,
            compact = compact,
        )
        runningSession != null -> TwinPhoneTakeOverBar(
            onTakeOver = { onTakeOver(runningSession) },
            onCancel = { onCancel(runningSession) },
            modifier = modifier,
            compact = compact,
        )
        else -> TwinPhoneOperateHint(modifier = modifier, compact = compact)
    }
}

@Composable
private fun TwinPhoneOperateHint(modifier: Modifier = Modifier, compact: Boolean = false) {
    if (compact) {
        Text(
            "操作手机",
            modifier = modifier.padding(horizontal = 8.dp),
            color = RuntimeTheme.colorScheme.onSurface,
            style = RuntimeTheme.typography.titleSmall,
            maxLines = 1,
        )
    } else {
        Row(
            modifier = modifier
                .fillMaxWidth()
                .squircleSurface(
                    color = RuntimeTheme.colorScheme.surfaceContainerHigh,
                    cornerRadius = 28.dp,
                )
                .padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "操作手机",
                color = RuntimeTheme.colorScheme.onSurface,
                style = RuntimeTheme.typography.titleSmall,
            )
        }
    }
}

@Composable
private fun TwinPhoneTakeOverBar(
    onTakeOver: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    Row(
        modifier = if (compact) {
            modifier.padding(start = 4.dp)
        } else {
            modifier
                .fillMaxWidth()
                .squircleSurface(
                    color = RuntimeTheme.colorScheme.surfaceContainerHigh,
                    cornerRadius = 28.dp,
                )
                .padding(horizontal = 12.dp, vertical = 10.dp)
        },
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            if (compact) "先点「我来」" else "自动操作中 · 先点「我来」再触摸",
            modifier = Modifier.weight(1f),
            color = RuntimeTheme.colorScheme.onSurface,
            style = RuntimeTheme.typography.bodySmall,
            maxLines = 1,
        )
        RuntimeTextButton(onClick = onCancel) { Text("停止") }
        RuntimeButton(onClick = onTakeOver) { Text("我来") }
    }
}

@Composable
private fun TwinPhoneWaitBar(
    mode: PhoneTaskWaitMode,
    onResume: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    val resumeLabel = when (mode) {
        PhoneTaskWaitMode.CONFIRM -> "允许"
        PhoneTaskWaitMode.HANDOFF -> "我做完了"
        PhoneTaskWaitMode.PAUSE -> "继续"
    }
    val cancelLabel = when (mode) {
        PhoneTaskWaitMode.CONFIRM -> "拒绝"
        else -> "停止"
    }
    val hint = when (mode) {
        PhoneTaskWaitMode.CONFIRM -> if (compact) "待确认" else "需要你确认后才会继续"
        PhoneTaskWaitMode.HANDOFF -> if (compact) "需要你" else "需要你在分身里操作，完成后点「我做完了」"
        PhoneTaskWaitMode.PAUSE -> if (compact) "接管中" else "你正在操作 · 接管中"
    }
    Row(
        modifier = if (compact) {
            modifier.padding(start = 4.dp)
        } else {
            modifier
                .fillMaxWidth()
                .squircleSurface(
                    color = RuntimeTheme.colorScheme.surfaceContainerHigh,
                    cornerRadius = 28.dp,
                )
                .padding(horizontal = 12.dp, vertical = 10.dp)
        },
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            hint,
            modifier = Modifier.weight(1f),
            color = RuntimeTheme.colorScheme.onSurface,
            style = RuntimeTheme.typography.bodySmall,
            maxLines = 1,
        )
        RuntimeTextButton(onClick = onCancel) { Text(cancelLabel) }
        RuntimeButton(onClick = onResume) { Text(resumeLabel) }
    }
}
