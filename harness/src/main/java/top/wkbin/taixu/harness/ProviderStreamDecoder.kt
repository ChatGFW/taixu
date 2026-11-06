package top.wkbin.taixu.harness

/** Per-response protocol state. No HTTP, coroutine jobs, credentials or persistence. */
internal interface ProviderStreamDecoder {
    /** SSE data after framing. Unknown/malformed payloads do not count as a first protocol event. */
    fun accept(data: String): ProviderDecodeStatus
    /** EOF must validate protocol completion before exposing executable tool calls. Idempotent. */
    fun finish(): ChatResult
}

internal data class ProviderDecodeStatus(val recognized: Boolean, val terminal: Boolean = false)
