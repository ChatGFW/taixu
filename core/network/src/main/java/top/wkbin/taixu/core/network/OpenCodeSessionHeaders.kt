package top.wkbin.taixu.core.network

import java.util.UUID
import okhttp3.Request

/** Attach stable routing metadata to OpenCode inference and connection probes. */
fun Request.withOpenCodeSessionHeaders(sessionKey: String? = null): Request {
    if (url.host != "opencode.ai" && header("x-opencode-session") == null) return this
    // Derivation survives restarts and accepts legacy/non-UUID conversation keys.
    val id = sessionKey?.takeIf(String::isNotBlank)?.let {
        UUID.nameUUIDFromBytes("taixu:opencode:$it".toByteArray(Charsets.UTF_8))
    } ?: UUID.randomUUID()
    val userAgent = header("User-Agent")
    return newBuilder()
        .header("x-opencode-session", id.toString())
        .apply { if (userAgent.isNullOrBlank()) header("User-Agent", "TaiXu") }
        .build()
}
