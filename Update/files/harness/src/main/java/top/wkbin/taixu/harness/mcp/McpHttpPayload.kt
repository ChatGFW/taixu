package top.wkbin.taixu.harness.mcp

import java.io.File
import okhttp3.Response

/** 工具目录必须完整解析；工具结果走更小的内联阈值转存。两者共用 DEFAULT_MAX_SPILL_BYTES 的 16MB 硬上限。 */
internal fun readMcpHttpPayload(
    response: Response,
    isToolCall: Boolean,
    spillDirectory: File?,
): McpResponseSizeLimiter.Payload = McpResponseSizeLimiter.readResponse(
    response,
    maxInlineBytes = if (isToolCall) McpResponseSizeLimiter.DEFAULT_MAX_INLINE_BYTES
        else McpResponseSizeLimiter.DEFAULT_MAX_SPILL_BYTES,
    spillDirectory = spillDirectory,
)
