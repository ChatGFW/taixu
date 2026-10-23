package top.wkbin.taixu.core.database

import androidx.room.*
import kotlinx.serialization.json.Json
import top.wkbin.taixu.core.model.bot.BotTeamDefinition

/** Team authoring and room membership synchronization share a transaction. */
@Dao
interface TeamDefinitionDao : GroupTeamDao {
    @Query("SELECT * FROM bot_member_groups WHERE id = :id")
    suspend fun definitionGroup(id: String): BotMemberGroupEntity?
    @Query("SELECT * FROM bot_member_group_members WHERE memberGroupId = :id ORDER BY displayOrder")
    suspend fun definitionMembers(id: String): List<BotMemberGroupMemberEntity>
    @Query("SELECT id FROM group_chats WHERE teamGroupId = :id")
    suspend fun linkedChats(id: String): List<String>
    @Upsert suspend fun writeDefinitionGroup(group: BotMemberGroupEntity)
    @Query("DELETE FROM bot_member_group_members WHERE memberGroupId = :id")
    suspend fun clearDefinitionMembers(id: String)
    @Insert suspend fun insertDefinitionMember(member: BotMemberGroupMemberEntity)
    @Upsert suspend fun syncChatMember(member: GroupMemberEntity)
    @Query("DELETE FROM group_members WHERE groupId = :groupId AND botId = :botId")
    suspend fun deleteLinkedMember(groupId: String, botId: String)
    @Query("UPDATE group_chats SET adminBotId = :admin, teamGroupId = :teamId WHERE id = :groupId")
    suspend fun setChatTeam(groupId: String, teamId: String, admin: String)
    @Query("UPDATE group_deliveries SET status = 'cancelled' WHERE groupId = :groupId AND botId = :botId AND status IN ('pending','submitting','admitted')")
    suspend fun closeRemovedMemberDeliveries(groupId: String, botId: String)

    @Transaction suspend fun saveDefinition(group: BotMemberGroupEntity, members: List<BotMemberGroupMemberEntity>) {
        require(group.name.isNotBlank() && members.map { it.botId }.distinct().size == members.size)
        val definition = group.definitionJson?.let { Json.decodeFromString<BotTeamDefinition>(it) }
        definition?.validate(members.map { it.botId })
        require(definition != null || linkedChats(group.id).isEmpty()) { "被群聊引用的团队组必须保留组织定义" }
        writeDefinitionGroup(group)
        clearDefinitionMembers(group.id)
        members.forEach { insertDefinitionMember(it.copy(memberGroupId = group.id)) }
        if (definition != null) linkedChats(group.id).forEach { syncDefinitionToChat(it, group.id) }
    }

    @Transaction suspend fun syncDefinitionToChat(groupId: String, teamId: String) {
        requireNotNull(teamAdmin(groupId)) { "群聊不存在" }
        val group = requireNotNull(definitionGroup(teamId)) { "团队组不存在" }
        val definition = Json.decodeFromString<BotTeamDefinition>(requireNotNull(group.definitionJson) { "请先在团队组中设置主控和组织" })
        val members = definitionMembers(teamId)
        definition.validate(members.map { it.botId })
        val ids = members.map { it.botId }.toSet()
        teamMembers(groupId).filter { it.botId !in ids }.forEach {
            closeRemovedMemberDeliveries(groupId, it.botId)
            deleteLinkedMember(groupId, it.botId)
        }
        setChatTeam(groupId, teamId, definition.adminBotId)
        members.forEach { syncChatMember(GroupMemberEntity(groupId, it.botId, it.displayOrder, it.addedAt)) }
    }
}
