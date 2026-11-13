package top.wkbin.taixu.runtime.virtualdisplay

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.View
import android.view.PixelCopy
import kotlin.math.max
import kotlin.math.min
import com.ai.assistance.showerclient.ShowerController
import com.ai.assistance.showerclient.ShowerLog
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

/**
 * SurfaceView 的 Surface 经 Binder 直挂 VirtualDisplay。
 *
 * 缓冲必须等于 VD 分辨率（[setFixedSize]）；小窗/大窗由系统 FIT_CENTER 缩放。
 * 缓冲跟 View 走会导致小窗黑屏，进而 PixelCopy 黑帧、screencap/Binder 间歇失败。
 */
class TwinPhoneEmbedView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : SurfaceView(context, attrs), SurfaceHolder.Callback {

    private var boundSession: String? = null
    private var boundController: ShowerController? = null
    private var scope: CoroutineScope? = null
    private var surfaceJob: Job? = null
    private var surfaceReady = false
    private var frameCapture: (suspend () -> ByteArray?)? = null
    private var fixedBufferW = 0
    private var fixedBufferH = 0

    /** 为 false 时拦截触控（任务 RUNNING、尚未点「我来」）。 */
    private var touchAllowed: (() -> Boolean)? = null
    private var onTouchBlocked: (() -> Unit)? = null
    private var gestureBlocked = false

    init {
        setBackgroundColor(Color.BLACK)
        holder.setFormat(PixelFormat.OPAQUE)
        holder.addCallback(this)
        setZOrderOnTop(false)
        setZOrderMediaOverlay(false)
    }

    fun bind(
        sessionId: String,
        coordinator: VirtualDisplayCoordinator,
        scope: CoroutineScope,
        @Suppress("UNUSED_PARAMETER") statusText: String = "",
    ) {
        boundSession = sessionId
        boundController = coordinator.controller(sessionId)
        this.scope = scope
        TwinPhonePresence.embeddedVisible = true
        applyPreviewVisibility()

        touchAllowed = {
            coordinator.phoneTasks.runningSession.value == null
        }
        onTouchBlocked = { TwinPhonePresence.nudgeTakeOver() }
        val capture: suspend () -> ByteArray? = { capturePng() }
        frameCapture?.let { VirtualDisplayFrameCaptures.unregister(sessionId, it) }
        frameCapture = capture
        VirtualDisplayFrameCaptures.register(sessionId, capture)
        if (surfaceReady) attachSurface()
    }

    fun applyPreviewVisibility() {
        val show = TwinPhonePresence.previewVisible
        visibility = if (show) View.VISIBLE else View.INVISIBLE
        if (show && surfaceReady) attachSurface()
    }

    override fun onVisibilityChanged(changedView: View, visibility: Int) {
        super.onVisibilityChanged(changedView, visibility)
        if (changedView === this && visibility == View.VISIBLE && surfaceReady) {
            attachSurface()
        }
    }

    fun unbind() {
        val session = boundSession
        val capture = frameCapture
        surfaceJob?.cancel()
        surfaceJob = null
        detachSurfaceAsync()
        if (session != null && capture != null) {
            VirtualDisplayFrameCaptures.unregister(session, capture)
        }
        frameCapture = null
        TwinPhonePresence.embeddedVisible = false
        boundSession = null
        boundController = null
        touchAllowed = null
        onTouchBlocked = null
        gestureBlocked = false
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        surfaceReady = true
        attachSurface()
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        // View 变了只重挂 Surface；缓冲仍锁 VD，不跟 View 走。
        if (surfaceReady) attachSurface()
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        surfaceReady = false
        surfaceJob?.cancel()
        surfaceJob = null
        detachSurfaceAsync()
    }

    private fun attachSurface() {
        val controller = boundController ?: return
        val attachScope = scope ?: return
        val surface = holder.surface ?: return
        if (!surface.isValid) return

        val size = controller.getVideoSize()
        if (size != null) {
            val (vw, vh) = size
            if (vw > 0 && vh > 0 && (vw != fixedBufferW || vh != fixedBufferH)) {
                runCatching { holder.setFixedSize(vw, vh) }
                fixedBufferW = vw
                fixedBufferH = vh
            }
        }
        surfaceJob?.cancel()
        surfaceJob = attachScope.launch(Dispatchers.IO) {
            val ok = controller.setDisplaySurface(surface)
            ShowerLog.d(
                TAG,
                "setDisplaySurface ok=$ok vd=${controller.getVideoSize()} " +
                    "buffer=${fixedBufferW}x${fixedBufferH} view=${width}x${height}",
            )
        }
    }

    private fun detachSurfaceAsync() {
        val controller = boundController ?: return
        val attachScope = scope ?: return
        attachScope.launch(Dispatchers.IO) {
            runCatching { controller.setDisplaySurface(null) }
        }
    }

