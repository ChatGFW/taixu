package top.wkbin.taixu.runtime.virtualdisplay

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.SurfaceTexture
import android.view.MotionEvent
import android.view.Surface
import android.view.TextureView
import com.ai.assistance.showerclient.ShowerController
import com.ai.assistance.showerclient.ShowerLog
import com.ai.assistance.showerclient.ShowerVideoRenderer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * 用 TextureView 解码虚拟屏视频，并把触摸按视图→视频坐标回注。
 * SurfaceView 在 TYPE_APPLICATION_OVERLAY 里经常整块黑屏，TextureView 不会。
 */
internal class TouchForwardVideoView(context: Context) :
    TextureView(context),
    TextureView.SurfaceTextureListener {

    private val renderer = ShowerVideoRenderer()
    private var decodeSurface: Surface? = null
    private var attachJob: Job? = null

    var forwardController: ShowerController? = null
    var forwardScopeSupplier: (() -> CoroutineScope?)? = null

    init {
        surfaceTextureListener = this
    }

    override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) {
        val ctrl = forwardController ?: return
        val scope = forwardScopeSupplier?.invoke() ?: return
        attachJob?.cancel()
        attachJob = scope.launch {
            var size = ctrl.getVideoSize()
            var tries = 0
            while (size == null && tries < 50) {
                delay(100)
                tries++
                size = ctrl.getVideoSize()
            }
            val (videoW, videoH) = size ?: run {
                ShowerLog.e(TAG, "TextureView: 等不到视频尺寸，画面会一直是黑的")
                return@launch
            }
            if (!isAvailable || videoW <= 0 || videoH <= 0) return@launch
            surface.setDefaultBufferSize(videoW, videoH)
            val output = Surface(surface)
            decodeSurface = output
            renderer.attach(output, videoW, videoH)
            ctrl.setBinaryHandler { frame -> renderer.onFrame(frame) }
            ShowerLog.d(TAG, "TextureView: 解码表面已接上 ${videoW}x$videoH")
        }
    }

    override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) = Unit

    override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
        attachJob?.cancel()
        attachJob = null
        forwardController?.setBinaryHandler(null)
        renderer.detach()
        decodeSurface?.release()
        decodeSurface = null
        return true
    }

    override fun onSurfaceTextureUpdated(surface: SurfaceTexture) = Unit

    private var downTime = 0L

    /**
     * 公平锁：保证注入顺序 = 事件顺序。每个 MOVE 独立 launch 协程，
     * 无锁时多个注入并发执行会乱序到达虚拟屏，滑动轨迹错乱。
     */
    private val injectMutex = Mutex()

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        val controller = forwardController ?: return true
        val scope = forwardScopeSupplier?.invoke() ?: return true
        val (vw, vh) = controller.getVideoSize() ?: return true
        val w = width.coerceAtLeast(1)
        val h = height.coerceAtLeast(1)
        val x = event.x * (vw - 1) / w
        val y = event.y * (vh - 1) / h

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downTime = event.downTime
                scope.launch { inject(controller, MotionEvent.ACTION_DOWN, x, y, event) }
            }
            MotionEvent.ACTION_MOVE -> {
                scope.launch { inject(controller, MotionEvent.ACTION_MOVE, x, y, event) }
            }
            MotionEvent.ACTION_UP -> {
                scope.launch {
                    inject(controller, MotionEvent.ACTION_UP, x, y, event)
                }
            }
            MotionEvent.ACTION_CANCEL -> {
                scope.launch { inject(controller, MotionEvent.ACTION_CANCEL, x, y, event) }
            }
        }
        return true
    }

    private suspend fun inject(
        controller: ShowerController,
        action: Int,
        x: Float,
        y: Float,
        event: MotionEvent,
    ) {
        injectMutex.withLock {
            runCatching {
                controller.injectTouchEvent(
                    action = action,
                    x = x,
                    y = y,
                    downTime = downTime,
                    eventTime = event.eventTime,
                    pressure = if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) 0f else 1f,
                    size = 1f,
                    metaState = 0,
                    xPrecision = 1f,
                    yPrecision = 1f,
                    deviceId = 0,
                    edgeFlags = 0,
                )
            }.onFailure { ShowerLog.w(TAG, "触摸回传失败: ${it.message}") }
        }
    }

    private companion object {
        const val TAG = "VirtualDisplayHud"
    }
}
