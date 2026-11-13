package top.wkbin.taixu.runtime.virtualdisplay

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

enum class TwinTouchTrailKind { Tap, Swipe }

/**
 * 虚拟屏注入触点可视化：相对坐标 0..1（相对 VD 宽高），Compose 叠层按铺满矩形绘制。
 * 便于核对 AI 0–1000 → 像素是否打准。
 */
data class TwinTouchTrail(
    val id: Long,
    val kind: TwinTouchTrailKind,
    val x1: Float,
    val y1: Float,
    val x2: Float = x1,
    val y2: Float = y1,
    val untilEpochMs: Long,
)

object TwinTouchTrailBus {
    const val TAP_HOLD_MS = 700L
    const val SWIPE_HOLD_MS = 900L

    private val _trails = MutableStateFlow<List<TwinTouchTrail>>(emptyList())
    val trails: StateFlow<List<TwinTouchTrail>> = _trails.asStateFlow()

    private var seq = 0L

    fun publishTap(xPx: Int, yPx: Int, vdW: Int, vdH: Int) {
        if (vdW <= 1 || vdH <= 1) return
        push(
            TwinTouchTrail(
                id = nextId(),
                kind = TwinTouchTrailKind.Tap,
                x1 = (xPx.toFloat() / (vdW - 1)).coerceIn(0f, 1f),
                y1 = (yPx.toFloat() / (vdH - 1)).coerceIn(0f, 1f),
                untilEpochMs = System.currentTimeMillis() + TAP_HOLD_MS,
            ),
        )
    }

    fun publishSwipe(x1: Int, y1: Int, x2: Int, y2: Int, vdW: Int, vdH: Int) {
        if (vdW <= 1 || vdH <= 1) return
        push(
            TwinTouchTrail(
                id = nextId(),
                kind = TwinTouchTrailKind.Swipe,
                x1 = (x1.toFloat() / (vdW - 1)).coerceIn(0f, 1f),
                y1 = (y1.toFloat() / (vdH - 1)).coerceIn(0f, 1f),
                x2 = (x2.toFloat() / (vdW - 1)).coerceIn(0f, 1f),
                y2 = (y2.toFloat() / (vdH - 1)).coerceIn(0f, 1f),
                untilEpochMs = System.currentTimeMillis() + SWIPE_HOLD_MS,
            ),
        )
    }

    fun pruneExpired(now: Long = System.currentTimeMillis()) {
        _trails.update { list -> list.filter { it.untilEpochMs > now } }
    }

    private fun push(trail: TwinTouchTrail) {
        val now = System.currentTimeMillis()
        _trails.update { list ->
            (list.filter { it.untilEpochMs > now } + trail).takeLast(8)
        }
    }

    private fun nextId(): Long = synchronized(this) { ++seq }
}
