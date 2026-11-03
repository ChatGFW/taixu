package top.wkbin.taixu.ui.developer

import org.junit.Assert.assertEquals
import org.junit.Test
import top.wkbin.taixu.core.database.AgentApprovalAuditEntity

class UnattendedAuditGroupingTest {

    @Test
    fun `groups keep first seen session order and title fallback`() {
        val groups = groupUnattendedAuditsBySession(
            entries = listOf(
                audit("a1", "s1", createdAt = 300),
                audit("a2", "s2", createdAt = 200),
                audit("a3", "s1", createdAt = 100),
            ),
            sessionTitles = mapOf("s1" to "定时会话"),
        )
        assertEquals(listOf("s1", "s2"), groups.map { it.sessionId })
        assertEquals(listOf("a1", "a3"), groups[0].entries.map { it.id })
        assertEquals("定时会话", groups[0].sessionTitle)
        assertEquals("未知会话", groups[1].sessionTitle)
    }

    @Test
    fun `labels cover unattended initiator and audit kinds`() {
        assertEquals("排期", unattendedInitiatorLabel("schedule"))
        assertEquals("委派", unattendedInitiatorLabel("handoff"))
        assertEquals("工作流", unattendedInitiatorLabel("workflow"))
        assertEquals("会话授权", unattendedAuditKindLabel(AgentApprovalAuditEntity.KIND_GRANT_WRITTEN))
        assertEquals("授权豁免", unattendedAuditKindLabel(AgentApprovalAuditEntity.KIND_GRANT_EXEMPTED))
        assertEquals("Lane 交接", unattendedAuditKindLabel(AgentApprovalAuditEntity.KIND_DEFERRED))
        assertEquals("策略否决", unattendedAuditKindLabel(AgentApprovalAuditEntity.KIND_POLICY_DENIED))
        assertEquals("放行决策", unattendedAuditKindLabel(AgentApprovalAuditEntity.KIND_DECISION_FORWARDED))
        assertEquals("执行失败", unattendedAuditKindLabel(AgentApprovalAuditEntity.KIND_ACTION_FAILED))
        assertEquals("人接管", unattendedAuditKindLabel(AgentApprovalAuditEntity.KIND_CONTROL_TAKEN))
        assertEquals("人交还", unattendedAuditKindLabel(AgentApprovalAuditEntity.KIND_CONTROL_RELEASED))
        assertEquals("Bot 求助", unattendedAuditKindLabel(AgentApprovalAuditEntity.KIND_HELP_REQUESTED))
        assertEquals(emptyList<UnattendedAuditSessionGroup>(), groupUnattendedAuditsBySession(emptyList(), emptyMap()))
    }

    private fun audit(id: String, sessionId: String, createdAt: Long) = AgentApprovalAuditEntity(
        id = id,
        sessionId = sessionId,
        kind = AgentApprovalAuditEntity.KIND_GRANT_EXEMPTED,
        toolName = "write",
        createdAt = createdAt,
        initiatorKind = "schedule",
        initiatorId = "sched-1",
    )
}
