package top.wkbin.taixu.runtime.webchat

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.min

/**
 * Web 协作服务的鉴权核心，主桥与终端桥共用同一份实现。
 *
 * 设计要点（对应评审报告 P0-H1：6 位 PIN 无节流可暴破 → 等同 RCE 面）：
 * - 6 位配对码只用于「人眼核对 + 换取会话令牌」，并由 [SecureRandom] 生成；
 * - 数据面（会话/文件/终端/Agent）只认 256 位会话令牌，枚举空间不可穷举；
 * - 配对码在服务启动时轮换，旧令牌随之一并作废；
 * - 失败按来源地址累计并指数退避锁定，杜绝在线枚举；
 * - 凭证比较统一走 [MessageDigest.isEqual] 恒定时间实现。
 */
internal class WebChatAuth(private val clock: () -> Long = System::currentTimeMillis) {

    /** 展示给用户的一次性配对码；仅在 `/session/bootstrap` 配对时使用。 */
    @Volatile
    private var pairingCode: String = ""

    /** 会话令牌 → 过期时间戳。 */
    private val sessions = ConcurrentHashMap<String, Long>()
    private val failures = ConcurrentHashMap<String, FailureState>()
    private val random = SecureRandom()

    private class FailureState {
        @Volatile var attempts: Int = 0
        @Volatile var blockedUntil: Long = 0L
        @Volatile var lastAttempt: Long = 0L
    }

    val currentPairingCode: String get() = pairingCode

    /** 服务启动：轮换配对码（未指定则重新生成）并作废旧会话令牌。 */
    fun rotate(pin: String? = null): String {
        val next = pin?.trim()?.takeIf { it.length >= PAIRING_CODE_LENGTH } ?: newPairingCode()
        pairingCode = next
        sessions.clear()
        failures.clear()
        return next
    }

    /** 服务停止：清空全部凭证，避免关停后仍持有可用令牌。 */
    fun clear() {
        pairingCode = ""
        sessions.clear()
        failures.clear()
    }

    /**
     * 配对入口。`credential` 可以是配对码，也可以是已持有的会话令牌
     * （页面刷新/重连时直接续用，不必让用户重新输码）。
     */
    fun pair(clientKey: String, credential: String?): PairOutcome {
        val value = credential?.trim().orEmpty()
        if (value.isNotEmpty() && isSessionToken(value)) {
            clearFailures(clientKey)
            return PairOutcome.Paired(value)
        }
        remainingBlock(clientKey)?.let { return PairOutcome.Throttled(it) }
        if (value.isEmpty() || pairingCode.isEmpty() || !constantTimeEquals(value, pairingCode)) {
            recordFailure(clientKey)
            return PairOutcome.InvalidCredential
        }
        clearFailures(clientKey)
        return PairOutcome.Paired(issueSession())
    }

    /** 数据面鉴权：只认会话令牌，配对码本身不能直接访问数据面。 */
    fun authorize(clientKey: String, token: String?): AuthOutcome {
        val value = token?.trim().orEmpty()
        if (value.isNotEmpty() && isSessionToken(value)) {
            clearFailures(clientKey)
            return AuthOutcome.Allowed
        }
        remainingBlock(clientKey)?.let { return AuthOutcome.Throttled(it) }
        recordFailure(clientKey)
        return AuthOutcome.Denied
    }

    // ------------------------------------------------------------- internals

    private fun isSessionToken(token: String): Boolean {
        val now = clock()
        val expiresAt = sessions[token] ?: return false
        if (expiresAt <= now) {
            sessions.remove(token)
            return false
        }
        // 滑动续期：持续使用的会话不会在 12h 处被强制下线，闲置才会过期。
        if (expiresAt - now < SESSION_REFRESH_THRESHOLD_MS) {
            sessions[token] = now + SESSION_TTL_MS
        }
        return true
    }

    private fun issueSession(): String {
        pruneSessions()
        pruneFailures(clock())
        val token = newSessionToken()
        sessions[token] = clock() + SESSION_TTL_MS
        return token
    }

    private fun pruneSessions() {
        val now = clock()
        sessions.keys.toList().forEach { key ->
            val expiresAt = sessions[key] ?: return@forEach
            if (expiresAt <= now) sessions.remove(key)
        }
        while (sessions.size >= MAX_SESSIONS) {
            val oldest = sessions.entries.minByOrNull { it.value } ?: break
            sessions.remove(oldest.key)
        }
    }

