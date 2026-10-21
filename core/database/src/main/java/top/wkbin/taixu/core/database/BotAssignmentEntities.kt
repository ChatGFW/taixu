package top.wkbin.taixu.core.database

import androidx.room.ColumnInfo
import androidx.room.Transaction
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

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
    indices = [Index(value = ["groupId", "createdAt"]), Index(value = ["status"]), Index(value = ["sessionId"])],
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
    @ColumnInfo(defaultValue = "NULL") val reportToBotId: String? = null,
    @ColumnInfo(defaultValue = "0") val independent: Boolean = false,
    @ColumnInfo(defaultValue = "0") val revision: Int = 0,
    @ColumnInfo(defaultValue = "NULL") val activeRequestId: String? = null,
    @ColumnInfo(defaultValue = "'[]'") val dependsOnJson: String = "[]",
)

@Dao
interface BotAssignmentDao : GroupDeliveryDao, BotAssignmentRequestDao {
    @Query("SELECT * FROM bot_assignments WHERE botId = :botId AND independent = 1")
    suspend fun assignmentsForBot(botId: String): List<BotAssignmentEntity>

    @Query("SELECT * FROM bot_assignments WHERE sessionId = :sessionId AND independent = 1 LIMIT 1")
    suspend fun assignmentForSession(sessionId: String): BotAssignmentEntity?

    @Transaction
    suspend fun createIndependent(task: BotAssignmentEntity, session: HarnessSessionEntity, request: BotAssignmentRequestEntity) {
        require(task.independent && task.sessionId == session.id && request.assignmentId == task.id)
        val group = requireNotNull(task.groupId)
        require(deliveryMember(group, task.botId)) { "执行成员已移出群聊" }
        require(task.reportToBotId != null && deliveryMember(group, task.reportToBotId)) { "请求者已移出群聊" }
        insertIndependentSession(session)
        insertIndependentAssignment(task)
        insertAssignmentRequest(request)
    }

    @Transaction
    suspend fun enqueueRequest(request: BotAssignmentRequestEntity) {
        val task = requireNotNull(findById(request.assignmentId)) { "任务不存在" }
        require(task.independent) { "旧版任务没有独立会话，请重新派发" }
        require(deliveryMember(requireNotNull(task.groupId), task.botId)) { "执行成员已移出群聊" }
        val existing = findAssignmentRequest(request.id)
        if (existing != null) {
            require(existing.assignmentId == request.assignmentId && existing.instruction == request.instruction) { "补充要求 ID 已被其他内容使用" }
            return
        }
        if (task.status == "INTERRUPTED") cancelAssignmentRequests(task.id)
        insertAssignmentRequest(request)
        queueAssignment(task.id)
    }

    @Transaction
    suspend fun claimRequest(id: String, now: Long): Boolean {
        val request = findAssignmentRequest(id)?.takeIf { it.status == "pending" } ?: return false
        val task = findById(request.assignmentId) ?: return false
        if (task.status != "QUEUED" || !task.independent) return false
        if (!deliveryMember(requireNotNull(task.groupId), task.botId) ||
            task.reportToBotId?.let { !deliveryMember(task.groupId, it) } == true) {
            cancelAssignment(task.id, now)
            return false
        }
        if (activateAssignment(task.id, id, now) != 1) return false
        check(claimAssignmentRequest(id) == 1)
        return true
    }

    @Transaction
    suspend fun finishRequest(id: String, success: Boolean, uncertain: Boolean, summary: String?, error: String?, now: Long) {
        val request = findAssignmentRequest(id) ?: return
        val task = findById(request.assignmentId) ?: return
        if (task.activeRequestId != id || task.status != "RUNNING") return
        val requestStatus = if (uncertain) "unknown" else if (success) "completed" else "failed"
        if (settleAssignmentRequest(id, requestStatus, summary, error) != 1) return
        val outcome = when {
            uncertain -> "INTERRUPTED"
            !success -> "FAILED"
            !request.blockedReason.isNullOrBlank() -> "BLOCKED"
            else -> "COMPLETED"
        }
        // Uncertain native effects require explicit resumption, even if a later instruction was queued.
        val status = if (!uncertain && queuedAssignmentRequests(task.id) > 0) "QUEUED" else outcome
        if (uncertain) settleInterruptedAssignmentMessages(task.id, "⚠️ 原执行结果待核对，未自动重跑")
        val detail = if (uncertain || !success) error else request.blockedReason
        settleAssignment(task.id, status, now, summary, detail)
        stageAssignmentReport(task, status, now, summary, detail)
    }

