package top.wkbin.taixu.harness

import okio.BufferedSource
import kotlin.math.min

/** Bounded reads for non-SSE provider bodies and related stream I/O helpers. */
internal object ProviderStreamIo {
    /**
     * 非 SSE 整段 body 的读取上限（字节）。兼容忽略 `stream:true` 的网关 JSON 补全，
     * 同时防止把巨大 HTML/错误页整页读进堆。
     */
    const val NON_SSE_BODY_LIMIT_BYTES = 512L * 1024L

    /** 至多读取 [maxBytes]；第二项为 true 表示还有未读数据被截断。 */
    fun readUtf8Bounded(source: BufferedSource, maxBytes: Long): Pair<String, Boolean> {
        require(maxBytes > 0L)
        source.request(maxBytes + 1L)
        val available = source.buffer.size
        val truncated = available > maxBytes
        val toRead = min(available, maxBytes)
        return source.readUtf8(toRead) to truncated
    }

    /**
     * 组装非 SSE 响应体：[firstLine] 已由 `readUtf8Line` 读入，计入预算后按余额截断剩余。
     */
    fun captureNonSseBody(source: BufferedSource, firstLine: String): String {
        val lineBudget = min(firstLine.length.toLong(), NON_SSE_BODY_LIMIT_BYTES).toInt()
        val linePart = firstLine.take(lineBudget)
        val remaining = NON_SSE_BODY_LIMIT_BYTES - lineBudget
        val (rest, restTruncated) = if (remaining > 0L) {
            readUtf8Bounded(source, remaining)
        } else {
            "" to source.request(1L)
        }
        val truncated = restTruncated || linePart.length < firstLine.length
        return buildString {
            append(linePart).append('\n').append(rest)
            if (truncated) append("\n…[non-SSE body truncated at $NON_SSE_BODY_LIMIT_BYTES bytes]")
        }
    }
}
