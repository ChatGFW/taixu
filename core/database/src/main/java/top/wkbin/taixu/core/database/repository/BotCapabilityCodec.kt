package top.wkbin.taixu.core.database.repository

import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray

/**
 * Bot 级 MCP 能力白名单的 JSON 编解码（从 PersonaBotRepository 抽出以控制文件尺寸）。
 * 兼容旧服务数组；带工具限制时使用版本化对象。损坏或未知版本按无可用服务处理。
 */
internal fun decodeCapabilityServers(raw: String?): List<String>? = raw?.let {
    runCatching {
        if (botCapabilityJson.parseToJsonElement(it) is JsonArray) {
            botCapabilityJson.decodeFromString(ListSerializer(String.serializer()), it)
        } else decodeStoredCapabilities(it).servers
    }.getOrDefault(emptyList())
}

internal fun decodeCapabilityTools(raw: String?): Map<String, List<String>> = raw?.let {
    runCatching { decodeStoredCapabilities(it).tools }.getOrDefault(emptyMap())
}.orEmpty()

internal fun decodeCapabilityExpiry(raw: String?): Long? = raw?.let {
    runCatching { decodeStoredCapabilities(it).expiresAt }.getOrNull()
}

internal fun encodeCapabilityServers(names: List<String>?, tools: Map<String, List<String>> = emptyMap(), expiresAt: Long? = null): String? {
    if (tools.isNotEmpty() || expiresAt != null) return botCapabilityJson.encodeToString(StoredBotCapabilities(version = 1, servers = names, tools = tools, expiresAt = expiresAt))
    return names?.let { botCapabilityJson.encodeToString(ListSerializer(String.serializer()), it) }
}

private fun decodeStoredCapabilities(raw: String): StoredBotCapabilities =
    botCapabilityJson.decodeFromString<StoredBotCapabilities>(raw).also {
        require(it.version == 1 && (it.expiresAt == null || it.expiresAt > 0))
    }

@Serializable
private data class StoredBotCapabilities(
    val version: Int,
    val servers: List<String>?,
    val tools: Map<String, List<String>>,
    val expiresAt: Long? = null,
)

private val botCapabilityJson = Json { ignoreUnknownKeys = true }
