package top.wkbin.taixu.runtime.webchat

/** Runtime HTTP boundary for external executors; auth is the runtime token itself. */
interface ExternalAgentRuntimeGateway {
    suspend fun authorize(rawToken: String): ExternalAgentRuntimeIdentity?
    suspend fun claimNext(rawToken: String): ExternalAgentRuntimeClaim?
    suspend fun acknowledge(
        rawToken: String,
        deliveryId: String,
        claimNonce: String,
        status: String,
        error: String?,
    ): Boolean
    suspend fun postMessage(rawToken: String, groupId: String, content: String): String
}

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
)
