package top.wkbin.taixu.runtime.virtualdisplay

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

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
        // 轨迹铺满 View，与 TextureView stretch 对齐。
        val now = System.currentTimeMillis()
        for (trail in TwinTouchTrailBus.trails.value) {
            val remain = (trail.untilEpochMs - now).toFloat()
            val hold = when (trail.kind) {
                TwinTouchTrailKind.Tap -> TwinTouchTrailBus.TAP_HOLD_MS.toFloat()
                TwinTouchTrailKind.Swipe -> TwinTouchTrailBus.SWIPE_HOLD_MS.toFloat()
            }
            val t = (1f - (remain / hold)).coerceIn(0f, 1f)
            val alpha = ((1f - t) * 255f).toInt().coerceIn(0, 255)
            val x1 = trail.x1 * w
            val y1 = trail.y1 * h
            when (trail.kind) {
                TwinTouchTrailKind.Tap -> {
                    tapPaint.alpha = alpha
                    tapFill.alpha = alpha
                    val radius = dp(10f) + t * dp(22f)
                    canvas.drawCircle(x1, y1, radius, tapPaint)
                    canvas.drawCircle(x1, y1, dp(5f), tapFill)
                }
                TwinTouchTrailKind.Swipe -> {
                    val x2 = trail.x2 * w
                    val y2 = trail.y2 * h
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

    private fun dp(v: Float): Float = v * resources.displayMetrics.density
}
