package top.wkbin.taixu.harness.bot

import kotlinx.coroutines.flow.first
import top.wkbin.taixu.core.database.McpServerRepository
import top.wkbin.taixu.core.database.repository.PersonaBotRepository
import top.wkbin.taixu.core.database.repository.BotAssignmentRepository
import top.wkbin.taixu.core.model.bot.Bot
import top.wkbin.taixu.core.model.bot.BotAssignment
import top.wkbin.taixu.core.model.bot.BotCapabilityPolicy
import top.wkbin.taixu.core.model.bot.BotTaskCapabilityGrant

/** 当前成员配置与任务创建时的上限共同约束每次 MCP 准入。 */
class RepositoryBotCapabilityScope(
    private val bots: PersonaBotRepository,
    private val servers: McpServerRepository,
    private val assignments: BotAssignmentRepository,
    private val now: () -> Long = System::currentTimeMillis,
) : BotCapabilityScope {
    override suspend fun allowedMcpServers(sessionId: String): Set<String>? = policyForSession(sessionId).servers?.toSet()

    override suspend fun allowedMcpTools(sessionId: String, serverId: String): Set<String>? {
        val policy = policyForSession(sessionId)
        if (policy.servers?.contains(serverId.lowercase()) == false) return emptySet()
        return policy.tools[serverId]?.toSet()
    }

    override suspend fun allowsTool(sessionId: String, serverId: String, tool: String): Boolean {
        val policy = policyForSession(sessionId)
        return (policy.servers?.contains(serverId.lowercase()) != false) &&
            (policy.tools[serverId]?.contains(tool) != false)
    }

    override suspend fun issueTaskGrant(task: BotAssignment, sourceSessionId: String): BotTaskCapabilityGrant {
        val owner = requireNotNull(bots.getBot(task.botId)) { "执行成员已不存在" }
        val issuerId = requireNotNull(task.reportToBotId) { "任务缺少派单成员" }
        val issuer = requireNotNull(bots.getBot(issuerId)) { "派单成员已不存在" }
        val policy = policyFor(owner).intersect(policyFor(issuer)).intersect(policyForSession(sourceSessionId))
        return BotTaskCapabilityGrant(task.id, task.botId, task.groupId, issuerId, task.parentAssignmentId, policy)
    }

    private suspend fun policyForSession(sessionId: String): BotCapabilityPolicy {
        val task = assignments.assignmentForSession(sessionId)
        if (task?.capabilityGrant == null) {
            // 历史任务 grant 为 null、Bot 行已删时不要炸循环：拒权即可。
            val bot = bots.getBotBySessionId(sessionId) ?: task?.botId?.let { bots.getBot(it) }
            return usable(
                when {
                    bot != null -> policyFor(bot)
                    task != null -> denied
                    else -> unrestricted
                },
            )
        }
        var policy = unrestricted
        var current: BotAssignment = task
        val seen = mutableSetOf<String>()
        while (true) {
            if (!seen.add(current.id) || seen.size > 8 || !current.status.isActive) return denied
            val owner = bots.getBot(current.botId) ?: return denied
            policy = policy.intersect(policyFor(owner))
            current.capabilityGrant?.let { grant ->
                if (!grant.matches(current)) return denied
                val issuer = bots.getBot(grant.issuerBotId) ?: return denied
                policy = policy.intersect(grant.policy).intersect(policyFor(issuer))
            }
            val parentId = current.parentAssignmentId ?: return usable(policy)
            val parent = assignments.get(parentId) ?: return denied
            if (parent.groupId != task.groupId || parent.botId != current.reportToBotId) return denied
            current = parent
        }
    }

    private fun usable(policy: BotCapabilityPolicy): BotCapabilityPolicy =
        if (policy.isExpired(now())) denied else policy

    private suspend fun policyFor(bot: Bot): BotCapabilityPolicy {
        val entries = bot.capabilityServerNames
        val canonical = if (entries == null) null else {
            val configured = servers.servers.first()
            entries.map { entry ->
                configured.firstOrNull {
                    it.id.equals(entry, ignoreCase = true) || it.name.equals(entry, ignoreCase = true)
                }?.id?.lowercase() ?: entry.lowercase()
            }.distinct()
        }
        return BotCapabilityPolicy(canonical, bot.capabilityToolNames, bot.capabilityExpiresAt)
    }

    private companion object {
        val unrestricted = BotCapabilityPolicy(null, emptyMap())
        val denied = BotCapabilityPolicy(emptyList(), emptyMap())
    }
}
