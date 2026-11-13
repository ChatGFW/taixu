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
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * TextureView 预览：缓冲必须锁成 **VD 分辨率**（与 VirtualDisplay 一致）。
 *
 * VirtualDisplay 合成进 SurfaceTexture 时会按 consumer 默认缓冲尺寸取样；若缓冲是
 * View 小窗像素，合成器会中心裁切，只露出中间一条。缓冲=VD 后 identity 铺满 View；
 * 外层 Compose 按 VD 宽高比定框，勿再套 FIT 矩阵（会叠在「先铺满」上挤到一角）。
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
    /** 串行挂载/摘除。过期命令直接跳过，不把仍在使用的 Surface 摘掉。 */
    private val surfaceGate = SurfaceAttachGate()

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
        val gen = surfaceGate.bump()
        surfaceJob?.cancel()
        surfaceJob = null
        detachSurfaceAsync(gen, releaseLocal = false, releaseTexture = null)
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
        val gen = surfaceGate.bump()
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
        // 返回 false：本回调拥有 SurfaceTexture。先排队摘掉 VD，再 release。
        // 不放进 surfaceJob，避免 unbind 取消掉这次 release。
        attachScope.launch(Dispatchers.IO) {
            var released = false
            try {
                surfaceGate.detachThenRelease(
                    gen,
                    detach = { runCatching { controller.setDisplaySurface(null) } },
                ) {
                    releaseDestroyedSurface(local, surface)
                    released = true
                }
            } finally {
                if (!released) releaseDestroyedSurface(local, surface)
            }
        }
        return false
    }

    override fun onSurfaceTextureUpdated(surface: SurfaceTexture) = Unit

    private fun prepareAndAttach(surfaceTexture: SurfaceTexture? = this.surfaceTexture) {
        val controller = boundController ?: return
        val attachScope = scope ?: return
        val st = surfaceTexture ?: return
        // 必须等 layout，否则 width/height=0，外层宽高比框尚未落地。
        if (width <= 0 || height <= 0) return

        val size = controller.getVideoSize()
        val rawW = size?.first?.takeIf { it > 0 }
        val rawH = size?.second?.takeIf { it > 0 }
        if (rawW == null || rawH == null) {
            // 建屏尚未回写尺寸：稍后重试，切勿用 View 像素当缓冲（会触发合成中心裁切）。
            surfaceJob?.cancel()
            surfaceJob = attachScope.launch {
                kotlinx.coroutines.delay(120)
                if (surfaceReady && boundController === controller) {
                    withContext(Dispatchers.Main) { prepareAndAttach() }
                }
            }
            return
        }
        // 与 shower-server DisplaySession 16 对齐，避免缓冲与真实 VD 差几像素。
        val bw = TwinPhoneEmbedSupport.alignBlock(rawW)
        val bh = TwinPhoneEmbedSupport.alignBlock(rawH)

        val bufferChanged = bw != fixedBufferW || bh != fixedBufferH
        // TextureView.onSizeChanged 会先把默认缓冲重设为 View 尺寸，再通知 listener。
        // VD 尺寸缓存不代表 SurfaceTexture 当前尺寸；每次挂载/尺寸回调都必须恢复。
        st.setDefaultBufferSize(bw, bh)
        if (bufferChanged) {
            fixedBufferW = bw
            fixedBufferH = bh
            displaySurface?.release()
            displaySurface = null
            TwinPhonePresence.updateContentAspect(bw.toFloat() / bh.toFloat())
            ShowerLog.d(TAG, "setDefaultBufferSize vd=${bw}x${bh} view=${width}x${height}")
        }
        // identity：整帧铺满 View；外层已按 VD 宽高比定框。
        setTransform(android.graphics.Matrix())

        val existing = displaySurface
        val surface = if (existing != null && existing.isValid) {
            existing
        } else {
            existing?.release()
            Surface(st).also { displaySurface = it }
        }
        if (!surface.isValid) return

        val gen = surfaceGate.bump()
        surfaceJob?.cancel()
        surfaceJob = attachScope.launch(Dispatchers.IO) {
            surfaceGate.runIfCurrent(gen) {
                val ok = controller.setDisplaySurface(surface)
                ShowerLog.d(
                    TAG,
                    "setDisplaySurface ok=$ok vd=${controller.getVideoSize()} " +
                        "buffer=${fixedBufferW}x${fixedBufferH} view=${width}x${height}",
                )
            }
        }
    }

    private fun detachSurfaceAsync(
        gen: Int,
        releaseLocal: Boolean,
        releaseTexture: SurfaceTexture?,
    ) {
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
            releaseTexture?.let { runCatching { it.release() } }
            return
        }
        attachScope.launch(Dispatchers.IO) {
            surfaceGate.runIfCurrent(gen) {
                runCatching { controller.setDisplaySurface(null) }
            }
            if (releaseLocal || releaseTexture != null) {
                withContext(NonCancellable + Dispatchers.Main) {
                    if (releaseLocal) local?.release()
                    releaseTexture?.let { runCatching { it.release() } }
                }
            }
        }
    }

    private suspend fun releaseDestroyedSurface(local: Surface?, texture: SurfaceTexture) {
        withContext(NonCancellable + Dispatchers.Main) {
            runCatching { local?.release() }
            runCatching { texture.release() }
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
