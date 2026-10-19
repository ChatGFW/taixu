package top.wkbin.taixu.core.model.bot

import kotlinx.serialization.Serializable

@Serializable
enum class BotAssignmentStatus { RUNNING, COMPLETED, FAILED, CANCELLED }

/** 主控派发给某个 Bot 的一次后台任务，持久化后可供查询、验收和取消。 */
@Serializable
data class BotAssignment(
    val id: String,
    val groupId: String?,
    val botId: String,
    val botName: String,
    val title: String,
    val task: String,
    val status: BotAssignmentStatus,
    val writePaths: List<String> = emptyList(),
    val createdAt: Long,
    val startedAt: Long,
    val finishedAt: Long? = null,
    val summary: String? = null,
    val error: String? = null,
    val sessionId: String? = null,
    val laneName: String? = null,
)
