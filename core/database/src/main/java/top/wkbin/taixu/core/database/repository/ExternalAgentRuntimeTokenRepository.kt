package top.wkbin.taixu.core.database.repository

import top.wkbin.taixu.core.database.ExternalAgentRuntimeTokenDao
import top.wkbin.taixu.core.database.ExternalAgentRuntimeTokenEntity
import top.wkbin.taixu.core.model.bot.ExternalAgentRuntimeToken

interface ExternalAgentRuntimeTokenRepository {
    suspend fun insert(token: ExternalAgentRuntimeToken)
    suspend fun findByHash(tokenHash: String): ExternalAgentRuntimeToken?
    suspend fun findById(id: String): ExternalAgentRuntimeToken?
    suspend fun listForBot(botId: String): List<ExternalAgentRuntimeToken>
    suspend fun revoke(id: String, revokedAt: Long): Boolean
    suspend fun revokeAllForBot(botId: String, revokedAt: Long): Int
    suspend fun revokeExpiredForBot(botId: String, now: Long, revokedAt: Long): Int
}

class RoomExternalAgentRuntimeTokenRepository(
    private val dao: ExternalAgentRuntimeTokenDao,
) : ExternalAgentRuntimeTokenRepository {
    override suspend fun insert(token: ExternalAgentRuntimeToken) = dao.insert(
        ExternalAgentRuntimeTokenEntity(
            id = token.id,
            botId = token.botId,
            label = token.label,
            tokenHash = token.tokenHash,
            createdAt = token.createdAt,
            expiresAt = token.expiresAt,
            revokedAt = token.revokedAt,
        ),
    )

    override suspend fun findByHash(tokenHash: String) = dao.findByHash(tokenHash)?.toModel()
    override suspend fun findById(id: String) = dao.findById(id)?.toModel()
    override suspend fun listForBot(botId: String) = dao.listForBot(botId).map { it.toModel() }
    override suspend fun revoke(id: String, revokedAt: Long) = dao.revoke(id, revokedAt) == 1
    override suspend fun revokeAllForBot(botId: String, revokedAt: Long) = dao.revokeAllForBot(botId, revokedAt)
    override suspend fun revokeExpiredForBot(botId: String, now: Long, revokedAt: Long) =
        dao.revokeExpiredForBot(botId, now, revokedAt)

    private fun ExternalAgentRuntimeTokenEntity.toModel() = ExternalAgentRuntimeToken(
        id = id,
        botId = botId,
        label = label,
        tokenHash = tokenHash,
        createdAt = createdAt,
        expiresAt = expiresAt,
        revokedAt = revokedAt,
    )
}
