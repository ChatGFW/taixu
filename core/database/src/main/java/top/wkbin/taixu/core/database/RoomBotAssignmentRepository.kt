package top.wkbin.taixu.core.database

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import top.wkbin.taixu.core.database.repository.BotAssignmentRepository
import top.wkbin.taixu.core.model.bot.BotAssignment
import top.wkbin.taixu.core.model.bot.BotAssignmentStatus

class RoomBotAssignmentRepository(
    private val dao: BotAssignmentDao,
    private val json: Json,
) : BotAssignmentRepository, top.wkbin.taixu.core.database.repository.IndependentAssignmentRepository by
    top.wkbin.taixu.core.database.repository.RoomIndependentAssignmentRepository(dao, json) {
    override fun observeForGroup(groupId: String): Flow<List<BotAssignment>> =
        dao.observeForGroup(groupId).map { rows -> rows.map { it.toModel(json) } }

    override suspend fun listRunning(groupId: String): List<BotAssignment> =
        dao.listRunning(groupId).map { it.toModel(json) }

    override suspend fun listRecent(limit: Int): List<BotAssignment> =
        dao.listRecent(limit).map { it.toModel(json) }

    override suspend fun get(id: String): BotAssignment? = dao.findById(id)?.let { it.toModel(json) }

    override suspend fun save(assignment: BotAssignment) = dao.upsert(assignment.toEntity(json))

    override suspend fun finish(
        id: String,
        status: BotAssignmentStatus,
        summary: String?,
        error: String?,
        finishedAt: Long,
    ) = dao.finish(id, status.name, finishedAt, summary, error)

    override suspend fun failRunning(now: Long, error: String) = dao.failRunning(now, error)

}

internal fun BotAssignmentEntity.toModel(json: Json) = BotAssignment(
    id = id,
    groupId = groupId,
    botId = botId,
    botName = botName,
    title = title,
    task = task,
    status = runCatching { BotAssignmentStatus.valueOf(status) }
        .getOrDefault(BotAssignmentStatus.FAILED),
    writePaths = runCatching {
        json.decodeFromString(ListSerializer(String.serializer()), writePathsJson)
    }.getOrDefault(emptyList()),
    createdAt = createdAt,
    startedAt = startedAt,
    finishedAt = finishedAt,
    summary = summary,
    error = error,
    sessionId = sessionId,
    laneName = laneName,
    reportToBotId = reportToBotId,
    independent = independent, revision = revision, activeRequestId = activeRequestId,
)

internal fun BotAssignment.toEntity(json: Json) = BotAssignmentEntity(
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
    reportToBotId = reportToBotId,
    independent = independent, revision = revision, activeRequestId = activeRequestId,
)
