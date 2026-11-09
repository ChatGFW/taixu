package top.wkbin.taixu.runtime.webchat

import java.net.InetSocketAddress
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
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

    @Synchronized
    fun start(port: Int = DEFAULT_EXTERNAL_AGENT_RUNTIME_PORT): Boolean {
        if (_status.value.isRunning) return true
        return runCatching {
            val server = AndroidHttpServer.create(InetSocketAddress("0.0.0.0", port), 0)
            server.createContext("/runtime/v1/me", MeHandler())
            server.createContext("/runtime/v1/events/claim", ClaimHandler())
            server.createContext("/runtime/v1/events/ack", AckHandler())
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
        runCatching { httpServer?.stop(0) }
        httpServer = null
        _status.value = ExternalAgentRuntimeServerStatus(port = _status.value.port)
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
        val token = when {
            header.startsWith("Bearer ", ignoreCase = true) -> header.removePrefix("Bearer ").trim()
            else -> getQueryParam(exchange, "token").orEmpty().trim()
        }
        if (!token.startsWith("tx_rt_")) throw SecurityException("需要外部运行时令牌")
        return token
    }

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

    private inner class MeHandler : AndroidHttpHandler {
        override fun handle(exchange: AndroidHttpExchange) = launchRequest(exchange) {
            if (handlePreflight(exchange)) return@launchRequest
            if (exchange.requestMethod != "GET") {
                sendJson(exchange, 405, errorJson("仅支持 GET")); return@launchRequest
            }
            val identity = gateway.authorize(bearerToken(exchange)) ?: throw SecurityException("令牌无效或已撤销")
            sendJson(exchange, 200, buildJsonObject {
                put("botId", identity.botId)
                put("label", identity.label)
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
            val waitMs = getQueryParam(exchange, "waitMs")?.toLongOrNull()
                ?.coerceIn(0L, MAX_EXTERNAL_AGENT_CLAIM_WAIT_MS) ?: 0L
            val deadline = System.currentTimeMillis() + waitMs
            var event = gateway.claimNext(token)
            while (event == null && System.currentTimeMillis() < deadline) {
                delay(EXTERNAL_AGENT_CLAIM_POLL_MS)
                event = gateway.claimNext(token)
            }
            if (event == null) {
                exchange.sendResponseHeaders(204, -1)
                exchange.close()
                return@launchRequest
            }
            sendJson(exchange, 200, buildJsonObject {
                put("deliveryId", event.deliveryId)
                put("claimNonce", event.claimNonce)
                put("groupId", event.groupId)
                put("botId", event.botId)
                put("sourceMessageId", event.sourceMessageId)
                put("content", event.content)
                put("createdAt", event.createdAt)
                putJsonArray("leases") {
                    val leases = event.leases.ifEmpty {
                        listOf(
                            ExternalAgentRuntimeClaimLease(
                                event.deliveryId,
                                event.claimNonce,
                                event.sourceMessageId,
                                event.content,
                            ),
                        )
                    }
                    leases.forEach { lease ->
                        addJsonObject {
                            put("deliveryId", lease.deliveryId)
                            put("claimNonce", lease.claimNonce)
                            put("sourceMessageId", lease.sourceMessageId)
                            put("content", lease.content)
                        }
                    }
                }
            })
        }
    }

    private inner class AckHandler : AndroidHttpHandler {
        override fun handle(exchange: AndroidHttpExchange) = launchRequest(exchange) {
            if (handlePreflight(exchange)) return@launchRequest
            if (exchange.requestMethod != "POST") {
                sendJson(exchange, 405, errorJson("仅支持 POST")); return@launchRequest
            }
            val token = bearerToken(exchange)
            val body = requestJson(exchange)
            val ok = gateway.acknowledge(
                rawToken = token,
                deliveryId = body["deliveryId"]?.jsonPrimitive?.content.orEmpty(),
                claimNonce = body["claimNonce"]?.jsonPrimitive?.content.orEmpty(),
                status = body["status"]?.jsonPrimitive?.content.orEmpty(),
                error = body["error"]?.jsonPrimitive?.content,
            )
            if (!ok) sendJson(exchange, 409, errorJson("回执被拒绝（租约不匹配或已结算）"))
            else sendJson(exchange, 200, buildJsonObject { put("ok", true) })
        }
    }

    private inner class GroupsHandler : AndroidHttpHandler {
        override fun handle(exchange: AndroidHttpExchange) = launchRequest(exchange) {
            if (handlePreflight(exchange)) return@launchRequest
            val path = exchange.requestURI.path.removePrefix("/runtime/v1/groups/").trim('/')
            val parts = path.split('/').filter { it.isNotBlank() }
            if (parts.size != 2 || parts[1] != "messages" || exchange.requestMethod != "POST") {
                sendJson(exchange, 404, errorJson("未知路径")); return@launchRequest
            }
            val token = bearerToken(exchange)
            val content = requestJson(exchange)["content"]?.jsonPrimitive?.content.orEmpty()
            val messageId = gateway.postMessage(token, parts[0], content)
            sendJson(exchange, 200, buildJsonObject { put("messageId", messageId) })
        }
    }
}
