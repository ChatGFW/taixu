package top.wkbin.taixu.core.database

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import top.wkbin.taixu.core.model.bot.BotInboxItem
import top.wkbin.taixu.core.model.bot.BotInboxState

/**
 * Inbox 的派生事实表（ADR-0007）。
 *
 * 唯一索引 `(botId, sourceKind, sourceId)` 让「同一条来源对同一个 Bot」只能准入一次；
 * 重复准入用 `INSERT OR IGNORE` 消化，不会把已决策项重置回 pending。
 */
@Entity(
    tableName = "bot_inbox_items",
    indices = [
        Index(value = ["botId", "state", "createdAt"]),
        Index(value = ["botId", "sourceKind", "sourceId"], unique = true),
    ],
)
data class BotInboxItemEntity(
    @PrimaryKey val id: String,
    val botId: String,
    val sourceKind: String,
    val sourceId: String,
    val sessionId: String? = null,
    val reason: String,
    val priority: Int = 0,
    val state: String = BotInboxState.PENDING,
    val observedAt: Long? = null,
    val updatedAt: Long? = null,
    val createdAt: Long,
) {
    fun toModel() = BotInboxItem(
        id, botId, sourceKind, sourceId, sessionId, reason, priority, state, observedAt, createdAt, updatedAt,
    )
}

@Dao
abstract class BotInboxDao {
    /** 返回新行 id；-1 表示该来源对该 Bot 已经准入过（幂等）。 */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    abstract suspend fun admit(row: BotInboxItemEntity): Long

    @Query("UPDATE bot_inbox_items SET state = :state, observedAt = COALESCE(observedAt, :observedAt), updatedAt = :now WHERE id = :id")
    abstract suspend fun decide(id: String, state: String, observedAt: Long?, now: Long): Int

    @Query(
        "SELECT * FROM bot_inbox_items WHERE botId = :botId AND state = :state " +
            "ORDER BY priority DESC, createdAt ASC LIMIT :limit",
    )
    abstract suspend fun listForBot(botId: String, state: String, limit: Int): List<BotInboxItemEntity>

    @Query("SELECT * FROM bot_inbox_items WHERE botId = :botId AND sourceKind = :sourceKind AND sourceId = :sourceId LIMIT 1")
    abstract suspend fun findBySource(botId: String, sourceKind: String, sourceId: String): BotInboxItemEntity?

    @Query("SELECT COUNT(*) FROM bot_inbox_items WHERE botId = :botId AND state = :state")
    abstract suspend fun countForBot(botId: String, state: String): Int

    @Query("DELETE FROM bot_inbox_items WHERE botId = :botId")
    abstract suspend fun deleteForBot(botId: String)
}
