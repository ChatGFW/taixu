package com.ai.assistance.showerclient

import com.ai.assistance.shower.IShowerVideoSink

/** 旧 H.264 帧缓冲（直出 Surface 后极少使用；保留 API 兼容）。 */
internal class ShowerFrameBuffer {
    private val lock = Any()
    private val earlyFrames = ArrayDeque<ByteArray>()
    @Volatile
    private var handler: ((ByteArray) -> Unit)? = null

    val sink: IShowerVideoSink.Stub = object : IShowerVideoSink.Stub() {
        override fun onVideoFrame(data: ByteArray) {
            val current: ((ByteArray) -> Unit)?
            synchronized(lock) {
                current = handler
                if (current == null) {
                    if (earlyFrames.size >= 120) earlyFrames.removeFirst()
                    earlyFrames.addLast(data)
                }
            }
            current?.invoke(data)
        }
    }

    fun setHandler(next: ((ByteArray) -> Unit)?, logTag: String, displayId: Int?) {
        val replay: List<ByteArray>
        synchronized(lock) {
            handler = next
            ShowerLog.d(
                logTag,
                "setBinaryHandler: id=$displayId handlerSet=${next != null}, bufferedFrames=${earlyFrames.size}",
            )
            replay = if (next != null && earlyFrames.isNotEmpty()) {
                val list = earlyFrames.toList()
                earlyFrames.clear()
                list
            } else {
                emptyList()
            }
        }
        if (next != null && replay.isNotEmpty()) {
            ShowerLog.d(logTag, "setBinaryHandler: replaying ${replay.size} buffered frames")
            replay.forEach { frame -> runCatching { next(frame) } }
        }
    }

    fun clear() {
        synchronized(lock) {
            handler = null
            earlyFrames.clear()
        }
    }
}
