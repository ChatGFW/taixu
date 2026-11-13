package top.wkbin.taixu.ui.chat

import android.widget.Toast
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import top.wkbin.taixu.feature.chat.R
import top.wkbin.taixu.runtime.virtualdisplay.PhoneTaskWaitMode
import top.wkbin.taixu.runtime.virtualdisplay.TwinPhonePresence
import top.wkbin.taixu.runtime.virtualdisplay.TwinPhoneStickyKind
import top.wkbin.taixu.runtime.virtualdisplay.VirtualDisplayCoordinator

/**
 * 挂载 [TwinPhoneLayer]：小窗状态 chip + 流光；大窗圆角下挂软导航与接管条。
 * SurfaceView 常挂；无任务时 [miniVisible]=false。
 */
@Composable
internal fun ChatTwinHost(
    bottomInsets: WindowInsets,
    phonePage: Boolean,
    phoneFullscreen: Boolean,
    phoneTaskActive: Boolean,
    twinAspect: Float,
    onExpand: () -> Unit,
    onCollapse: () -> Unit,
    twin: @Composable () -> Unit,
) {
    val twinCoordinator = koinInject<VirtualDisplayCoordinator>()
    val scope = rememberCoroutineScope()
    val twinWaiting by twinCoordinator.phoneTasks.waiting.collectAsStateWithLifecycle()
    val twinRunning by twinCoordinator.phoneTasks.runningSession.collectAsStateWithLifecycle()
    val twinSticky by TwinPhonePresence.stickyResult.collectAsStateWithLifecycle()
    val miniPinned by TwinPhonePresence.miniPinned.collectAsStateWithLifecycle()
    val takeOverNudge by TwinPhonePresence.takeOverNudge.collectAsStateWithLifecycle()
    val showMini = phoneTaskActive || miniPinned
    TwinPhonePresence.previewVisible = phonePage || showMini
    val context = LocalContext.current
    val takeoverNudgeText = stringResource(R.string.chat_twin_takeover_nudge)
    LaunchedEffect(takeOverNudge) {
        if (takeOverNudge <= 0L) return@LaunchedEffect
        Toast.makeText(context, takeoverNudgeText, Toast.LENGTH_SHORT).show()
    }
    val stickyBadge = when (twinSticky?.kind) {
        TwinPhoneStickyKind.Done -> TwinMiniBadge(
            stringResource(R.string.chat_twin_mini_done), Color(0xFF3E7D5C),
        )
        TwinPhoneStickyKind.Failed -> TwinMiniBadge(
            stringResource(R.string.chat_twin_mini_failed), Color(0xFFA8453A),
        )
        TwinPhoneStickyKind.Cancelled -> TwinMiniBadge(
            stringResource(R.string.chat_twin_mini_cancelled), Color(0xFF5A6069),
        )
        null -> null
    }
    val waitBadge = when (twinWaiting?.mode) {
        PhoneTaskWaitMode.HANDOFF -> TwinMiniBadge(
            stringResource(R.string.chat_twin_mini_need), Color(0xFFA8681C), breath = true,
        )
        PhoneTaskWaitMode.CONFIRM -> TwinMiniBadge(
            stringResource(R.string.chat_twin_mini_confirm), Color(0xFFA8681C), breath = true,
        )
        PhoneTaskWaitMode.PAUSE -> TwinMiniBadge(
            stringResource(R.string.chat_twin_mini_paused), Color(0xFFA8681C),
        )
        null -> null
    }
    val glowKind = when {
        twinSticky?.kind == TwinPhoneStickyKind.Done -> TwinGlowKind.Done
        twinSticky?.kind == TwinPhoneStickyKind.Failed ||
            twinSticky?.kind == TwinPhoneStickyKind.Cancelled -> TwinGlowKind.Failed
        twinWaiting != null -> TwinGlowKind.Need
        else -> TwinGlowKind.Running
    }
    TwinPhoneLayer(
        bottomInsets = bottomInsets,
        expanded = phonePage,
        immersive = phoneFullscreen,
        miniVisible = showMini,
        sweepActive = showMini,
        contentAspect = twinAspect,
        glowKind = glowKind,
        badge = stickyBadge ?: waitBadge,
        onExpand = onExpand,
        onCollapse = onCollapse,
        expandedChrome = if (phonePage && !phoneFullscreen) {
            {
                TwinPhoneChromeBar(
                    onInjectKey = { keyCode ->
                        // 仅自动操控中锁软键；接手/暂停等待时与画面触控一并放开。
                        val session = twinWaiting?.sessionId
                            ?: twinRunning
                            ?: VirtualDisplayCoordinator.DEFAULT_SESSION_ID
                        if (twinRunning != null && twinWaiting == null) {
                            TwinPhonePresence.nudgeTakeOver()
                        } else {
                            scope.launch {
                                twinCoordinator.injectKey(sessionId = session, keyCode = keyCode)
                            }
                        }
                    },
                ) {
                    TwinPhoneExpandedChrome(
                        waiting = twinWaiting,
                        runningSession = twinRunning,
                        onTakeOver = { twinCoordinator.takeOverPhoneTask(it) },
                        onResume = { twinCoordinator.resumePhoneTask(it) },
                        onCancel = { twinCoordinator.cancelPhoneTask(it) },
                        compact = true,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        } else {
            null
        },
    ) {
        CompositionLocalProvider(
            LocalTwinPreviewVisible provides (phonePage || showMini),
            LocalTwinPhoneExpanded provides phonePage,
        ) {
            twin()
        }
    }
}
