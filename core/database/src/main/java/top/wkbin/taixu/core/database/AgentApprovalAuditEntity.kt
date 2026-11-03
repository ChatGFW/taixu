package top.wkbin.taixu.core.database

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * 审批审计台账（借鉴 DSH approval/asked/decided 审计对持久化的思路）。
 *
 * 定位：只登记 **agent_approval_requests 表覆盖不到的审批事实**——
 * - [KIND_GRANT_WRITTEN] / [KIND_GRANT_EXEMPTED] / [KIND_DEFERRED]：会话授权与 Lane 交接
 * - [KIND_POLICY_DENIED]：执行前策略否决（PLAN / 能力禁用）
 * - [KIND_DECISION_FORWARDED]：高风险或无人值守下即将执行的放行决策
 * - [KIND_ACTION_FAILED]：上述放行后的执行失败（OpenBot 双审计第二行）
 * - [KIND_CONTROL_TAKEN] / [KIND_CONTROL_RELEASED]：浏览器人机接管交接（OpenBot computer.control_*）
 * - [KIND_HELP_REQUESTED]：人接管期间 Bot 仍尝试页面动作（OpenBot computer.help_requested）
 *
 * asked / 用户批准 / 拒绝 / 过期已在 agent_approval_requests 持久化，不在此重复。
 *
 * [initiatorKind] / [initiatorId]：与「以谁的权限」正交；缺省 person。不参与授权判定。
 * 授权本身仍是纯内存；本表只留痕迹，随会话删除清理。
 * 浏览器接管行写入固定 [SESSION_BROWSER_CONTROL]，不随聊天会话清理。
 */
@Entity(
    tableName = "agent_approval_audits",
    indices = [Index(value = ["sessionId", "createdAt"])],
)
data class AgentApprovalAuditEntity(
    @PrimaryKey val id: String,
    val sessionId: String,
    /** grant_* | deferred | policy_denied | decision_forwarded | action_failed | control_* | help_requested */
    val kind: String,
    val toolName: String,
    val toolCallId: String? = null,
    val riskLevel: String = "",
    /** 命中/写入的授权类别键（cmd:/dir:/mcp:/exact: 前缀，见 SessionApprovalGrants）。 */
    val grantKey: String? = null,
    val approvalRequestId: String? = null,
    val detail: String? = null,
    val createdAt: Long,
    @ColumnInfo(defaultValue = "'person'") val initiatorKind: String = "person",
    @ColumnInfo(defaultValue = "NULL") val initiatorId: String? = null,
) {
    companion object {
        const val KIND_GRANT_WRITTEN = "grant_written"
        const val KIND_GRANT_EXEMPTED = "grant_exempted"
        const val KIND_DEFERRED = "deferred"
        const val KIND_POLICY_DENIED = "policy_denied"
        const val KIND_DECISION_FORWARDED = "decision_forwarded"
        const val KIND_ACTION_FAILED = "action_failed"
        const val KIND_CONTROL_TAKEN = "control_taken"
        const val KIND_CONTROL_RELEASED = "control_released"
        const val KIND_HELP_REQUESTED = "help_requested"

        /** 浏览器共浏览交接专用会话桶（非聊天会话，deleteForSession 不会扫到聊天清理路径）。 */
        const val SESSION_BROWSER_CONTROL = "browser-control"
        const val TOOL_BROWSER_CONTROL = "browser.control"
    }
}

@Dao
interface AgentApprovalAuditDao {
    @Insert
    suspend fun insert(entry: AgentApprovalAuditEntity)

    @Query("SELECT * FROM agent_approval_audits WHERE sessionId = :sessionId ORDER BY createdAt ASC")
    fun observeForSession(sessionId: String): Flow<List<AgentApprovalAuditEntity>>

    @Query("SELECT * FROM agent_approval_audits WHERE sessionId = :sessionId ORDER BY createdAt DESC LIMIT :limit")
    suspend fun listForSession(sessionId: String, limit: Int): List<AgentApprovalAuditEntity>

    @Query("SELECT COUNT(*) FROM agent_approval_audits WHERE sessionId = :sessionId")
    suspend fun countForSession(sessionId: String): Int

    @Query(
        """
        SELECT * FROM agent_approval_audits
        WHERE initiatorKind IN ('schedule', 'handoff', 'workflow')
        ORDER BY createdAt DESC
        LIMIT :limit
        """,
    )
    fun observeUnattendedRecent(limit: Int): Flow<List<AgentApprovalAuditEntity>>

    @Query(
        """
        SELECT * FROM agent_approval_audits
        WHERE kind IN ('control_taken', 'control_released', 'help_requested')
        ORDER BY createdAt DESC
        LIMIT :limit
        """,
    )
    fun observeControlRecent(limit: Int): Flow<List<AgentApprovalAuditEntity>>

    @Query("DELETE FROM agent_approval_audits WHERE sessionId = :sessionId")
    suspend fun deleteForSession(sessionId: String)
}

class AgentApprovalAuditRepository(private val dao: AgentApprovalAuditDao) {

    suspend fun record(entry: AgentApprovalAuditEntity) = dao.insert(entry)

    fun observeForSession(sessionId: String): Flow<List<AgentApprovalAuditEntity>> =
        dao.observeForSession(sessionId)

    suspend fun listForSession(sessionId: String, limit: Int = 200): List<AgentApprovalAuditEntity> =
        dao.listForSession(sessionId, limit)

    /**
     * OpenBot「Nobody watching」：排期 / 群接力 / 工作流触发下的审批旁路事实。
     * kind 字符串与 harness RunInitiator 对齐；过滤在仓库侧以便诊断/UI 复用。
     */
    suspend fun listUnattendedForSession(sessionId: String, limit: Int = 200): List<AgentApprovalAuditEntity> =
        listForSession(sessionId, limit).filter { it.initiatorKind in UNATTENDED_INITIATOR_KINDS }

    fun observeUnattendedRecent(limit: Int = UNATTENDED_RECENT_LIMIT): Flow<List<AgentApprovalAuditEntity>> =
        dao.observeUnattendedRecent(limit)

    fun observeControlRecent(limit: Int = CONTROL_RECENT_LIMIT): Flow<List<AgentApprovalAuditEntity>> =
        dao.observeControlRecent(limit)

    suspend fun deleteForSession(sessionId: String) = dao.deleteForSession(sessionId)

    companion object {
        val UNATTENDED_INITIATOR_KINDS = setOf("schedule", "handoff", "workflow")
        const val UNATTENDED_RECENT_LIMIT = 50
        const val CONTROL_RECENT_LIMIT = 50
    }
}
