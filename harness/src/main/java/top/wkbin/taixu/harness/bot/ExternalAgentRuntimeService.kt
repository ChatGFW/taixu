package top.wkbin.taixu.harness.bot

import java.security.MessageDigest
import java.util.UUID
import kotlinx.coroutines.flow.first
import top.wkbin.taixu.core.database.repository.ExternalAgentRuntimeTokenRepository
import top.wkbin.taixu.core.database.repository.GroupChatRepository
import top.wkbin.taixu.core.database.repository.PersonaBotRepository
import top.wkbin.taixu.core.model.bot.BotRuntimeMode
import top.wkbin.taixu.core.model.bot.ExternalAgentDeliveryAck
import top.wkbin.taixu.core.model.bot.ExternalAgentDeliveryEvent
import top.wkbin.taixu.core.model.bot.ExternalAgentDeliveryLease
import top.wkbin.taixu.core.model.bot.ExternalAgentGroupMessage
import top.wkbin.taixu.core.model.bot.ExternalAgentGroupSummary
import top.wkbin.taixu.core.model.bot.ExternalAgentPendingPreview
import top.wkbin.taixu.core.model.bot.ExternalAgentPrincipal
import top.wkbin.taixu.core.model.bot.ExternalAgentRuntimeToken
import top.wkbin.taixu.core.model.bot.ExternalAgentSessionInfo
import top.wkbin.taixu.core.model.bot.GroupDelivery
import top.wkbin.taixu.core.model.bot.GroupMessage
import top.wkbin.taixu.core.model.bot.GroupSenderType
import top.wkbin.taixu.core.model.bot.IssuedExternalAgentToken

/**
 * Phone-side contract for computer/server agents that join a Bot identity.
 * HTTP exposure is a later adapter; this service owns auth, claim, ack and posting.
 */
