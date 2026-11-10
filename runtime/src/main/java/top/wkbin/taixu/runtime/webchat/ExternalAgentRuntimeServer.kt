package top.wkbin.taixu.runtime.webchat

import java.net.InetSocketAddress
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Semaphore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import top.wkbin.taixu.core.common.logging.AppLogger

const val DEFAULT_EXTERNAL_AGENT_RUNTIME_PORT = 8901
const val MAX_EXTERNAL_AGENT_CLAIM_WAIT_MS = 30_000L
const val EXTERNAL_AGENT_CLAIM_POLL_MS = 250L
const val MAX_EXTERNAL_AGENT_CLAIM_INFLIGHT = 32
const val MAX_EXTERNAL_AGENT_CLAIM_PER_IP = 4
/** Runtime routes only need small JSON bodies; do not inherit WebChat's 32MB upload budget. */
const val MAX_EXTERNAL_AGENT_REQUEST_BODY_BYTES = 256 * 1024

data class ExternalAgentRuntimeServerStatus(
    val isRunning: Boolean = false,
    val port: Int = DEFAULT_EXTERNAL_AGENT_RUNTIME_PORT,
    val localIp: String = "127.0.0.1",
) {
    val accessUrl: String get() = "http://$localIp:$port"
    val claimUrl: String get() = "$accessUrl/runtime/v1/events/claim"
}

/**
 * LAN endpoint for EXTERNAL bots. Authentication is only the member runtime token
 * (`Authorization: Bearer tx_rt_…`); WebChat PIN is never accepted here.
 */
