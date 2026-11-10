package top.wkbin.taixu.harness.bot

import top.wkbin.taixu.core.model.bot.BotAssignment
import top.wkbin.taixu.core.model.bot.BotTaskCapabilityGrant


/**
 * Bot 级能力白名单端口（DS Bot 能力库语义）：null = 不限制（团队共享库全量可用）；
 * 否则仅返回集合内（服务 id 或名称，小写）的 MCP 服务可被该会话挂载与调用。
 * 由用户在 Bot 详情中配置，成员工具没有修改入口（"使用中锁定"由配置入口唯一性满足）。
 */
fun interface BotCapabilityScope {
    suspend fun allowedMcpServers(sessionId: String): Set<String>?

    /** null 为不限制，空集合为全部禁用；工具名大小写敏感。 */
    suspend fun allowedMcpTools(sessionId: String, serverId: String): Set<String>? = null

    suspend fun issueTaskGrant(task: BotAssignment, sourceSessionId: String):
        BotTaskCapabilityGrant? = null

    suspend fun allowsTool(sessionId: String, serverId: String, tool: String): Boolean =
        (allowedMcpServers(sessionId)?.contains(serverId.lowercase()) != false) &&
            (allowedMcpTools(sessionId, serverId)?.contains(tool) != false)
}
