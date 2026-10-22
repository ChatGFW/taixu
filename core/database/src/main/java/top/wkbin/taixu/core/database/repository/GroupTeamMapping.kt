package top.wkbin.taixu.core.database.repository

import top.wkbin.taixu.core.database.GroupMemberEntity
import top.wkbin.taixu.core.model.bot.GroupTeam

internal fun teamFromRows(adminId: String, rows: List<GroupMemberEntity>) = GroupTeam(
    adminId,
    rows.map { it.botId }.toSet(),
    rows.mapNotNull { row -> row.supervisorBotId?.takeIf { id -> rows.any { it.botId == id } }?.let { row.botId to it } }.toMap(),
)
