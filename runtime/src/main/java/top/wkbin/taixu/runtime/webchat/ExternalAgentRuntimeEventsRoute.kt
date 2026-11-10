package top.wkbin.taixu.runtime.webchat

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Semaphore
import kotlinx.coroutines.delay
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray

internal suspend fun handleExternalAgentClaimRequest(
    exchange: AndroidHttpExchange,
    gateway: ExternalAgentRuntimeGateway,
    token: String,
    waitMs: Long,
    serverRunning: () -> Boolean,
    clientIp: String,
    claimGlobal: Semaphore,
    claimPerIp: ConcurrentHashMap<String, Semaphore>,
    sendJson: (AndroidHttpExchange, Int, JsonObject) -> Unit,
    errorJson: (String, String?) -> JsonObject,
) {
    val ipGate = claimPerIp.getOrPut(clientIp) { Semaphore(MAX_EXTERNAL_AGENT_CLAIM_PER_IP) }
    if (!claimGlobal.tryAcquire()) {
        sendJson(exchange, 429, errorJson("claim 并发已满", "claim_busy"))
        return
    }
    try {
        if (!ipGate.tryAcquire()) {
            sendJson(exchange, 429, errorJson("该地址 claim 并发已满", "claim_busy"))
            return
        }
        try {
            val deadline = System.currentTimeMillis() + waitMs
            var event = gateway.claimNext(token)
            while (event == null && serverRunning() && System.currentTimeMillis() < deadline) {
                delay(EXTERNAL_AGENT_CLAIM_POLL_MS)
                event = gateway.claimNext(token)
            }
            if (event == null) {
                exchange.sendResponseHeaders(204, -1)
                exchange.close()
                return
            }
            sendJson(exchange, 200, buildJsonObject {
                put("deliveryId", event.deliveryId)
                put("claimNonce", event.claimNonce)
                put("groupId", event.groupId)
                put("botId", event.botId)
                put("sourceMessageId", event.sourceMessageId)
                put("content", event.content)
                put("createdAt", event.createdAt)
                put("leaseExpiresAt", event.leaseExpiresAt)
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
        } finally {
            ipGate.release()
        }
    } finally {
        claimGlobal.release()
    }
}

internal suspend fun handleExternalAgentAckRequest(
    exchange: AndroidHttpExchange,
    gateway: ExternalAgentRuntimeGateway,
    token: String,
    body: JsonObject,
    sendJson: (AndroidHttpExchange, Int, JsonObject) -> Unit,
    errorJson: (String, String?) -> JsonObject,
) {
    val status = body["status"]?.jsonPrimitive?.content.orEmpty()
    if (!isValidExternalAgentAckStatus(status)) {
        sendJson(exchange, 400, errorJson("不支持的回执状态: $status", "invalid_ack_status"))
        return
    }
    val error = body["error"]?.jsonPrimitive?.contentOrNull
    val batch = body["leases"]
    if (batch is JsonArray) {
        val refs = batch.mapNotNull { element ->
            val obj = element as? JsonObject ?: return@mapNotNull null
            val deliveryId = obj["deliveryId"]?.jsonPrimitive?.content.orEmpty()
            val claimNonce = obj["claimNonce"]?.jsonPrimitive?.content.orEmpty()
            if (deliveryId.isBlank() || claimNonce.isBlank()) null
            else ExternalAgentRuntimeClaimLeaseRef(deliveryId, claimNonce)
        }
        val settled = gateway.acknowledgeAll(token, status, error, refs)
        if (settled == 0) sendJson(exchange, 409, errorJson("回执被拒绝（租约不匹配）", "lease_mismatch"))
        else sendJson(exchange, 200, buildJsonObject {
            put("ok", settled == refs.size)
            put("settled", settled)
            put("requested", refs.size)
        })
        return
    }
    val ok = gateway.acknowledge(
        rawToken = token,
        deliveryId = body["deliveryId"]?.jsonPrimitive?.content.orEmpty(),
        claimNonce = body["claimNonce"]?.jsonPrimitive?.content.orEmpty(),
        status = status,
        error = error,
    )
    if (!ok) sendJson(exchange, 409, errorJson("回执被拒绝（租约不匹配）", "lease_mismatch"))
    else sendJson(exchange, 200, buildJsonObject { put("ok", true) })
}
