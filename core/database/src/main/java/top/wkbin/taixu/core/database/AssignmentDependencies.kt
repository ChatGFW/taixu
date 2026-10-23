package top.wkbin.taixu.core.database

import kotlinx.serialization.json.Json

internal const val DEPENDENCY_BLOCK_REASON = "前置任务不存在、不属于本群或尚未完成；请核验为 COMPLETED 后继续原任务。"

/** Snapshot checks belong to the same transaction as admission/claim, not just the model-facing tool. */
internal suspend fun BotAssignmentDao.hasCompletedDependencies(task: BotAssignmentEntity): Boolean {
    val ids = runCatching { Json.decodeFromString<List<String>>(task.dependsOnJson) }.getOrElse { return false }
    return ids.all { id ->
        id != task.id && findById(id)?.let { it.groupId == task.groupId && it.status == "COMPLETED" } == true
    }
}
