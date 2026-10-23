package top.wkbin.taixu.core.database

import androidx.room.*
import top.wkbin.taixu.core.model.bot.GroupGovernance

@Dao
interface GroupTemplateDao : GroupGovernanceDao {
    @Query("SELECT * FROM group_chats WHERE id = :id LIMIT 1")
    suspend fun templateGroup(id: String): GroupChatEntity?
    @Upsert suspend fun publishTemplateGroup(group: GroupChatEntity)
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertTemplateMember(member: GroupMemberEntity)

    @Transaction
    suspend fun installTeamTemplate(group: GroupChatEntity, members: List<String>, supervisors: Map<String, String>,
        policy: GroupGovernance, createNew: Boolean) {
        val existing = templateGroup(group.id)
        require(if (createNew) existing == null else existing?.adminBotId == group.adminBotId) { "群聊状态已变化" }
        if (createNew) publishTemplateGroup(group)
        val oldMembers = teamMembers(group.id)
        members.forEachIndexed { index, id -> insertTemplateMember(GroupMemberEntity(group.id, id, oldMembers.size + index, System.currentTimeMillis())) }
        saveTeam(group.id, oldMembers.mapNotNull { row -> row.supervisorBotId?.let { row.botId to it } }.toMap() + supervisors)
        saveGovernance(group.id, GroupGovernance(governance(group.id).rules + policy.rules))
    }
}