class ExternalAgentRuntimeServer(
    private val gateway: ExternalAgentRuntimeGateway,
    private val logger: AppLogger,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var httpServer: AndroidHttpServer? = null
    private val _status = MutableStateFlow(ExternalAgentRuntimeServerStatus())
    val status: StateFlow<ExternalAgentRuntimeServerStatus> = _status.asStateFlow()
    private val claimGlobal = Semaphore(MAX_EXTERNAL_AGENT_CLAIM_INFLIGHT)
    private val claimPerIp = ConcurrentHashMap<String, Semaphore>()

    @Synchronized
    fun start(port: Int = DEFAULT_EXTERNAL_AGENT_RUNTIME_PORT): Boolean {
        if (_status.value.isRunning) return true
        return runCatching {
            val server = AndroidHttpServer.create(
                InetSocketAddress("0.0.0.0", port),
                0,
                maxBodyBytes = MAX_EXTERNAL_AGENT_REQUEST_BODY_BYTES,
            )
            server.createContext("/runtime/v1/health", HealthHandler())
            server.createContext("/runtime/v1/meta", MetaHandler())
            server.createContext("/runtime/v1/me", MeHandler())
            server.createContext("/runtime/v1/events/pending", PendingHandler())
            server.createContext("/runtime/v1/events/claim", ClaimHandler())
            server.createContext("/runtime/v1/events/ack", AckHandler())
            server.createContext("/runtime/v1/events/release", ReleaseHandler())
            server.createContext("/runtime/v1/events/heartbeat", HeartbeatHandler())
            server.createContext("/runtime/v1/groups", GroupsHandler())
            server.start()
            httpServer = server
            _status.value = ExternalAgentRuntimeServerStatus(true, port, resolveWebChatLanIpv4())
            true
        }.onFailure {
            logger.e("[ExternalAgentRuntime] 启动失败", it)
            stop()
        }.getOrDefault(false)
    }

    @Synchronized
    fun stop() {
        _status.value = ExternalAgentRuntimeServerStatus(port = _status.value.port)
        runCatching { httpServer?.stop(0) }
        httpServer = null
        // UI 只调 stop()：必须取消长轮询 claim，避免停服后仍 claimNext 成功却写不回客户端。
        scope.coroutineContext[Job]?.cancelChildren()
    }

    fun close() {
        stop()
        scope.cancel()
    }

    private fun launchRequest(exchange: AndroidHttpExchange, block: suspend () -> Unit) {
        scope.launch {
            try {
                block()
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (failure: Throwable) {
                logger.e("[ExternalAgentRuntime] ${exchange.requestURI.path}", failure)
                if (!exchange.isResponseStarted) {
                    runCatching { sendJson(exchange, failureStatus(failure), errorJson(failure.message ?: "请求失败")) }
                }
            } finally {
                runCatching { exchange.close() }
            }
        }
    }

    private fun failureStatus(failure: Throwable): Int = when (failure) {
        is IllegalArgumentException -> 400
        is SecurityException -> 401
        else -> 500
    }

    private fun bearerToken(exchange: AndroidHttpExchange): String {
        val header = exchange.requestHeaders.getFirst("Authorization").orEmpty()
        if (!header.startsWith("Bearer ", ignoreCase = true)) {
            throw SecurityException("需要 Authorization: Bearer 外部运行时令牌")
        }
        val token = header.substringAfter(' ').trim()
        if (!token.startsWith("tx_rt_")) throw SecurityException("需要外部运行时令牌")
        return token
    }

    private suspend fun requireAuthorized(token: String): ExternalAgentRuntimeIdentity =
        gateway.authorize(token) ?: throw SecurityException("令牌无效或已撤销")

    private fun clientIp(exchange: AndroidHttpExchange): String =
        exchange.remoteAddress?.address?.hostAddress ?: "unknown"

    private fun handlePreflight(exchange: AndroidHttpExchange): Boolean {
        if (exchange.requestMethod != "OPTIONS") return false
        applyRuntimeCors(exchange)
        exchange.sendResponseHeaders(204, -1)
        exchange.close()
        return true
    }

    private fun applyRuntimeCors(exchange: AndroidHttpExchange) {
        exchange.responseHeaders.add("Access-Control-Allow-Origin", "*")
        exchange.responseHeaders.add("Access-Control-Allow-Headers", "Content-Type, Authorization")
        exchange.responseHeaders.add("Access-Control-Allow-Methods", "GET, POST, OPTIONS")
    }

    private inner class HealthHandler : AndroidHttpHandler {
        override fun handle(exchange: AndroidHttpExchange) = launchRequest(exchange) {
            if (handlePreflight(exchange)) return@launchRequest
            if (exchange.requestMethod != "GET") {
                sendJson(exchange, 405, errorJson("仅支持 GET")); return@launchRequest
            }
            sendJson(exchange, 200, externalAgentRuntimeHealthJson())
        }
    }

    private inner class MetaHandler : AndroidHttpHandler {
        override fun handle(exchange: AndroidHttpExchange) = launchRequest(exchange) {
            if (handlePreflight(exchange)) return@launchRequest
            if (exchange.requestMethod != "GET") {
                sendJson(exchange, 405, errorJson("仅支持 GET")); return@launchRequest
            }
            sendJson(exchange, 200, externalAgentRuntimeMetaJson())
        }
    }

    private inner class MeHandler : AndroidHttpHandler {
        override fun handle(exchange: AndroidHttpExchange) = launchRequest(exchange) {
            if (handlePreflight(exchange)) return@launchRequest
            if (exchange.requestMethod != "GET") {
                sendJson(exchange, 405, errorJson("仅支持 GET")); return@launchRequest
            }
            val token = bearerToken(exchange)
            requireAuthorized(token)
            val session = gateway.session(token) ?: throw SecurityException("令牌无效或已撤销")
            sendJson(exchange, 200, buildJsonObject {
                put("botId", session.botId)
                put("label", session.label)
                put("pendingCount", session.pendingCount)
                put("claimLeaseTtlMs", session.claimLeaseTtlMs)
                putJsonArray("groups") {
                    session.groups.forEach { group ->
                        addJsonObject {
                            put("id", group.id)
                            put("name", group.name)
                        }
                    }
                }
            })
        }
    }

    private inner class PendingHandler : AndroidHttpHandler {
        override fun handle(exchange: AndroidHttpExchange) = launchRequest(exchange) {
            if (handlePreflight(exchange)) return@launchRequest
            if (exchange.requestMethod != "GET") {
                sendJson(exchange, 405, errorJson("仅支持 GET")); return@launchRequest
            }
            val token = bearerToken(exchange)
            requireAuthorized(token)
            val limit = getQueryParam(exchange, "limit")?.toIntOrNull()
                ?: ExternalAgentRuntimeServiceDefaults.DEFAULT_PENDING_PREVIEW_LIMIT
            val pending = gateway.peekPending(token, limit)
            sendJson(exchange, 200, buildJsonObject {
                put("count", pending.size)
                putJsonArray("pending") {
                    pending.forEach { item ->
                        addJsonObject {
                            put("deliveryId", item.deliveryId)
                            put("groupId", item.groupId)
                            put("sourceMessageId", item.sourceMessageId)
                            put("contentPreview", item.contentPreview)
                            put("createdAt", item.createdAt)
                        }
                    }
                }
            })
        }
    }

    private inner class ClaimHandler : AndroidHttpHandler {
        override fun handle(exchange: AndroidHttpExchange) = launchRequest(exchange) {
            if (handlePreflight(exchange)) return@launchRequest
            if (exchange.requestMethod != "POST") {
                sendJson(exchange, 405, errorJson("仅支持 POST")); return@launchRequest
            }
            val token = bearerToken(exchange)
            // 无效令牌不得进入 waitMs 长轮询（否则每 250ms 打一次库）。
            requireAuthorized(token)
            val waitMs = getQueryParam(exchange, "waitMs")?.toLongOrNull()
                ?.coerceIn(0L, MAX_EXTERNAL_AGENT_CLAIM_WAIT_MS) ?: 0L
            handleExternalAgentClaimRequest(
                exchange = exchange,
                gateway = gateway,
                token = token,
                waitMs = waitMs,
                serverRunning = { _status.value.isRunning },
                clientIp = clientIp(exchange),
                claimGlobal = claimGlobal,
                claimPerIp = claimPerIp,
                sendJson = ::sendJson,
                errorJson = { message, code -> errorJson(message, code) },
            )
        }
    }

    private inner class AckHandler : AndroidHttpHandler {
        override fun handle(exchange: AndroidHttpExchange) = launchRequest(exchange) {
            if (handlePreflight(exchange)) return@launchRequest
            if (exchange.requestMethod != "POST") {
                sendJson(exchange, 405, errorJson("仅支持 POST")); return@launchRequest
            }
            val token = bearerToken(exchange)
            requireAuthorized(token)
            handleExternalAgentAckRequest(
                exchange = exchange,
                gateway = gateway,
                token = token,
                body = requestJson(exchange),
                sendJson = ::sendJson,
                errorJson = { message, code -> errorJson(message, code) },
            )
        }
    }

    private inner class ReleaseHandler : AndroidHttpHandler {
        override fun handle(exchange: AndroidHttpExchange) = launchRequest(exchange) {
            if (handlePreflight(exchange)) return@launchRequest
            if (exchange.requestMethod != "POST") {
                sendJson(exchange, 405, errorJson("仅支持 POST")); return@launchRequest
            }
            val token = bearerToken(exchange)
            requireAuthorized(token)
            val body = requestJson(exchange)
            val ok = gateway.release(
                rawToken = token,
                deliveryId = body["deliveryId"]?.jsonPrimitive?.content.orEmpty(),
                claimNonce = body["claimNonce"]?.jsonPrimitive?.content.orEmpty(),
            )
            if (!ok) sendJson(exchange, 409, errorJson("释放被拒绝（租约不匹配或已结算）", "lease_mismatch"))
            else sendJson(exchange, 200, buildJsonObject { put("ok", true) })
        }
    }

    private inner class HeartbeatHandler : AndroidHttpHandler {
        override fun handle(exchange: AndroidHttpExchange) = launchRequest(exchange) {
            if (handlePreflight(exchange)) return@launchRequest
            if (exchange.requestMethod != "POST") {
                sendJson(exchange, 405, errorJson("仅支持 POST")); return@launchRequest
            }
            val token = bearerToken(exchange)
            requireAuthorized(token)
            val leases = parseExternalAgentHeartbeatLeases(requestJson(exchange))
            if (leases.isEmpty()) {
                sendJson(exchange, 400, errorJson("需要 leases 或 deliveryId/claimNonce", "invalid_lease_payload"))
                return@launchRequest
            }
            val renewed = gateway.heartbeat(token, leases)
            if (renewed == 0) sendJson(exchange, 409, errorJson("续租被拒绝（租约不匹配或已结算）", "lease_mismatch"))
            else sendJson(exchange, 200, externalAgentHeartbeatOkJson(renewed, leases.size))
        }
    }

    private inner class GroupsHandler : AndroidHttpHandler {
        override fun handle(exchange: AndroidHttpExchange) = launchRequest(exchange) {
            if (handlePreflight(exchange)) return@launchRequest
            val token = bearerToken(exchange)
            requireAuthorized(token)
            handleExternalAgentGroupsRequest(
                exchange = exchange,
                gateway = gateway,
                token = token,
                sendJson = ::sendJson,
                errorJson = ::errorJson,
                requestJson = ::requestJson,
                getQueryParam = ::getQueryParam,
            )
        }
    }
}
