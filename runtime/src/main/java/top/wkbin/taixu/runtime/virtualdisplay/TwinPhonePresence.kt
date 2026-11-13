package top.wkbin.taixu.runtime.virtualdisplay

import android.content.Intent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

enum class TwinPhoneStickyKind { Done, Failed, Cancelled }

data class TwinPhoneStickyResult(
    val kind: TwinPhoneStickyKind,
    val label: String,
    val untilEpochMs: Long,
)

/**
 * 聊天伴随「分身」Tab 是否正在显示，以及打开/小窗钉住/结果 sticky 请求。
 *
 * 用单调递增的 [openGeneration]（StateFlow）而不是无 replay 的 SharedFlow：
 * 任务开始时 UI 可能尚未订阅，StateFlow 仍能让后到的收集者看到最新打开请求。
 */
object TwinPhonePresence {
    @Volatile
    var embeddedVisible: Boolean = false

    /**
     * 分身 SurfaceView 是否应显示。
     * false=INVISIBLE 保活（聊天页），避免 SurfaceView hole-punch 穿透；不触发 surfaceDestroyed。
     */
    @Volatile
    var previewVisible: Boolean = true

    private val _openGeneration = MutableStateFlow(0L)
    val openGeneration: StateFlow<Long> = _openGeneration.asStateFlow()

    private val _expandGeneration = MutableStateFlow(0L)
    val expandGeneration: StateFlow<Long> = _expandGeneration.asStateFlow()

    private val _miniPinned = MutableStateFlow(false)
    val miniPinned: StateFlow<Boolean> = _miniPinned.asStateFlow()

    private val _takeOverNudge = MutableStateFlow(0L)
    val takeOverNudge: StateFlow<Long> = _takeOverNudge.asStateFlow()

    private val _stickyResult = MutableStateFlow<TwinPhoneStickyResult?>(null)
    val stickyResult: StateFlow<TwinPhoneStickyResult?> = _stickyResult.asStateFlow()

    private val _phoneSurfaceOpen = MutableStateFlow(false)
    val phoneSurfaceOpen: StateFlow<Boolean> = _phoneSurfaceOpen.asStateFlow()

    private val _inPip = MutableStateFlow(false)
    /** 系统画中画中；UI 应只保留分身画面。 */
    val inPip: StateFlow<Boolean> = _inPip.asStateFlow()

    /** 成功结果 sticky 约 1800ms。 */
    private const val STICKY_DONE_MS = 1800L
    /** 失败/取消 sticky 约 1600ms。 */
    private const val STICKY_FAIL_MS = 1600L

    /** 手机任务开始时请求 UI 切到分身 Tab（若尚未打开），并钉住小窗。 */
    fun requestOpen(): Boolean {
        pinMini()
        _openGeneration.update { it + 1L }
        return true
    }

    /** 请求展开为大窗。 */
    fun requestExpand(): Boolean {
        requestOpen()
        _expandGeneration.update { it + 1L }
        return true
    }

    /** 任务/虚拟屏操作进行中，从大窗回到聊天时必须保留小窗。 */
    fun pinMini() {
        _miniPinned.value = true
    }

    fun unpinMini() {
        _miniPinned.value = false
    }

    /** Agent 操控中用户触摸：提醒点「我来」，不自动接管。 */
    fun nudgeTakeOver() {
        _takeOverNudge.update { it + 1L }
    }

    fun setPhoneSurfaceOpen(open: Boolean) {
        _phoneSurfaceOpen.value = open
    }

    fun setInPip(inPip: Boolean) {
        _inPip.value = inPip
        if (inPip) {
            pinMini()
            previewVisible = true
        }
    }

    fun showResultSticky(label: String, success: Boolean, cancelled: Boolean = false) {
        val kind = when {
            cancelled -> TwinPhoneStickyKind.Cancelled
            success -> TwinPhoneStickyKind.Done
            else -> TwinPhoneStickyKind.Failed
        }
        val hold = if (kind == TwinPhoneStickyKind.Done) STICKY_DONE_MS else STICKY_FAIL_MS
        pinMini()
        _stickyResult.value = TwinPhoneStickyResult(
            kind = kind,
            label = label.take(40),
            untilEpochMs = System.currentTimeMillis() + hold,
        )
    }

    fun clearStickyResult() {
        _stickyResult.value = null
        unpinMini()
    }

    /** 消费分身任务通知点入的 Intent extra，打开分身 Tab。 */
    fun consumeOpenTwinIntent(intent: Intent?): Boolean {
        if (intent?.getBooleanExtra(PhoneTaskNotifier.EXTRA_OPEN_TWIN, false) != true) return false
        requestOpen()
        intent.removeExtra(PhoneTaskNotifier.EXTRA_OPEN_TWIN)
        return true
    }
}
