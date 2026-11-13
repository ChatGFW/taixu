package top.wkbin.taixu.runtime.virtualdisplay

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.SurfaceTexture
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.Surface
import android.view.TextureView
import android.view.View
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
import kotlinx.coroutines.withContext

/**
 * TextureView 预览：缓冲用 [SurfaceTexture.setDefaultBufferSize] 锁成 VD 分辨率，
 * 再经 Binder [ShowerController.setDisplaySurface] 直挂。
 *
 * 默认铺满 View（identity transform）；外层 Compose 已按 VD 宽高比定框，
 * 勿再做 FIT_CENTER 矩阵，否则小窗会出现「画面挤在一角 + 大块黑边」。
 */
class TwinPhoneEmbedView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : TextureView(context, attrs), TextureView.SurfaceTextureListener {

    private var boundSession: String? = null
    private var boundController: ShowerController? = null
    private var scope: CoroutineScope? = null
    private var surfaceJob: Job? = null
    private var surfaceReady = false
    private var frameCapture: (suspend () -> ByteArray?)? = null
    private var displaySurface: Surface? = null
    private var fixedBufferW = 0
    private var fixedBufferH = 0

    private var touchAllowed: (() -> Boolean)? = null
    private var onTouchBlocked: (() -> Unit)? = null
    private var gestureBlocked = false

    init {
        surfaceTextureListener = this
        isOpaque = true
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
        if (surfaceReady) prepareAndAttach()
    }

    fun applyPreviewVisibility() {
        val show = TwinPhonePresence.previewVisible
        visibility = if (show) View.VISIBLE else View.INVISIBLE
        if (show && surfaceReady) prepareAndAttach()
    }

    override fun onVisibilityChanged(changedView: View, visibility: Int) {
        super.onVisibilityChanged(changedView, visibility)
        if (changedView === this && visibility == View.VISIBLE && surfaceReady) {
            prepareAndAttach()
        }
    }

    fun unbind() {
        val session = boundSession
        val capture = frameCapture
        surfaceJob?.cancel()
        surfaceJob = null
        detachSurfaceAsync(releaseLocal = false)
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

    override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) {
        surfaceReady = true
        prepareAndAttach(surfaceTexture = surface)
    }

    override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) {
        if (surfaceReady) prepareAndAttach(surfaceTexture = surface)
    }

    override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
        surfaceReady = false
        surfaceJob?.cancel()
        surfaceJob = null
        detachSurfaceAsync(releaseLocal = true)
        return true
    }

    override fun onSurfaceTextureUpdated(surface: SurfaceTexture) = Unit

    private fun prepareAndAttach(surfaceTexture: SurfaceTexture? = this.surfaceTexture) {
        val controller = boundController ?: return
        val attachScope = scope ?: return
        val st = surfaceTexture ?: return
        val size = controller.getVideoSize()
        val vw = size?.first?.takeIf { it > 0 } ?: width.takeIf { it > 0 } ?: return
        val vh = size?.second?.takeIf { it > 0 } ?: height.takeIf { it > 0 } ?: return

        if (vw != fixedBufferW || vh != fixedBufferH) {
            st.setDefaultBufferSize(vw, vh)
            fixedBufferW = vw
            fixedBufferH = vh
            ShowerLog.d(TAG, "setDefaultBufferSize ${vw}x${vh}")
        }
        // identity：铺满 View，与外层宽高比框一致。
        setTransform(android.graphics.Matrix())

        val existing = displaySurface
        val surface = if (existing != null && existing.isValid) {
            existing
        } else {
            existing?.release()
            Surface(st).also { displaySurface = it }
        }
        if (!surface.isValid) return

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

    private fun detachSurfaceAsync(releaseLocal: Boolean) {
        val controller = boundController
        val attachScope = scope
        val local = displaySurface
        if (releaseLocal) {
            displaySurface = null
            fixedBufferW = 0
            fixedBufferH = 0
        }
        if (controller == null || attachScope == null) {
            if (releaseLocal) local?.release()
            return
        }
        attachScope.launch(Dispatchers.IO) {
            runCatching { controller.setDisplaySurface(null) }
            if (releaseLocal) local?.release()
        }
    }

    private suspend fun capturePng(): ByteArray? {
        if (!surfaceReady || width <= 0 || height <= 0) return null
        return withContext(Dispatchers.Main) {
            val bitmap = runCatching { getBitmap(width, height) }.getOrNull() ?: return@withContext null
            try {
                if (isMostlyBlack(bitmap)) return@withContext null
                ByteArrayOutputStream().use { out ->
                    bitmap.compress(Bitmap.CompressFormat.PNG, 90, out)
                    out.toByteArray()
                }
            } finally {
                bitmap.recycle()
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
        if (action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_POINTER_DOWN) {
            val allowed = touchAllowed?.invoke() != false
            if (action == MotionEvent.ACTION_DOWN) {
                gestureBlocked = !allowed
            }
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
        // 多指：映射全部指针后原样注入 MotionEvent。
        if (event.pointerCount > 1 ||
            action == MotionEvent.ACTION_POINTER_DOWN ||
            action == MotionEvent.ACTION_POINTER_UP
        ) {
            val mapped = mapMotionEvent(event, width, height, vw, vh) ?: return true
            attachScope.launch(Dispatchers.IO) {
                try {
                    controller.injectMotionEvent(mapped)
                } finally {
                    mapped.recycle()
                }
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

        /** 铺满映射：View 坐标线性落到 VD。 */
        fun mapViewToDisplay(
            viewX: Float,
            viewY: Float,
            viewW: Int,
            viewH: Int,
            vdW: Int,
            vdH: Int,
        ): Pair<Float, Float> {
            if (viewW <= 0 || viewH <= 0 || vdW <= 1 || vdH <= 1) return 0f to 0f
            val nx = (viewX / viewW).coerceIn(0f, 1f)
            val ny = (viewY / viewH).coerceIn(0f, 1f)
            return nx * (vdW - 1) to ny * (vdH - 1)
        }

        fun mapMotionEvent(
            event: MotionEvent,
            viewW: Int,
            viewH: Int,
            vdW: Int,
            vdH: Int,
        ): MotionEvent? {
            val count = event.pointerCount
            if (count <= 0) return null
            val props = Array(count) { MotionEvent.PointerProperties() }
            val coords = Array(count) { MotionEvent.PointerCoords() }
            for (i in 0 until count) {
                event.getPointerProperties(i, props[i])
                event.getPointerCoords(i, coords[i])
                val (dx, dy) = mapViewToDisplay(coords[i].x, coords[i].y, viewW, viewH, vdW, vdH)
                coords[i].x = dx
                coords[i].y = dy
            }
            return MotionEvent.obtain(
                event.downTime,
                event.eventTime,
                event.action,
                count,
                props,
                coords,
                event.metaState,
                event.buttonState,
                event.xPrecision,
                event.yPrecision,
                event.deviceId,
                event.edgeFlags,
                event.source,
                event.flags,
            )
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
