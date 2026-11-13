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
 * TextureView 预览：缓冲跟 **View 像素** 对齐（勿锁成 VD 全分辨率）。
 * 全分辨率 buffer 塞进小窗/大窗会按中心裁切，只露出画面中间一条。
 * VD 输出缩放到 View 大小后 identity 铺满；截图仍按 VD 尺寸取样以对齐点击坐标。
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
    /** 挂载/拆卸代数：忽略过期 Binder 结果，避免把废弃 Surface 挂回。 */
    private var attachGeneration = 0

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
        val controller = coordinator.controller(sessionId)
        val same =
            boundSession == sessionId &&
                boundController === controller &&
                this.scope === scope
        boundSession = sessionId
        boundController = controller
        this.scope = scope
        TwinPhonePresence.embeddedVisible = true
        // 自动操控中锁触控；暂停/接手/确认等待时必须放开，方便登录验证码等人工操作。
        touchAllowed = {
            coordinator.phoneTasks.waiting.value != null ||
                coordinator.phoneTasks.runningSession.value == null
        }
        onTouchBlocked = { TwinPhonePresence.nudgeTakeOver() }
        if (!same) {
            val capture: suspend () -> ByteArray? = { capturePng() }
            frameCapture?.let { VirtualDisplayFrameCaptures.unregister(sessionId, it) }
            frameCapture = capture
            VirtualDisplayFrameCaptures.register(sessionId, capture)
        }
        applyPreviewVisibility()
        if (!same && surfaceReady) prepareAndAttach()
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
        attachGeneration++
        surfaceJob?.cancel()
        surfaceJob = null
        detachSurfaceAsync(releaseLocal = false, releaseTexture = null)
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
        val gen = ++attachGeneration
        surfaceJob?.cancel()
        surfaceJob = null
        val controller = boundController
        val attachScope = scope
        val local = displaySurface
        displaySurface = null
        fixedBufferW = 0
        fixedBufferH = 0
        if (controller == null || attachScope == null) {
            local?.release()
            return true
        }
        // 返回 false：先从 VD 摘掉，再自行 release，避免系统抢先释放后异步 setSurface(null)。
        surfaceJob = attachScope.launch(Dispatchers.IO) {
            runCatching { controller.setDisplaySurface(null) }
            withContext(Dispatchers.Main) {
                local?.release()
                if (gen == attachGeneration) {
                    runCatching { surface.release() }
                }
            }
        }
        return false
    }

    override fun onSurfaceTextureUpdated(surface: SurfaceTexture) = Unit

    private fun prepareAndAttach(surfaceTexture: SurfaceTexture? = this.surfaceTexture) {
        val controller = boundController ?: return
        val attachScope = scope ?: return
        val st = surfaceTexture ?: return
        // 必须等 layout；缓冲跟 View 像素对齐，让 VD 整帧缩进 Surface。
        // 勿用 VD 全分辨率 buffer：TextureView 会按中心裁切，小窗/大窗只剩中间一条。
        val bw = width.takeIf { it > 0 } ?: return
        val bh = height.takeIf { it > 0 } ?: return
        // 展开动画每帧改尺寸；≥8px 才重建，避免狂刷 setSurface。
        val bufferChanged =
            fixedBufferW == 0 ||
                fixedBufferH == 0 ||
                kotlin.math.abs(bw - fixedBufferW) >= 8 ||
                kotlin.math.abs(bh - fixedBufferH) >= 8
        if (bufferChanged) {
            st.setDefaultBufferSize(bw, bh)
            fixedBufferW = bw
            fixedBufferH = bh
            displaySurface?.release()
            displaySurface = null
            ShowerLog.d(TAG, "setDefaultBufferSize view=${bw}x${bh} vd=${controller.getVideoSize()}")
        }
        // identity：缓冲已是 View 大小，整窗铺满；外层 Compose 按 VD 宽高比定框。
        setTransform(android.graphics.Matrix())

        val existing = displaySurface
        val surface = if (existing != null && existing.isValid) {
            existing
        } else {
            existing?.release()
            Surface(st).also { displaySurface = it }
        }
        if (!surface.isValid) return

        val gen = ++attachGeneration
        surfaceJob?.cancel()
        surfaceJob = attachScope.launch(Dispatchers.IO) {
            if (gen != attachGeneration) return@launch
            val ok = controller.setDisplaySurface(surface)
            if (gen != attachGeneration) {
                if (displaySurface === surface) {
                    runCatching { controller.setDisplaySurface(null) }
                }
                return@launch
            }
            controller.getVideoSize()?.let { (vw, vh) ->
                if (vw > 0 && vh > 0) {
                    TwinPhonePresence.updateContentAspect(vw.toFloat() / vh.toFloat())
                }
            }
            ShowerLog.d(
                TAG,
                "setDisplaySurface ok=$ok vd=${controller.getVideoSize()} " +
                    "buffer=${fixedBufferW}x${fixedBufferH} view=${width}x${height}",
            )
        }
    }

    private fun detachSurfaceAsync(releaseLocal: Boolean, releaseTexture: SurfaceTexture?) {
        val controller = boundController
        val attachScope = scope
        val local = displaySurface
        val gen = attachGeneration
        if (releaseLocal) {
            displaySurface = null
            fixedBufferW = 0
            fixedBufferH = 0
        }
        if (controller == null || attachScope == null) {
            if (releaseLocal) local?.release()
            releaseTexture?.let { runCatching { it.release() } }
            return
        }
        attachScope.launch(Dispatchers.IO) {
            if (gen != attachGeneration && !releaseLocal) return@launch
            runCatching { controller.setDisplaySurface(null) }
            withContext(Dispatchers.Main) {
                if (releaseLocal) local?.release()
                releaseTexture?.let { runCatching { it.release() } }
            }
        }
    }

    private suspend fun capturePng(): ByteArray? {
        if (!surfaceReady) return null
        val size = boundController?.getVideoSize() ?: return null
        val bw = size.first.takeIf { it > 0 } ?: return null
        val bh = size.second.takeIf { it > 0 } ?: return null
        return withContext(Dispatchers.Main) {
            // 必须按 VD 分辨率取图，否则 PhoneAgent 会把小窗像素当成虚拟屏坐标注入。
            val bitmap = runCatching { getBitmap(bw, bh) }.getOrNull() ?: return@withContext null
            try {
                if (TwinPhoneEmbedSupport.isMostlyBlack(bitmap)) return@withContext null
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
            if (action == MotionEvent.ACTION_DOWN) gestureBlocked = !allowed
            if (!allowed) {
                onTouchBlocked?.invoke()
                return true
            }
        } else if (gestureBlocked) {
            if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) gestureBlocked = false
            return true
        }
        if (event.pointerCount > 1 ||
            action == MotionEvent.ACTION_POINTER_DOWN ||
            action == MotionEvent.ACTION_POINTER_UP
        ) {
            val mapped = TwinPhoneEmbedSupport.mapMotionEvent(event, width, height, vw, vh) ?: return true
            attachScope.launch(Dispatchers.IO) {
                try {
                    controller.injectMotionEvent(mapped)
                } finally {
                    mapped.recycle()
                }
            }
            return true
        }
        val (dx, dy) = TwinPhoneEmbedSupport.mapViewToDisplay(event.x, event.y, width, height, vw, vh)
        val touch = Touch(controller, action, dx, dy, event.downTime, event.eventTime)
        synchronized(touches) {
            if (action == MotionEvent.ACTION_MOVE && touches.lastOrNull()?.action == action) touches.removeLast()
            touches.addLast(touch)
        }
        if (touchJob?.isActive != true) touchJob = attachScope.launch { drainTouches() }
        wakeTouch.trySend(Unit)
        return true
    }

    private suspend fun drainTouches() {
        for (signal in wakeTouch) {
            while (true) {
                val touch = synchronized(touches) { touches.removeFirstOrNull() } ?: break
                val up = touch.action == MotionEvent.ACTION_UP || touch.action == MotionEvent.ACTION_CANCEL
                touch.controller.injectTouchEvent(
                    action = touch.action, x = touch.x, y = touch.y,
                    downTime = touch.downTime, eventTime = touch.eventTime,
                    pressure = if (up) 0f else 1f, size = 1f, metaState = 0,
                    xPrecision = 1f, yPrecision = 1f, deviceId = 0, edgeFlags = 0,
                )
            }
        }
    }

    private companion object {
        const val TAG = "TwinPhoneEmbed"
    }
}