class ExternalAgentRuntimeService(
    private val bots: PersonaBotRepository,
    private val groups: GroupChatRepository,
    private val tokens: ExternalAgentRuntimeTokenRepository,
    private val now: () -> Long = System::currentTimeMillis,
) {
    suspend fun issueToken(botId: String, label: String, expiresAt: Long? = null): IssuedExternalAgentToken {
        val bot = requireNotNull(bots.getBot(botId)) { "成员不存在" }
        require(!bot.isMainBot) { "主控成员不能切换为外部运行时" }
        val trimmed = label.trim().ifBlank { "external-runtime" }
        require(trimmed.length <= 64) { "令牌备注过长" }
        if (expiresAt != null) require(expiresAt > now()) { "过期时间必须在未来" }
        val tokenId = UUID.randomUUID().toString()
        val raw = "tx_rt_${UUID.randomUUID().toString().replace("-", "")}"
        tokens.insert(
            ExternalAgentRuntimeToken(
                id = tokenId,
                botId = botId,
                label = trimmed,
                tokenHash = sha256(raw),
                createdAt = now(),
                expiresAt = expiresAt,
            ),
        )
        if (bot.runtimeMode != BotRuntimeMode.EXTERNAL) {
            bots.saveBot(bot.copy(runtimeMode = BotRuntimeMode.EXTERNAL, updatedAt = now()))
        }
        return IssuedExternalAgentToken(tokenId, botId, raw, expiresAt)
    }

    suspend fun revokeToken(tokenId: String): Boolean {
        val existing = tokens.findById(tokenId) ?: return false
        if (!tokens.revoke(tokenId, now())) return false
        // 该成员已无可用令牌时，把在途 EXTERNAL 租约退回 pending，避免永远卡在 submitting。
        requeueIfNoActiveTokens(existing.botId)
        return true
    }

    /** Marks absolute-expired, still-unrevoked tokens as revoked; returns how many were cleaned. */
    suspend fun revokeExpiredTokens(botId: String): Int {
        val stamp = now()
        val cleaned = tokens.revokeExpiredForBot(botId, stamp, stamp)
        if (cleaned > 0) requeueIfNoActiveTokens(botId)
        return cleaned
    }

    private suspend fun requeueIfNoActiveTokens(botId: String) {
        val stamp = now()
        if (tokens.listForBot(botId).none { it.isActive(stamp) }) {
            groups.requeueSubmittingForBot(botId)
        }
    }

    suspend fun listTokens(botId: String): List<ExternalAgentRuntimeToken> = tokens.listForBot(botId)

    suspend fun restoreLocalRuntime(botId: String): Boolean {
        val bot = bots.getBot(botId) ?: return false
        // Requeue before flipping mode so in-flight EXTERNAL claims are not left stranded
        // (and later cold-started as UNKNOWN under LOCAL).
        groups.requeueSubmittingForBot(botId)
        tokens.revokeAllForBot(botId, now())
        if (bot.runtimeMode != BotRuntimeMode.LOCAL) {
            bots.saveBot(bot.copy(runtimeMode = BotRuntimeMode.LOCAL, updatedAt = now()))
        }
        return true
    }

    suspend fun authorize(rawToken: String): ExternalAgentPrincipal? {
        val token = tokens.findByHash(sha256(rawToken.trim())) ?: return null
        if (!token.isActive(now())) return null
        val bot = bots.getBot(token.botId) ?: return null
        if (bot.runtimeMode != BotRuntimeMode.EXTERNAL) return null
        return ExternalAgentPrincipal(token.id, token.botId, token.label)
    }

    suspend fun describeSession(principal: ExternalAgentPrincipal): ExternalAgentSessionInfo {
        requireExternalBot(principal.botId)
        reclaimStaleClaims()
        val pending = groups.pendingDeliveries().first().count { it.botId == principal.botId }
        return ExternalAgentSessionInfo(
            botId = principal.botId,
            label = principal.label,
            pendingCount = pending,
            groups = listGroups(principal),
            claimLeaseTtlMs = CLAIM_LEASE_TTL_MS,
        )
    }

    /** Peek pending receipts without claiming; content is truncated for listing only. */
    suspend fun peekPending(
        principal: ExternalAgentPrincipal,
        limit: Int = DEFAULT_PENDING_PREVIEW_LIMIT,
    ): List<ExternalAgentPendingPreview> {
        requireExternalBot(principal.botId)
        reclaimStaleClaims()
        val cap = limit.coerceIn(1, MAX_PENDING_PREVIEW_LIMIT)
        val receipts = groups.pendingDeliveries().first()
            .asSequence()
            .filter { it.botId == principal.botId }
            .take(cap)
            .toList()
        return buildList(receipts.size) {
            for (receipt in receipts) {
                val source = groups.deliverySource(receipt.sourceMessageId) ?: continue
                add(
                    ExternalAgentPendingPreview(
                        deliveryId = receipt.id,
                        groupId = receipt.groupId,
                        sourceMessageId = source.id,
                        contentPreview = source.content.take(280),
                        createdAt = receipt.createdAt,
                    ),
                )
            }
        }
    }

    suspend fun claimNextDelivery(principal: ExternalAgentPrincipal): ExternalAgentDeliveryEvent? {
        val bot = requireExternalBot(principal.botId)
        reclaimStaleClaims()
        val pending = groups.pendingDeliveries().first().filter { it.botId == bot.id }
        for (receipt in pending) {
            val lead = claimLease(bot.id, receipt) ?: continue
            val sameGroup = pending
                .asSequence()
                .filter { it.id != receipt.id && it.groupId == receipt.groupId }
                .take(ExternalAgentDeliveryEvent.MAX_COALESCED_DELIVERIES - 1)
            val leases = buildList {
                add(lead)
                for (extra in sameGroup) {
                    add(claimLease(bot.id, extra) ?: continue)
                }
            }
            val claimedAt = now()
            return ExternalAgentDeliveryEvent(
                deliveryId = lead.deliveryId,
                claimNonce = lead.claimNonce,
                groupId = receipt.groupId,
                botId = bot.id,
                sourceMessageId = lead.sourceMessageId,
                content = renderClaimContent(leases),
                createdAt = receipt.createdAt,
                leases = leases,
                leaseExpiresAt = claimedAt + CLAIM_LEASE_TTL_MS,
            )
        }
        return null
    }

    private suspend fun claimLease(botId: String, receipt: GroupDelivery): ExternalAgentDeliveryLease? {
        val nonce = groups.claimDelivery(receipt.id) ?: return null
        val source = groups.deliverySource(receipt.sourceMessageId)
        if (source == null) {
            groups.finishDelivery(receipt.id, GroupDelivery.REJECTED, "来源消息已清除", nonce)
            return null
        }
        if (groups.getMembers(receipt.groupId).none { it.id == botId }) {
            groups.finishDelivery(receipt.id, GroupDelivery.REJECTED, "成员已不在群内", nonce)
            return null
        }
        return ExternalAgentDeliveryLease(receipt.id, nonce, source.id, source.content)
    }

    private fun renderClaimContent(leases: List<ExternalAgentDeliveryLease>): String {
        val lead = leases.first().content
        if (leases.size == 1) return lead
        val extras = leases.drop(1)
        val frame = if (extras.size == 1) {
            "[另有 1 条同群消息在本轮开始前到达，已并入本轮。请与上文一并阅读并答复。]"
        } else {
            "[另有 ${extras.size} 条同群消息在本轮开始前到达，已并入本轮。请与上文一并阅读并答复全部内容。]"
        }
        return buildString {
            append(lead)
            append("\n\n")
            append(frame)
            extras.forEachIndexed { index, lease ->
                append("\n\n[同群续讯 #${index + 1} · ${lease.sourceMessageId}]\n")
                append(lease.content)
            }
        }
    }

    suspend fun acknowledge(principal: ExternalAgentPrincipal, ack: ExternalAgentDeliveryAck): Boolean {
        requireExternalBot(principal.botId)
        val receipt = groups.findDelivery(ack.deliveryId) ?: return false
        if (receipt.botId != principal.botId || receipt.claimNonce != ack.claimNonce) return false
        // Lost HTTP reply after a successful settle: same lease + terminal status → already applied.
        if (receipt.status in TERMINAL_DELIVERY_STATUSES) return true
        if (receipt.status != GroupDelivery.SUBMITTING && receipt.status != GroupDelivery.ADMITTED) return false
        groups.finishDelivery(ack.deliveryId, ack.status, ack.error, ack.claimNonce)
        return groups.findDelivery(ack.deliveryId)?.status == ack.status
    }

    /** Settles each lease independently; returns how many succeeded. */
    suspend fun acknowledgeAll(principal: ExternalAgentPrincipal, acks: List<ExternalAgentDeliveryAck>): Int =
        acks.count { acknowledge(principal, it) }

    /**
     * Puts a submitting lease back on the pending queue so another claim can pick it up.
     * Admitted receipts are never released (native harness owns them).
     */
    suspend fun release(principal: ExternalAgentPrincipal, deliveryId: String, claimNonce: String): Boolean {
        requireExternalBot(principal.botId)
        val receipt = groups.findDelivery(deliveryId) ?: return false
        if (receipt.botId != principal.botId || receipt.claimNonce != claimNonce) return false
        if (receipt.status != GroupDelivery.SUBMITTING) return false
        return groups.releaseDelivery(deliveryId, claimNonce)
    }

    /**
     * Renews claim leases so long-running external turns are not reclaimed by TTL.
     * Returns how many leases were refreshed.
     */
    suspend fun heartbeat(
        principal: ExternalAgentPrincipal,
        leases: List<Pair<String, String>>,
    ): Int {
        requireExternalBot(principal.botId)
        if (leases.isEmpty()) return 0
        val touchedAt = now()
        return leases.count { (deliveryId, claimNonce) ->
            deliveryId.isNotBlank() &&
                claimNonce.isNotBlank() &&
                groups.touchClaim(deliveryId, principal.botId, claimNonce, touchedAt)
        }
    }

    /** Reclaims EXTERNAL submitting leases older than [CLAIM_LEASE_TTL_MS]. */
    suspend fun reclaimStaleClaims(ttlMs: Long = CLAIM_LEASE_TTL_MS): Int {
        val age = ttlMs.coerceIn(MIN_CLAIM_LEASE_TTL_MS, MAX_CLAIM_LEASE_TTL_MS)
        return groups.reclaimStaleExternalClaims(now() - age)
    }

    suspend fun postMessage(principal: ExternalAgentPrincipal, groupId: String, content: String): String {
        val bot = requireExternalBot(principal.botId)
        val text = content.trim()
        require(text.isNotEmpty()) { "消息不能为空" }
        require(text.length <= 32_000) { "消息过长" }
        require(groups.getMembers(groupId).any { it.id == bot.id }) { "成员不在该群" }
        val id = UUID.randomUUID().toString()
        groups.postMessage(
            GroupMessage(
                id = id,
                groupId = groupId,
                senderId = bot.id,
                senderName = bot.name,
                senderType = GroupSenderType.BOT,
                content = text,
                timestamp = now(),
            ),
        )
        return id
    }

    suspend fun listGroups(principal: ExternalAgentPrincipal): List<ExternalAgentGroupSummary> {
        requireExternalBot(principal.botId)
        return groups.observeGroupsForBot(principal.botId).first()
            .map { ExternalAgentGroupSummary(it.id, it.name) }
    }

    /**
     * Recent transcript for a group this member belongs to.
     * Streaming drafts are included as-is; attachments/tools are omitted from this surface.
     */
    suspend fun groupContext(
        principal: ExternalAgentPrincipal,
        groupId: String,
        limit: Int = DEFAULT_CONTEXT_LIMIT,
    ): List<ExternalAgentGroupMessage> {
        val bot = requireExternalBot(principal.botId)
        require(groups.getMembers(groupId).any { it.id == bot.id }) { "成员不在该群" }
        val cap = limit.coerceIn(1, MAX_CONTEXT_LIMIT)
        return groups.observeMessages(groupId).first()
            .asReversed()
            .take(cap)
            .asReversed()
            .map { message ->
                ExternalAgentGroupMessage(
                    id = message.id,
                    senderId = message.senderId,
                    senderName = message.senderName,
                    senderType = message.senderType.name,
                    content = message.content.take(8_000),
                    timestamp = message.timestamp,
                    seq = message.seq,
                )
            }
    }

    private suspend fun requireExternalBot(botId: String) =
        requireNotNull(bots.getBot(botId)?.takeIf { it.runtimeMode == BotRuntimeMode.EXTERNAL }) {
            "成员未处于外部运行模式"
        }

    companion object {
        const val DEFAULT_CONTEXT_LIMIT = 40
        const val MAX_CONTEXT_LIMIT = 100
        const val DEFAULT_PENDING_PREVIEW_LIMIT = 16
        const val MAX_PENDING_PREVIEW_LIMIT = 64
        /** Default age after which a silent EXTERNAL submitting lease is requeued. */
        const val CLAIM_LEASE_TTL_MS = 15 * 60_000L
        const val MIN_CLAIM_LEASE_TTL_MS = 60_000L
        const val MAX_CLAIM_LEASE_TTL_MS = 60 * 60_000L
        private val TERMINAL_DELIVERY_STATUSES = setOf(
            GroupDelivery.COMPLETED,
            GroupDelivery.FAILED,
            GroupDelivery.REJECTED,
            GroupDelivery.UNKNOWN,
            GroupDelivery.CANCELLED,
        )
    }

    private fun sha256(value: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(Charsets.UTF_8))
            .joinToString("") { byte -> "%02x".format(byte) }
}
