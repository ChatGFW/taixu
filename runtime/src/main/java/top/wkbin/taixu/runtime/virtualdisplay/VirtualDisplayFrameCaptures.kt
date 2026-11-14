package top.wkbin.taixu.runtime.virtualdisplay

import java.util.concurrent.ConcurrentHashMap

/**
 * 各会话当前可见预览（悬浮窗 / 分身 Tab）登记的「从解码表面抓 PNG」回调。
 * Agent 优先走这里，避开慢且易踩 Binder 上限的 screencap。
 */
internal object VirtualDisplayFrameCaptures {
    private val captures = ConcurrentHashMap<String, suspend () -> ByteArray?>()

    fun register(sessionId: String, capture: suspend () -> ByteArray?) {
        captures[sessionId] = capture
    }

    fun unregister(sessionId: String, capture: suspend () -> ByteArray?) {
        captures.computeIfPresent(sessionId) { _, current ->
            if (current === capture) null else current
        }
    }

    suspend fun capture(sessionId: String): ByteArray? =
        captures[sessionId]?.invoke()?.takeIf { it.isNotEmpty() }
}