    @Transaction
    suspend fun recoverRequests(now: Long) {
        interruptedAssignmentRequests().forEach { request ->
            finishRequest(request.id, false, true, null, "进程退出，原执行结果待核对；继续前先核验原会话和产物", now)
        }
    }

    @Transaction
    suspend fun cancelAssignment(id: String, now: Long) {
        val task = findById(id) ?: return
        if (!task.independent) {
            finish(id, "CANCELLED", now, null, "用户取消")
            return
        }
        cancelAssignmentRequests(id)
        if (task.status == "CANCELLED") return
        settleAssignment(id, "CANCELLED", now, task.summary, "用户取消")
        val lastRequest = assignmentRequests(id).firstOrNull()?.id.orEmpty()
        stageAssignmentReport(task.copy(activeRequestId = "cancel:$lastRequest"), "CANCELLED", now, null, "用户取消")
    }

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
    suspend fun settleAssignment(id: String, status: String, finishedAt: Long, summary: String?, error: String?)

    @Transaction
    suspend fun finish(id: String, status: String, finishedAt: Long, summary: String?, error: String?) {
        val previous = findById(id) ?: return
        if (previous.status != "RUNNING") return
        settleAssignment(id, status, finishedAt, summary, error)
        stageAssignmentReport(previous, status, finishedAt, summary, error)
    }

    suspend fun stageAssignmentReport(assignment: BotAssignmentEntity, status: String, now: Long, summary: String?, error: String?) {
        val recipient = assignment.reportToBotId ?: return
        val groupId = assignment.groupId ?: return
        val label = when (status) {
            "COMPLETED" -> "已完成"
            "CANCELLED" -> "已取消"
            "BLOCKED" -> "受阻"
            "INTERRUPTED" -> "执行结果待核对"
            "QUEUED" -> "本轮结束，仍有补充要求待处理"
            else -> "执行未完成"
        }
        val content = "【成员委派结果】${assignment.botName} · $label（任务 ${assignment.id}）\n" +
            (summary ?: "无结果摘要").take(4_000) + (error?.let { "\n原因：$it" } ?: "") +
            "\n请核对原任务与产物；结果未知时先只读核验，勿重复执行。"
        stageGroupDelivery(GroupMessageEntity(
            id = "assignment-report:${assignment.id}" + (assignment.activeRequestId?.let { ":$it" } ?: ""), groupId = groupId, senderId = "system", senderName = "系统通知",
            senderType = "SYSTEM", content = content, timestamp = now, isStreaming = false, delegatedTaskId = assignment.id,
        ), if (status == "CANCELLED") emptyList() else listOf(recipient))
    }

    @Query("DELETE FROM bot_assignments WHERE groupId = :groupId")
    suspend fun deleteForGroup(groupId: String)

    @Query(
        "UPDATE bot_assignments SET status = 'FAILED', finishedAt = :now, error = :error " +
            "WHERE status = 'RUNNING' AND independent = 0",
    )
    suspend fun markRunningInterrupted(now: Long, error: String)

    @Query("SELECT * FROM bot_assignments WHERE status = 'RUNNING' AND independent = 0")
    suspend fun allRunning(): List<BotAssignmentEntity>

    @Transaction
    suspend fun failRunning(now: Long, error: String) {
        val interrupted = allRunning()
        markRunningInterrupted(now, error)
        interrupted.forEach { stageAssignmentReport(it, "FAILED", now, null, error) }
    }
}
