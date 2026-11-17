package top.wkbin.taixu.core.model.bot

import kotlinx.serialization.Serializable

/** 独立任务的持久 MCP 权限上限，绑定原群、成员、派单者与父任务。 */
@Serializable
data class BotTaskCapabilityGrant(
    val taskId: String,
    val botId: String,
    val groupId: String?,
    val issuerBotId: String,
    val parentAssignmentId: String?,
    val policy: BotCapabilityPolicy,
    val version: Int = 1,
) {
    fun matches(task: BotAssignment): Boolean = version == 1 && taskId == task.id && botId == task.botId &&
        groupId == task.groupId && issuerBotId == task.reportToBotId && parentAssignmentId == task.parentAssignmentId
}