    private fun remainingBlock(clientKey: String): Long? {
        val state = failures[clientKey] ?: return null
        val remaining = state.blockedUntil - clock()
        return if (remaining > 0) remaining else null
    }

    private fun recordFailure(clientKey: String) {
        val now = clock()
        pruneFailures(now)
        val state = failures.getOrPut(clientKey) { FailureState() }
        synchronized(state) {
            state.attempts += 1
            state.lastAttempt = now
            if (state.attempts > FREE_ATTEMPTS) {
                // 1s、2s、4s…最多 60s；命中阈值后每次失败都重新计时。
                val exponent = (state.attempts - FREE_ATTEMPTS - 1).coerceAtMost(6)
                state.blockedUntil = now + min(1_000L shl exponent, MAX_BLOCK_MS)
            }
        }
    }

    private fun clearFailures(clientKey: String) {
        if (failures.isNotEmpty()) failures.remove(clientKey)
    }

    /** 仅在缓存膨胀时清理：锁定到期且久未出现的来源可以丢弃。 */
    private fun pruneFailures(now: Long) {
        if (failures.size <= FAILURE_CACHE_LIMIT) return
        failures.entries.toList().forEach { (key, state) ->
            if (state.blockedUntil <= now && now - state.lastAttempt > FAILURE_MEMORY_MS) {
                failures.remove(key)
            }
        }
    }

    private fun newPairingCode(): String = (100000 + random.nextInt(900000)).toString()

    private fun newSessionToken(): String {
        val bytes = ByteArray(SESSION_TOKEN_BYTES)
        random.nextBytes(bytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }

    private fun constantTimeEquals(left: String, right: String): Boolean =
        MessageDigest.isEqual(left.toByteArray(Charsets.UTF_8), right.toByteArray(Charsets.UTF_8))

    private companion object {
        const val PAIRING_CODE_LENGTH = 6

        /** 256 位，Base64URL 后 43 个字符。 */
        const val SESSION_TOKEN_BYTES = 32
        const val SESSION_TTL_MS = 12 * 60 * 60 * 1000L
        /** 剩余寿命低于该阈值时才写回续期，避免每个请求都改 Map。 */
        const val SESSION_REFRESH_THRESHOLD_MS = SESSION_TTL_MS / 2
        const val MAX_SESSIONS = 16

        /** 前 5 次失败不惩罚（够用户手误），之后指数退避。 */
        const val FREE_ATTEMPTS = 5
        const val MAX_BLOCK_MS = 60_000L
        const val FAILURE_CACHE_LIMIT = 256
        const val FAILURE_MEMORY_MS = 15 * 60 * 1000L
    }
}

internal sealed interface PairOutcome {
    data class Paired(val sessionToken: String) : PairOutcome
    data object InvalidCredential : PairOutcome
    data class Throttled(val retryAfterMs: Long) : PairOutcome
}

internal sealed interface AuthOutcome {
    data object Allowed : AuthOutcome
    data object Denied : AuthOutcome
    data class Throttled(val retryAfterMs: Long) : AuthOutcome
}

/** 凭证来源：优先 `Authorization: Bearer`，SSE/下载等无法带头的场景回落到 `?token=`。 */
internal fun AndroidHttpExchange.authToken(): String? {
    val header = requestHeaders.getFirst("Authorization")
        ?.removePrefix("Bearer ")
        ?.trim()
        ?.takeIf(String::isNotEmpty)
    if (header != null) return header
    return getQueryParam(this, "token")?.trim()?.takeIf(String::isNotEmpty)
}

internal fun AndroidHttpExchange.clientKey(): String =
    remoteAddress?.address?.hostAddress ?: "unknown"

/** 数据面统一闸门：通过返回 true，否则已写好 401/429 响应。 */
internal fun WebChatAuth.requireAuthorized(exchange: AndroidHttpExchange): Boolean =
    when (val outcome = authorize(exchange.clientKey(), exchange.authToken())) {
        AuthOutcome.Allowed -> true
        AuthOutcome.Denied -> {
            sendJson(exchange, 401, errorJson("请先使用配对码连接"))
            false
        }
        is AuthOutcome.Throttled -> {
            sendThrottled(exchange, outcome.retryAfterMs)
            false
        }
    }

internal fun sendThrottled(exchange: AndroidHttpExchange, retryAfterMs: Long) {
    val seconds = ((retryAfterMs + 999) / 1000).coerceAtLeast(1)
    exchange.responseHeaders.add("Retry-After", seconds.toString())
    sendJson(exchange, 429, errorJson("配对失败次数过多，请 ${seconds}s 后重试", "too_many_attempts"))
}