    private suspend fun capturePng(): ByteArray? {
        if (!surfaceReady || width <= 0 || height <= 0) return null
        return withContext(Dispatchers.Main) {
            suspendCancellableCoroutine { cont ->
                val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                try {
                    PixelCopy.request(this@TwinPhoneEmbedView, bitmap, { result ->
                        if (result != PixelCopy.SUCCESS) {
                            bitmap.recycle()
                            cont.resume(null)
                            return@request
                        }
                        // 近全黑帧视为无效，交给 screencap/Binder，避免链式假成功。
                        if (isMostlyBlack(bitmap)) {
                            bitmap.recycle()
                            cont.resume(null)
                            return@request
                        }
                        val bytes = ByteArrayOutputStream().use { out ->
                            bitmap.compress(Bitmap.CompressFormat.PNG, 90, out)
                            out.toByteArray()
                        }
                        bitmap.recycle()
                        cont.resume(bytes)
                    }, handler)
                } catch (_: Throwable) {
                    bitmap.recycle()
                    cont.resume(null)
                }
            }
        }
    }

    private data class Touch(
        val controller: ShowerController,
        val action: Int,
        val x: Float,
        val y: Float,
        val downTime: Long,
        val eventTime: Long,
    )

    private val touches = ArrayDeque<Touch>()
    private val wakeTouch = Channel<Unit>(Channel.CONFLATED)
    private var touchJob: Job? = null

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        val controller = boundController ?: return true
        val attachScope = scope ?: return true
        val (vw, vh) = controller.getVideoSize() ?: return true
        val action = event.actionMasked
        if (action !in setOf(
                MotionEvent.ACTION_DOWN,
                MotionEvent.ACTION_MOVE,
                MotionEvent.ACTION_UP,
                MotionEvent.ACTION_CANCEL,
            )
        ) {
            return true
        }
        if (action == MotionEvent.ACTION_DOWN) {
            val allowed = touchAllowed?.invoke() != false
            gestureBlocked = !allowed
            if (!allowed) {
                onTouchBlocked?.invoke()
                return true
            }
        } else if (gestureBlocked) {
            if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
                gestureBlocked = false
            }
            return true
        }
        val (dx, dy) = mapViewToDisplay(event.x, event.y, width, height, vw, vh)
        val touch = Touch(controller, action, dx, dy, event.downTime, event.eventTime)
        synchronized(touches) {
            if (action == MotionEvent.ACTION_MOVE && touches.lastOrNull()?.action == action) {
                touches.removeLast()
            }
            touches.addLast(touch)
        }
        if (touchJob?.isActive != true) {
            touchJob = attachScope.launch { drainTouches() }
        }
        wakeTouch.trySend(Unit)
        return true
    }

    private suspend fun drainTouches() {
        for (signal in wakeTouch) {
            while (true) {
                val touch = synchronized(touches) { touches.removeFirstOrNull() } ?: break
                touch.controller.injectTouchEvent(
                    action = touch.action,
                    x = touch.x,
                    y = touch.y,
                    downTime = touch.downTime,
                    eventTime = touch.eventTime,
                    pressure = if (touch.action == MotionEvent.ACTION_UP ||
                        touch.action == MotionEvent.ACTION_CANCEL
                    ) {
                        0f
                    } else {
                        1f
                    },
                    size = 1f,
                    metaState = 0,
                    xPrecision = 1f,
                    yPrecision = 1f,
                    deviceId = 0,
                    edgeFlags = 0,
                )
            }
        }
    }

    private companion object {
        const val TAG = "TwinPhoneEmbed"

        /**
         * FIT_CENTER：与 SurfaceView 对 VD 缓冲的系统缩放一致，触控才不偏。
         */
        fun mapViewToDisplay(
            viewX: Float,
            viewY: Float,
            viewW: Int,
            viewH: Int,
            vdW: Int,
            vdH: Int,
        ): Pair<Float, Float> {
            if (viewW <= 0 || viewH <= 0 || vdW <= 1 || vdH <= 1) return 0f to 0f
            val content = fitCenterContent(viewW.toFloat(), viewH.toFloat(), vdW.toFloat(), vdH.toFloat())
            val nx = ((viewX - content.left) / content.width()).coerceIn(0f, 1f)
            val ny = ((viewY - content.top) / content.height()).coerceIn(0f, 1f)
            return nx * (vdW - 1) to ny * (vdH - 1)
        }

        fun fitCenterContent(viewW: Float, viewH: Float, vdW: Float, vdH: Float): RectF {
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

        fun isMostlyBlack(bitmap: Bitmap): Boolean {
            val w = bitmap.width
            val h = bitmap.height
            if (w <= 0 || h <= 0) return true
            val step = max(1, min(w, h) / 32)
            var dark = 0
            var total = 0
            var y = 0
            while (y < h) {
                var x = 0
                while (x < w) {
                    val c = bitmap.getPixel(x, y)
                    val lum = ((c shr 16) and 0xff) + ((c shr 8) and 0xff) + (c and 0xff)
                    if (lum < 30) dark++
                    total++
                    x += step
                }
                y += step
            }
            return total > 0 && dark * 100 / total >= 96
        }
    }
}
