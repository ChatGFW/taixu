package top.wkbin.taixu.runtime.webchat

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray

/** Keeps runtime HTTP free of a harness dependency while matching service defaults. */
internal object ExternalAgentRuntimeServiceDefaults {
    const val DEFAULT_CONTEXT_LIMIT = 40
    const val DEFAULT_PENDING_PREVIEW_LIMIT = 16
    const val CLAIM_LEASE_TTL_MS = 15 * 60_000L
}

internal fun externalAgentRuntimeHealthJson(): JsonObject = buildJsonObject {
    put("ok", true)
    put("service", "external-agent-runtime")
}

internal fun externalAgentRuntimeMetaJson(): JsonObject = buildJsonObject {
    put("service", "external-agent-runtime")
    put("claimLeaseTtlMs", ExternalAgentRuntimeServiceDefaults.CLAIM_LEASE_TTL_MS)
    put("maxClaimWaitMs", MAX_EXTERNAL_AGENT_CLAIM_WAIT_MS)
    putJsonArray("routes") {
        listOf(
            "GET /runtime/v1/health",
            "GET /runtime/v1/meta",
            "GET /runtime/v1/me",
            "GET /runtime/v1/events/pending",
            "POST /runtime/v1/events/claim",
            "POST /runtime/v1/events/ack",
            "POST /runtime/v1/events/release",
            "POST /runtime/v1/events/heartbeat",
            "GET /runtime/v1/groups",
            "GET /runtime/v1/groups/{groupId}/context",
            "POST /runtime/v1/groups/{groupId}/messages",
        ).forEach { add(it) }
    }
    putJsonArray("ackStatuses") {
        EXTERNAL_AGENT_ACK_STATUSES.forEach { add(it) }
    }
}

internal val EXTERNAL_AGENT_ACK_STATUSES = listOf("completed", "failed", "rejected", "unknown")

internal fun isValidExternalAgentAckStatus(status: String): Boolean =
    status in EXTERNAL_AGENT_ACK_STATUSES

internal fun parseExternalAgentHeartbeatLeases(body: JsonObject): List<ExternalAgentRuntimeClaimLeaseRef> {
    val batch = (body["leases"] as? JsonArray).orEmpty().mapNotNull { item ->
        val obj = item as? JsonObject ?: return@mapNotNull null
        val deliveryId = obj["deliveryId"]?.jsonPrimitive?.contentOrNull.orEmpty()
        val claimNonce = obj["claimNonce"]?.jsonPrimitive?.contentOrNull.orEmpty()
        if (deliveryId.isBlank() || claimNonce.isBlank()) null
        else ExternalAgentRuntimeClaimLeaseRef(deliveryId, claimNonce)
    }
    if (batch.isNotEmpty()) return batch
    val deliveryId = body["deliveryId"]?.jsonPrimitive?.contentOrNull.orEmpty()
    val claimNonce = body["claimNonce"]?.jsonPrimitive?.contentOrNull.orEmpty()
    if (deliveryId.isBlank() || claimNonce.isBlank()) return emptyList()
    return listOf(ExternalAgentRuntimeClaimLeaseRef(deliveryId, claimNonce))
}

internal fun externalAgentHeartbeatOkJson(renewed: Int, requested: Int): JsonObject = buildJsonObject {
    put("ok", renewed == requested)
    put("renewed", renewed)
    if (requested > 1 || renewed != requested) put("requested", requested)
    put("leaseExpiresAt", System.currentTimeMillis() + ExternalAgentRuntimeServiceDefaults.CLAIM_LEASE_TTL_MS)
}
