package top.wkbin.taixu.core.database.repository

import top.wkbin.taixu.core.database.GroupChatEntity
import top.wkbin.taixu.core.model.bot.GroupChat
import top.wkbin.taixu.core.model.bot.GroupReplyMode
import top.wkbin.taixu.core.database.GroupMemberEntity
import top.wkbin.taixu.core.model.bot.GroupTeam

internal fun teamFromRows(adminId: String, rows: List<GroupMemberEntity>) = GroupTeam(
    adminId,
    rows.map { it.botId }.toSet(),
    rows.mapNotNull { row -> row.supervisorBotId?.takeIf { id -> rows.any { it.botId == id } }?.let { row.botId to it } }.toMap(),
)

internal fun GroupChatEntity.toModel() = GroupChat(
    id = id,
    name = name,
    adminBotId = adminBotId,
    description = description,
    createdAt = createdAt,
    background = background, taskBudgetTokens = taskBudgetTokens, teamGroupId = teamGroupId, teamVersion = teamVersion,
    replyMode = runCatching { GroupReplyMode.valueOf(replyMode) }.getOrDefault(GroupReplyMode.ADMIN_FIRST),
)

internal fun GroupChat.toEntity() = GroupChatEntity(
    id = id,
    name = name,
    adminBotId = adminBotId,
    description = description,
    createdAt = createdAt,
    background = background, taskBudgetTokens = taskBudgetTokens, teamGroupId = teamGroupId, teamVersion = teamVersion,
    replyMode = replyMode.name,
)
