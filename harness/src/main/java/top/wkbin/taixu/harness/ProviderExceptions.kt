package top.wkbin.taixu.harness

import java.io.IOException

/** HTTP 429: distinguish temporary throttling from exhausted account quota. */
class LlmRateLimitException(
    message: String,
    val retryAfterSeconds: Long? = null,
    val quotaExhausted: Boolean = false,
    /** A started SSE stream must return to the turn runner to clear partial output before replay. */
    val allowImmediateKeyRotation: Boolean = true,
) : IOException(message)

/** Temporary upstream failure: retry without reducing context. */
class TransientHttpException(
    message: String,
    val httpCode: Int,
    val retryAfterSeconds: Long? = null,
) : IOException(message)

/** Input context or payload overflow: mechanically compact history before replay. */
class LlmContextOverflowException(message: String) : IOException(message)

/** Provider rejected the requested output-token parameter, not the input context. */
class LlmInvalidOutputTokensException(message: String) : IOException(message)

/** HTTP 200 without useful text, reasoning or tool calls must not complete the turn. */
class LlmEmptyResponseException(message: String) : IOException(message)
