package top.wkbin.taixu.webchat

import top.wkbin.taixu.core.model.bot.ExternalAgentDeliveryAck
import top.wkbin.taixu.harness.bot.ExternalAgentRuntimeService
import top.wkbin.taixu.runtime.webchat.ExternalAgentRuntimeClaim
import top.wkbin.taixu.runtime.webchat.ExternalAgentRuntimeClaimLease
import top.wkbin.taixu.runtime.webchat.ExternalAgentRuntimeGateway
import top.wkbin.taixu.runtime.webchat.ExternalAgentRuntimeIdentity

class TaiXuExternalAgentRuntimeGateway(
    private val runtime: ExternalAgentRuntimeService,
) : ExternalAgentRuntimeGateway {
    override suspend fun authorize(rawToken: String): ExternalAgentRuntimeIdentity? =
        runtime.authorize(rawToken)?.let { ExternalAgentRuntimeIdentity(it.botId, it.label) }

    override suspend fun claimNext(rawToken: String): ExternalAgentRuntimeClaim? {
        val principal = runtime.authorize(rawToken) ?: return null
        val event = runtime.claimNextDelivery(principal) ?: return null
        return ExternalAgentRuntimeClaim(
            deliveryId = event.deliveryId,
            claimNonce = event.claimNonce,
            groupId = event.groupId,
            botId = event.botId,
            sourceMessageId = event.sourceMessageId,
            content = event.content,
            createdAt = event.createdAt,
            leases = event.leases.map {
                ExternalAgentRuntimeClaimLease(it.deliveryId, it.claimNonce, it.sourceMessageId, it.content)
            },
        )
    }

    override suspend fun acknowledge(
        rawToken: String,
        deliveryId: String,
        claimNonce: String,
        status: String,
        error: String?,
    ): Boolean {
        if (deliveryId.isBlank() || claimNonce.isBlank() || status.isBlank()) return false
        val principal = runtime.authorize(rawToken) ?: return false
        val ack = runCatching { ExternalAgentDeliveryAck(deliveryId, claimNonce, status, error) }.getOrNull()
            ?: return false
        return runtime.acknowledge(principal, ack)
    }

    override suspend fun postMessage(rawToken: String, groupId: String, content: String): String {
        val principal = requireNotNull(runtime.authorize(rawToken)) { "令牌无效或已撤销" }
        return runtime.postMessage(principal, groupId, content)
    }
}
