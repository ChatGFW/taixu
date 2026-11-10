package top.wkbin.taixu.runtime.webchat

/** Runtime HTTP boundary for external executors; auth is the runtime token itself. */
interface ExternalAgentRuntimeGateway {
    suspend fun authorize(rawToken: String): ExternalAgentRuntimeIdentity?
    suspend fun session(rawToken: String): ExternalAgentRuntimeSession?
    suspend fun peekPending(rawToken: String, limit: Int): List<ExternalAgentRuntimePendingPreview>
    suspend fun claimNext(rawToken: String): ExternalAgentRuntimeClaim?
    suspend fun acknowledge(
        rawToken: String,
        deliveryId: String,
        claimNonce: String,
        status: String,
        error: String?,
    ): Boolean
    suspend fun acknowledgeAll(
        rawToken: String,
        status: String,
        error: String?,
        leases: List<ExternalAgentRuntimeClaimLeaseRef>,
    ): Int
    suspend fun release(rawToken: String, deliveryId: String, claimNonce: String): Boolean
    suspend fun heartbeat(rawToken: String, leases: List<ExternalAgentRuntimeClaimLeaseRef>): Int
    suspend fun postMessage(rawToken: String, groupId: String, content: String): String
    suspend fun listGroups(rawToken: String): List<ExternalAgentRuntimeGroupSummary>
    suspend fun groupContext(rawToken: String, groupId: String, limit: Int): List<ExternalAgentRuntimeGroupMessage>
}

data class ExternalAgentRuntimeSession(
    val botId: String,
    val label: String,
    val pendingCount: Int,
    val groups: List<ExternalAgentRuntimeGroupSummary>,
    val claimLeaseTtlMs: Long,
)

data class ExternalAgentRuntimePendingPreview(
    val deliveryId: String,
    val groupId: String,
    val sourceMessageId: String,
    val contentPreview: String,
    val createdAt: Long,
)

data class ExternalAgentRuntimeGroupSummary(
    val id: String,
    val name: String,
)

data class ExternalAgentRuntimeGroupMessage(
    val id: String,
    val senderId: String,
    val senderName: String,
    val senderType: String,
    val content: String,
    val timestamp: Long,
    val seq: Long,
)

data class ExternalAgentRuntimeClaimLeaseRef(
    val deliveryId: String,
    val claimNonce: String,
)

data class ExternalAgentRuntimeIdentity(
    val botId: String,
    val label: String,
)

data class ExternalAgentRuntimeClaimLease(
    val deliveryId: String,
    val claimNonce: String,
    val sourceMessageId: String,
    val content: String,
)

data class ExternalAgentRuntimeClaim(
    val deliveryId: String,
    val claimNonce: String,
    val groupId: String,
    val botId: String,
    val sourceMessageId: String,
    val content: String,
    val createdAt: Long,
    val leases: List<ExternalAgentRuntimeClaimLease> = emptyList(),
    val leaseExpiresAt: Long = 0L,
)
