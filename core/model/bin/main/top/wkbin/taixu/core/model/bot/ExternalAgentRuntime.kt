package top.wkbin.taixu.core.model.bot

import kotlinx.serialization.Serializable

/** Issued once; only the SHA-256 hash is retained. */
data class IssuedExternalAgentToken(
    val tokenId: String,
    val botId: String,
    val rawToken: String,
    val expiresAt: Long?,
)

@Serializable
data class ExternalAgentRuntimeToken(
    val id: String,
    val botId: String,
    val label: String,
    val tokenHash: String,
    val createdAt: Long,
    val expiresAt: Long? = null,
    val revokedAt: Long? = null,
) {
    fun isActive(now: Long): Boolean =
        revokedAt == null && (expiresAt == null || expiresAt > now)
}

data class ExternalAgentPrincipal(
    val tokenId: String,
    val botId: String,
    val label: String,
)

/** One claimed receipt inside a turn; ack must present the same nonce. */
@Serializable
data class ExternalAgentDeliveryLease(
    val deliveryId: String,
    val claimNonce: String,
    val sourceMessageId: String,
    val content: String,
)

/**
 * One external turn. Consecutive same-group pending receipts may be bound into
 * [leases] (cap [MAX_COALESCED_DELIVERIES]); top-level fields mirror the first lease
 * while [content] is the prompt the executor should read.
 */
@Serializable
data class ExternalAgentDeliveryEvent(
    val deliveryId: String,
    val claimNonce: String,
    val groupId: String,
    val botId: String,
    val sourceMessageId: String,
    val content: String,
    val createdAt: Long,
    val leases: List<ExternalAgentDeliveryLease> = emptyList(),
    /** Absolute millis when silent leases may be reclaimed without heartbeat. */
    val leaseExpiresAt: Long = 0L,
) {
    companion object {
        const val MAX_COALESCED_DELIVERIES = 8
    }
}

@Serializable
data class ExternalAgentSessionInfo(
    val botId: String,
    val label: String,
    val pendingCount: Int,
    val groups: List<ExternalAgentGroupSummary>,
    val claimLeaseTtlMs: Long,
)

@Serializable
data class ExternalAgentPendingPreview(
    val deliveryId: String,
    val groupId: String,
    val sourceMessageId: String,
    val contentPreview: String,
    val createdAt: Long,
)

@Serializable
data class ExternalAgentGroupSummary(
    val id: String,
    val name: String,
)

@Serializable
data class ExternalAgentGroupMessage(
    val id: String,
    val senderId: String,
    val senderName: String,
    val senderType: String,
    val content: String,
    val timestamp: Long,
    val seq: Long,
)

@Serializable
data class ExternalAgentDeliveryAck(
    val deliveryId: String,
    val claimNonce: String,
    val status: String,
    val error: String? = null,
) {
    init {
        require(
            status == GroupDelivery.COMPLETED ||
                status == GroupDelivery.FAILED ||
                status == GroupDelivery.REJECTED ||
                status == GroupDelivery.UNKNOWN,
        ) { "不支持的回执状态: $status" }
    }
}
