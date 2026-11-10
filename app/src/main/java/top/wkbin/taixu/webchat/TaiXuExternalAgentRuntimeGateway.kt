package top.wkbin.taixu.webchat

import top.wkbin.taixu.core.model.bot.ExternalAgentDeliveryAck
import top.wkbin.taixu.harness.bot.ExternalAgentRuntimeService
import top.wkbin.taixu.runtime.webchat.ExternalAgentRuntimeClaim
import top.wkbin.taixu.runtime.webchat.ExternalAgentRuntimeClaimLease
import top.wkbin.taixu.runtime.webchat.ExternalAgentRuntimeClaimLeaseRef
import top.wkbin.taixu.runtime.webchat.ExternalAgentRuntimeGateway
import top.wkbin.taixu.runtime.webchat.ExternalAgentRuntimeGroupMessage
import top.wkbin.taixu.runtime.webchat.ExternalAgentRuntimeGroupSummary
import top.wkbin.taixu.runtime.webchat.ExternalAgentRuntimeIdentity
import top.wkbin.taixu.runtime.webchat.ExternalAgentRuntimePendingPreview
import top.wkbin.taixu.runtime.webchat.ExternalAgentRuntimeSession

class TaiXuExternalAgentRuntimeGateway(
    private val runtime: ExternalAgentRuntimeService,
) : ExternalAgentRuntimeGateway {
    override suspend fun authorize(rawToken: String): ExternalAgentRuntimeIdentity? =
        runtime.authorize(rawToken)?.let { ExternalAgentRuntimeIdentity(it.botId, it.label) }

    override suspend fun session(rawToken: String): ExternalAgentRuntimeSession? {
        val principal = runtime.authorize(rawToken) ?: return null
        val info = runtime.describeSession(principal)
        return ExternalAgentRuntimeSession(
            botId = info.botId,
            label = info.label,
            pendingCount = info.pendingCount,
            groups = info.groups.map { ExternalAgentRuntimeGroupSummary(it.id, it.name) },
            claimLeaseTtlMs = info.claimLeaseTtlMs,
        )
    }

    override suspend fun peekPending(rawToken: String, limit: Int): List<ExternalAgentRuntimePendingPreview> {
        val principal = runtime.authorize(rawToken) ?: return emptyList()
        return runtime.peekPending(principal, limit).map {
            ExternalAgentRuntimePendingPreview(
                deliveryId = it.deliveryId,
                groupId = it.groupId,
                sourceMessageId = it.sourceMessageId,
                contentPreview = it.contentPreview,
                createdAt = it.createdAt,
            )
        }
    }

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
            leaseExpiresAt = event.leaseExpiresAt,
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

    override suspend fun acknowledgeAll(
        rawToken: String,
        status: String,
        error: String?,
        leases: List<ExternalAgentRuntimeClaimLeaseRef>,
    ): Int {
        if (status.isBlank() || leases.isEmpty()) return 0
        val principal = runtime.authorize(rawToken) ?: return 0
        val acks = leases.mapNotNull { lease ->
            runCatching {
                ExternalAgentDeliveryAck(lease.deliveryId, lease.claimNonce, status, error)
            }.getOrNull()
        }
        return runtime.acknowledgeAll(principal, acks)
    }

    override suspend fun release(rawToken: String, deliveryId: String, claimNonce: String): Boolean {
        if (deliveryId.isBlank() || claimNonce.isBlank()) return false
        val principal = runtime.authorize(rawToken) ?: return false
        return runtime.release(principal, deliveryId, claimNonce)
    }

    override suspend fun heartbeat(rawToken: String, leases: List<ExternalAgentRuntimeClaimLeaseRef>): Int {
        if (leases.isEmpty()) return 0
        val principal = runtime.authorize(rawToken) ?: return 0
        return runtime.heartbeat(principal, leases.map { it.deliveryId to it.claimNonce })
    }

    override suspend fun postMessage(rawToken: String, groupId: String, content: String): String {
        val principal = runtime.authorize(rawToken) ?: throw SecurityException("令牌无效或已撤销")
        return runtime.postMessage(principal, groupId, content)
    }

    override suspend fun listGroups(rawToken: String): List<ExternalAgentRuntimeGroupSummary> {
        val principal = runtime.authorize(rawToken) ?: throw SecurityException("令牌无效或已撤销")
        return runtime.listGroups(principal).map { ExternalAgentRuntimeGroupSummary(it.id, it.name) }
    }

    override suspend fun groupContext(
        rawToken: String,
        groupId: String,
        limit: Int,
    ): List<ExternalAgentRuntimeGroupMessage> {
        val principal = runtime.authorize(rawToken) ?: throw SecurityException("令牌无效或已撤销")
        return runtime.groupContext(principal, groupId, limit).map {
            ExternalAgentRuntimeGroupMessage(
                id = it.id,
                senderId = it.senderId,
                senderName = it.senderName,
                senderType = it.senderType,
                content = it.content,
                timestamp = it.timestamp,
                seq = it.seq,
            )
        }
    }
}
