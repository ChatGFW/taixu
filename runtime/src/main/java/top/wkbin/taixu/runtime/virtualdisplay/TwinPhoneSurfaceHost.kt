package top.wkbin.taixu.runtime.virtualdisplay

import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import android.widget.FrameLayout
import kotlinx.coroutines.CoroutineScope

/**
 * TextureView 预览 + 不拦截触控的轨迹叠层。Compose Canvas 会抢事件，故用 Android View。
 */
class TwinPhoneSurfaceHost @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : FrameLayout(context, attrs) {

    private val embed = TwinPhoneEmbedView(context)
    private val trails = TwinTouchTrailView(context)

    init {
        addView(embed, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        addView(trails, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
    }

    /** 轨迹层在最上，但触控必须落到 Embed，否则接手时只能点底栏。 */
    override fun dispatchTouchEvent(ev: MotionEvent): Boolean = embed.dispatchTouchEvent(ev)

    fun bind(
        sessionId: String,
        coordinator: VirtualDisplayCoordinator,
        scope: CoroutineScope,
        statusText: String = "",
    ) {
        // AndroidView update 每帧都会进来；embed.bind 内部幂等，避免重复挂 Surface。
        embed.bind(sessionId, coordinator, scope, statusText)
        trails.attach(scope)
        applyPreviewVisibility()
    }

    fun applyPreviewVisibility() {
        embed.applyPreviewVisibility()
        trails.visibility = embed.visibility
    }

    fun unbind() {
        trails.detach()
        embed.unbind()
    }
}
