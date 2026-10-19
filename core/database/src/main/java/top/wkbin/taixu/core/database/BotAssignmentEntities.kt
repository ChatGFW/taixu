package top.wkbin.taixu.core.database

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import top.wkbin.taixu.core.database.repository.BotAssignmentRepository
import top.wkbin.taixu.core.model.bot.BotAssignment
import top.wkbin.taixu.core.model.bot.BotAssignmentStatus

@Entity(
    tableName = "bot_assignments",
    foreignKeys = [
        ForeignKey(
            entity = GroupChatEntity::class,
            parentColumns = ["id"],
            childColumns = ["groupId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["groupId", "createdAt"]), Index(value = ["status"])],
)
data class BotAssignmentEntity(
    @PrimaryKey val id: String,
    val groupId: String?,
    val botId: String,
    val botName: String,
    val title: String,
    val task: String,
    val status: String,
    val writePathsJson: String,
    val createdAt: Long,
    val startedAt: Long,
    val finishedAt: Long?,
    val summary: String?,
    val error: String?,
    val sessionId: String?,
    val laneName: String?,
)

@Dao
interface BotAssignmentDao {
    @Query("SELECT * FROM bot_assignments WHERE groupId = :groupId ORDER BY createdAt DESC")
    fun observeForGroup(groupId: String): Flow<List<BotAssignmentEntity>>

    @Query("SELECT * FROM bot_assignments WHERE groupId = :groupId AND status = 'RUNNING' ORDER BY createdAt ASC")
    suspend fun listRunning(groupId: String): List<BotAssignmentEntity>

    @Query("SELECT * FROM bot_assignments ORDER BY createdAt DESC LIMIT :limit")
    suspend fun listRecent(limit: Int): List<BotAssignmentEntity>

    @Query("SELECT * FROM bot_assignments WHERE id = :id LIMIT 1")
    suspend fun findById(id: String): BotAssignmentEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(assignment: BotAssignmentEntity)

    @Query(
        "UPDATE bot_assignments SET status = :status, finishedAt = :finishedAt, " +
            "summary = :summary, error = :error WHERE id = :id",
    )
    suspend fun finish(id: String, status: String, finishedAt: Long, summary: String?, error: String?)

    @Query("DELETE FROM bot_assignments WHERE groupId = :groupId")
    suspend fun deleteForGroup(groupId: String)

    @Query(
        "UPDATE bot_assignments SET status = 'FAILED', finishedAt = :now, error = :error " +
            "WHERE status = 'RUNNING'",
    )
    suspend fun failRunning(now: Long, error: String)
}

class RoomBotAssignmentRepository(
    private val dao: BotAssignmentDao,
    private val json: Json,
) : BotAssignmentRepository {
    override fun observeForGroup(groupId: String): Flow<List<BotAssignment>> =
        dao.observeForGroup(groupId).map { rows -> rows.map { toModel(it) } }

    override suspend fun listRunning(groupId: String): List<BotAssignment> =
        dao.listRunning(groupId).map { toModel(it) }

    override suspend fun listRecent(limit: Int): List<BotAssignment> =
        dao.listRecent(limit).map { toModel(it) }

    override suspend fun get(id: String): BotAssignment? = dao.findById(id)?.let { toModel(it) }

    override suspend fun save(assignment: BotAssignment) = dao.upsert(assignment.toEntity())

    override suspend fun finish(
        id: String,
        status: BotAssignmentStatus,
        summary: String?,
        error: String?,
        finishedAt: Long,
    ) = dao.finish(id, status.name, finishedAt, summary, error)

    override suspend fun failRunning(now: Long, error: String) = dao.failRunning(now, error)

    private fun toModel(entity: BotAssignmentEntity) = BotAssignment(
        id = entity.id,
        groupId = entity.groupId,
        botId = entity.botId,
        botName = entity.botName,
        title = entity.title,
        task = entity.task,
        status = runCatching { BotAssignmentStatus.valueOf(entity.status) }
            .getOrDefault(BotAssignmentStatus.FAILED),
        writePaths = runCatching {
            json.decodeFromString(ListSerializer(String.serializer()), entity.writePathsJson)
        }.getOrDefault(emptyList()),
        createdAt = entity.createdAt,
        startedAt = entity.startedAt,
        finishedAt = entity.finishedAt,
        summary = entity.summary,
        error = entity.error,
        sessionId = entity.sessionId,
        laneName = entity.laneName,
    )

    private fun BotAssignment.toEntity() = BotAssignmentEntity(
        id = id,
        groupId = groupId,
        botId = botId,
        botName = botName,
        title = title,
        task = task,
        status = status.name,
        writePathsJson = json.encodeToString(ListSerializer(String.serializer()), writePaths),
        createdAt = createdAt,
        startedAt = startedAt,
        finishedAt = finishedAt,
        summary = summary,
        error = error,
        sessionId = sessionId,
        laneName = laneName,
    )
}
