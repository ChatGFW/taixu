package top.wkbin.taixu.core.database

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow
import top.wkbin.taixu.core.model.bot.GroupTeam

/** Kept in the membership table so removal and group deletion cannot leave detached organizations. */
@Dao
interface GroupTeamDao {
    @Query("SELECT * FROM group_members WHERE groupId = :groupId ORDER BY displayOrder")
    fun observeTeamMembers(groupId: String): Flow<List<GroupMemberEntity>>

    @Query("SELECT * FROM group_members WHERE groupId = :groupId")
    suspend fun teamMembers(groupId: String): List<GroupMemberEntity>

    @Query("SELECT adminBotId FROM group_chats WHERE id = :groupId")
    suspend fun teamAdmin(groupId: String): String?

    @Query("UPDATE group_members SET supervisorBotId = :supervisorId WHERE groupId = :groupId AND botId = :botId")
    suspend fun updateSupervisor(groupId: String, botId: String, supervisorId: String?)

    @Query("UPDATE group_members SET supervisorBotId = NULL WHERE groupId = :groupId AND supervisorBotId = :botId")
    suspend fun detachReports(groupId: String, botId: String)

    @Transaction
    suspend fun saveTeam(groupId: String, supervisors: Map<String, String>) {
        val admin = requireNotNull(teamAdmin(groupId)) { "群聊不存在" }
        val rows = teamMembers(groupId)
        GroupTeam(admin, rows.map { it.botId }.toSet(), supervisors).validate()
        rows.forEach { updateSupervisor(groupId, it.botId, supervisors[it.botId]?.takeUnless { id -> id == admin }) }
    }
}

val MIGRATION_74_75 = object : Migration(74, 75) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE group_members ADD COLUMN supervisorBotId TEXT DEFAULT NULL")
        db.execSQL("ALTER TABLE bot_assignments ADD COLUMN parentAssignmentId TEXT DEFAULT NULL")
    }
}
