package top.wkbin.taixu.core.model.bot

import kotlinx.serialization.Serializable

/** 用户保存的完整 MCP 能力配置；服务白名单为 null 时允许全部服务。 */
@Serializable
data class BotCapabilityPolicy(
    val servers: List<String>?,
    val tools: Map<String, List<String>>,
    val expiresAt: Long? = null,
) {
    fun isExpired(now: Long): Boolean = expiresAt?.let { now >= it } == true

    /** 输入的服务使用规范 ID；缺省集合为不限制，空集合为全部拒绝。 */
    fun intersect(other: BotCapabilityPolicy): BotCapabilityPolicy {
        val commonServers = when {
            servers == null -> other.servers
            other.servers == null -> servers
            else -> servers.intersect(other.servers.toSet()).toList()
        }
        val commonTools = (tools.keys + other.tools.keys).associateWith { server ->
            val first = tools[server]
            val second = other.tools[server]
            when {
                first == null -> second.orEmpty()
                second == null -> first
                else -> first.intersect(second.toSet()).toList()
            }
        }
        return BotCapabilityPolicy(commonServers, commonTools, listOfNotNull(expiresAt, other.expiresAt).minOrNull())
    }
}
