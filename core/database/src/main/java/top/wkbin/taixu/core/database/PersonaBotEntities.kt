package top.wkbin.taixu.core.database

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Entity(
    tableName = "persona_bots",
    indices = [
        Index(value = ["sessionId"], unique = true),
        Index(value = ["isMainBot"]),
    ],
)
data class BotEntity(
    @PrimaryKey val id: String,
    val name: String,
    val role: String,
    val systemPrompt: String,
    val avatarSeed: String,
    val modelId: String?,
    val providerId: String?,
    val sessionId: String,
    val isMainBot: Boolean,
    val colorHex: String,
    val createdAt: Long,
    val updatedAt: Long,
)

@Entity(
    tableName = "group_chats",
    foreignKeys = [
        ForeignKey(
            entity = BotEntity::class,
            parentColumns = ["id"],
            childColumns = ["adminBotId"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [Index(value = ["adminBotId"])],
)
data class GroupChatEntity(
    @PrimaryKey val id: String,
    val name: String,
    val adminBotId: String,
    val description: String,
    val createdAt: Long,
    val background: String = "default",
)

@Entity(
    tableName = "group_members",
    primaryKeys = ["groupId", "botId"],
    foreignKeys = [
        ForeignKey(
            entity = GroupChatEntity::class,
            parentColumns = ["id"],
            childColumns = ["groupId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = BotEntity::class,
            parentColumns = ["id"],
            childColumns = ["botId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["groupId"]), Index(value = ["botId"])],
)
data class GroupMemberEntity(
    val groupId: String,
    val botId: String,
    val displayOrder: Int,
    val joinedAt: Long,
)

@Entity(
    tableName = "group_messages",
    foreignKeys = [
        ForeignKey(
            entity = GroupChatEntity::class,
            parentColumns = ["id"],
            childColumns = ["groupId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["groupId", "timestamp"]),
        Index(value = ["delegatedTaskId"]),
    ],
)
data class GroupMessageEntity(
    @PrimaryKey val id: String,
    val groupId: String,
    val senderId: String,
    val senderName: String,
    val senderType: String,
    val content: String,
    val timestamp: Long,
    val isStreaming: Boolean,
    val delegatedTaskId: String?,
    val attachmentsJson: String = "[]",
    val reactionsJson: String = "[]",
    val stepsJson: String = "[]",
    @ColumnInfo(defaultValue = "''") val reasoning: String = "",
)

@Entity(
    tableName = "bot_schedules",
    foreignKeys = [
        ForeignKey(
            entity = BotEntity::class,
            parentColumns = ["id"],
            childColumns = ["botId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["botId"]), Index(value = ["nextRunAt"])],
)
data class BotScheduleEntity(
    @PrimaryKey val id: String,
    val botId: String,
    val groupId: String?,
    val cron: String,
    val instruction: String,
    val enabled: Boolean,
    @ColumnInfo(name = "nextRunAt") val nextRunAt: Long,
    val lastRunAt: Long?,
    val createdAt: Long,
)

@Dao
interface PersonaBotDao {
    @Query("SELECT * FROM persona_bots ORDER BY isMainBot DESC, updatedAt DESC")
    fun observeAll(): Flow<List<BotEntity>>

    @Query("SELECT * FROM persona_bots WHERE id = :id LIMIT 1")
    suspend fun findById(id: String): BotEntity?

    @Query("SELECT * FROM persona_bots WHERE sessionId = :sessionId OR id IN " +
        "(SELECT botId FROM bot_group_sessions WHERE sessionId = :sessionId) LIMIT 1")
    suspend fun findBySessionId(sessionId: String): BotEntity?

    @Query("SELECT * FROM persona_bots WHERE isMainBot = 1 LIMIT 1")
    suspend fun findMainBot(): BotEntity?

    @Query("SELECT * FROM persona_bots ORDER BY isMainBot DESC, updatedAt DESC")
    suspend fun listAll(): List<BotEntity>

    @Query("SELECT * FROM persona_bots WHERE name = :name COLLATE NOCASE LIMIT 1")
    suspend fun findByName(name: String): BotEntity?

    @Upsert
    suspend fun upsert(bot: BotEntity)

    @Query("DELETE FROM persona_bots WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("UPDATE persona_bots SET isMainBot = 0 WHERE id != :exceptId")
    suspend fun clearMainExcept(exceptId: String)

    @Transaction
    suspend fun upsertWithMain(bot: BotEntity) {
        if (bot.isMainBot) clearMainExcept(bot.id)
        upsert(bot)
    }
}

@Dao
interface GroupChatDao {
    @Query("SELECT * FROM group_chats ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<GroupChatEntity>>

    @Query(
        "SELECT g.* FROM group_chats g INNER JOIN group_members m ON g.id = m.groupId " +
            "WHERE m.botId = :botId ORDER BY g.createdAt DESC",
    )
    fun observeForBot(botId: String): Flow<List<GroupChatEntity>>

    @Query("SELECT * FROM group_chats WHERE id = :id LIMIT 1")
    suspend fun findById(id: String): GroupChatEntity?

    @Query("SELECT COUNT(*) FROM group_chats WHERE adminBotId = :botId")
    suspend fun countAsAdmin(botId: String): Int

    @Upsert
    suspend fun upsertGroup(group: GroupChatEntity)

    @Query("DELETE FROM group_chats WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query(
        "SELECT b.* FROM persona_bots b INNER JOIN group_members m ON b.id = m.botId " +
            "WHERE m.groupId = :groupId ORDER BY m.displayOrder ASC",
    )
    fun observeMembers(groupId: String): Flow<List<BotEntity>>

    @Query(
        "SELECT b.* FROM persona_bots b INNER JOIN group_members m ON b.id = m.botId " +
            "WHERE m.groupId = :groupId ORDER BY m.displayOrder ASC",
    )
    suspend fun getMembers(groupId: String): List<BotEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addMember(member: GroupMemberEntity)

    @Query("DELETE FROM group_members WHERE groupId = :groupId AND botId = :botId")
    suspend fun removeMember(groupId: String, botId: String)

    @Query("SELECT * FROM group_messages WHERE groupId = :groupId ORDER BY timestamp ASC")
    fun observeMessages(groupId: String): Flow<List<GroupMessageEntity>>

    @Query("SELECT * FROM group_messages WHERE id = :id LIMIT 1")
    suspend fun findMessage(id: String): GroupMessageEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertMessage(message: GroupMessageEntity)

    @Query(
        "UPDATE group_messages SET content = :content, isStreaming = :isStreaming, " +
            "stepsJson = :stepsJson, reasoning = COALESCE(:reasoning, reasoning) WHERE id = :id",
    )
    suspend fun updateMessageContent(id: String, content: String, isStreaming: Boolean, stepsJson: String, reasoning: String?)

    @Query("UPDATE group_messages SET reactionsJson = :reactionsJson WHERE id = :id")
    suspend fun updateMessageReactions(id: String, reactionsJson: String)

    @Query("DELETE FROM group_messages WHERE groupId = :groupId")
    suspend fun deleteMessages(groupId: String)

    @Query("DELETE FROM bot_assignments WHERE groupId = :groupId")
    suspend fun deleteAssignments(groupId: String)

    @Transaction
    suspend fun clearMessages(groupId: String) {
        deleteAssignments(groupId)
        deleteMessages(groupId)
    }
}

@Dao
interface BotScheduleDao {
    @Query("SELECT * FROM bot_schedules WHERE botId = :botId ORDER BY nextRunAt ASC")
    fun observeForBot(botId: String): Flow<List<BotScheduleEntity>>

    @Query("SELECT * FROM bot_schedules WHERE enabled = 1 AND nextRunAt <= :now ORDER BY nextRunAt ASC")
    suspend fun listDue(now: Long): List<BotScheduleEntity>

    @Query("SELECT * FROM bot_schedules WHERE id = :id LIMIT 1")
    suspend fun findById(id: String): BotScheduleEntity?

    @Query("SELECT * FROM bot_schedules WHERE enabled = 1 ORDER BY nextRunAt ASC LIMIT 1")
    suspend fun findNext(): BotScheduleEntity?

    @Upsert
    suspend fun upsert(schedule: BotScheduleEntity)

    @Query("DELETE FROM bot_schedules WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM bot_schedules WHERE botId = :botId")
    suspend fun deleteForBot(botId: String)

    @Query("UPDATE bot_schedules SET lastRunAt = :lastRunAt, nextRunAt = :nextRunAt WHERE id = :id")
    suspend fun markRun(id: String, lastRunAt: Long, nextRunAt: Long)
}
