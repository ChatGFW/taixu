package top.wkbin.taixu.core.database.repository

import top.wkbin.taixu.core.database.BotInboxDao
import top.wkbin.taixu.core.database.BotInboxItemEntity
import top.wkbin.taixu.core.model.bot.BotInboxItem

/** Inbox 投影的读写口；harness 只依赖本接口，不直接触 DAO。 */
interface BotInboxRepository {
    /** 返回 false 表示该来源对该 Bot 已准入过（幂等，不改动已有状态）。 */
    suspend fun admit(item: BotInboxItem): Boolean
    suspend fun decide(id: String, state: String, observedAt: Long?, now: Long): Boolean
    suspend fun listForBot(botId: String, state: String, limit: Int = 50): List<BotInboxItem>
    suspend fun findBySource(botId: String, sourceKind: String, sourceId: String): BotInboxItem?
    suspend fun countForBot(botId: String, state: String): Int
    suspend fun deleteForBot(botId: String)
}

class RoomBotInboxRepository(private val dao: BotInboxDao) : BotInboxRepository {
    override suspend fun admit(item: BotInboxItem): Boolean = dao.admit(item.toEntity()) != -1L

    override suspend fun decide(id: String, state: String, observedAt: Long?, now: Long): Boolean =
        dao.decide(id, state, observedAt, now) > 0

    override suspend fun listForBot(botId: String, state: String, limit: Int): List<BotInboxItem> =
        dao.listForBot(botId, state, limit).map { it.toModel() }

    override suspend fun findBySource(botId: String, sourceKind: String, sourceId: String): BotInboxItem? =
        dao.findBySource(botId, sourceKind, sourceId)?.toModel()

    override suspend fun countForBot(botId: String, state: String): Int = dao.countForBot(botId, state)

    override suspend fun deleteForBot(botId: String) = dao.deleteForBot(botId)
}

private fun BotInboxItem.toEntity() = BotInboxItemEntity(
    id = id,
    botId = botId,
    sourceKind = sourceKind,
    sourceId = sourceId,
    sessionId = sessionId,
    reason = reason,
    priority = priority,
    state = state,
    observedAt = observedAt,
    updatedAt = updatedAt,
    createdAt = createdAt,
)
