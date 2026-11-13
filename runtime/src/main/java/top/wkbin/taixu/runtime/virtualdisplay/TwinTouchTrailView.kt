package top.wkbin.taixu.runtime.virtualdisplay

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.koin.core.context.GlobalContext
import top.wkbin.taixu.runtime.virtualdisplay.VirtualDisplayCoordinator.Companion.DEFAULT_SESSION_ID

/**
 * 叠在 [TwinPhoneEmbedView] 上的轨迹层：不消费触控（回传 false），只绘制 AI 注入点。
 */
class TwinTouchTrailView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {

    private var scope: CoroutineScope? = null
    private var pruneJob: Job? = null
    private val tapPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(2.5f)
        color = 0xFF45C4F0.toInt()
    }
    private val tapFill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = 0xFF45C4F0.toInt()
    }
    private val swipePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(3f)
        strokeCap = Paint.Cap.ROUND
        color = 0xFFFFC56B.toInt()
    }
    private val swipeHead = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = 0xFF45C4F0.toInt()
    }
    private val path = Path()

    init {
        setWillNotDraw(false)
        isClickable = false
        isFocusable = false
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
    }

    fun attach(scope: CoroutineScope) {
        this.scope = scope
        pruneJob?.cancel()
        pruneJob = scope.launch {
            while (isActive) {
                TwinTouchTrailBus.pruneExpired()
                invalidate()
                delay(32)
            }
        }
    }

    fun detach() {
        pruneJob?.cancel()
        pruneJob = null
        scope = null
    }

    override fun onTouchEvent(event: MotionEvent?): Boolean = false

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat().coerceAtLeast(1f)
        val h = height.toFloat().coerceAtLeast(1f)
        // 轨迹落在 FIT_CENTER 内容矩形内，与 SurfaceView 画面对齐。
        val content = contentRect(w, h)
        val now = System.currentTimeMillis()
        for (trail in TwinTouchTrailBus.trails.value) {
            val remain = (trail.untilEpochMs - now).toFloat()
            val hold = when (trail.kind) {
                TwinTouchTrailKind.Tap -> TwinTouchTrailBus.TAP_HOLD_MS.toFloat()
                TwinTouchTrailKind.Swipe -> TwinTouchTrailBus.SWIPE_HOLD_MS.toFloat()
            }
            val t = (1f - (remain / hold)).coerceIn(0f, 1f)
            val alpha = ((1f - t) * 255f).toInt().coerceIn(0, 255)
            val x1 = content.left + trail.x1 * content.width()
            val y1 = content.top + trail.y1 * content.height()
            when (trail.kind) {
                TwinTouchTrailKind.Tap -> {
                    tapPaint.alpha = alpha
                    tapFill.alpha = alpha
                    val radius = dp(10f) + t * dp(22f)
                    canvas.drawCircle(x1, y1, radius, tapPaint)
                    canvas.drawCircle(x1, y1, dp(5f), tapFill)
                }
                TwinTouchTrailKind.Swipe -> {
                    val x2 = content.left + trail.x2 * content.width()
                    val y2 = content.top + trail.y2 * content.height()
                    val hx = x1 + (x2 - x1) * t
                    val hy = y1 + (y2 - y1) * t
                    swipePaint.alpha = alpha
                    swipeHead.alpha = alpha
                    tapFill.alpha = alpha
                    path.reset()
                    path.moveTo(x1, y1)
                    path.lineTo(hx, hy)
                    canvas.drawPath(path, swipePaint)
                    canvas.drawCircle(x1, y1, dp(6f), tapFill)
                    canvas.drawCircle(hx, hy, dp(7f), swipeHead)
                }
            }
        }
    }

    private fun contentRect(viewW: Float, viewH: Float): RectF {
        val size = runCatching {
            GlobalContext.get().get<VirtualDisplayCoordinator>().getVideoSize(DEFAULT_SESSION_ID)
        }.getOrNull()
        val vdW = size?.first?.toFloat()?.takeIf { it > 1f } ?: (9f)
        val vdH = size?.second?.toFloat()?.takeIf { it > 1f } ?: (19.5f)
        val viewAspect = viewW / viewH
        val vdAspect = vdW / vdH
        return if (viewAspect > vdAspect) {
            val h = viewH
            val w = h * vdAspect
            val left = (viewW - w) / 2f
            RectF(left, 0f, left + w, h)
        } else {
            val w = viewW
            val h = w / vdAspect
            val top = (viewH - h) / 2f
            RectF(0f, top, w, top + h)
        }
    }

    private fun dp(v: Float): Float = v * resources.displayMetrics.density
}
