package top.wkbin.taixu.ui.settings

import java.io.File
import top.wkbin.taixu.core.datastore.AgentServerPreferences
import top.wkbin.taixu.core.model.McpServerConfig
import top.wkbin.taixu.core.model.McpTransportType

sealed interface LocalMcpDiscoveryState {
    data object Idle : LocalMcpDiscoveryState
    data object Scanning : LocalMcpDiscoveryState
    data class Results(val servers: List<DetectedLocalMcpServer>) : LocalMcpDiscoveryState
    data class Error(val message: String) : LocalMcpDiscoveryState
}

data class DetectedLocalMcpServer(
    val server: McpServerConfig,
    val toolCount: Int,
) {
    val serverUrl: String get() = server.serverUrl
}

/** 从 Linux proc socket 表提取可由 127.0.0.1 访问的监听端口。 */
internal fun localLoopbackMcpCandidates(): List<DetectedLocalMcpServer> {
    val ports = linkedSetOf<Int>()
    listOf("/proc/net/tcp", "/proc/net/tcp6").forEach { path ->
        runCatching { File(path).readLines() }.getOrDefault(emptyList()).drop(1).forEach { line ->
            val fields = line.trim().split(Regex("\\s+"))
            val local = fields.getOrNull(1) ?: return@forEach
            if (fields.getOrNull(3) != "0A") return@forEach // TCP_LISTEN
            val address = local.substringBefore(':')
            val port = local.substringAfter(':', "").toIntOrNull(16) ?: return@forEach
            val loopbackOrAny = address == "0100007F" || address.all { it == '0' } || address.endsWith("00000001")
            if (loopbackOrAny && port in 1..65535) ports += port
        }
    }
    // 某些 Android 版本会限制 /proc 可见性；保留常见开发端口作为小范围回退。
    if (ports.isEmpty()) ports += listOf(3000, 3001, 4000, 5000, 5173, 8000, 8080, 8787, 9000)

    return ports.take(32).flatMap { port ->
        listOf("mcp", "sse").map { path ->
            val url = "http://127.0.0.1:$port/$path"
            DetectedLocalMcpServer(
                server = McpServerConfig(
                    id = "local_probe_${port}_$path",
                    name = "本机 MCP ($port)",
                    description = "在 127.0.0.1:$port 上自动探测到的 MCP 服务",
                    transportType = McpTransportType.SSE,
                    serverUrl = url,
                    isEnabled = true,
                    isBuiltin = false,
                ),
                toolCount = 0,
            )
        }
    }
}

/** MCP 被控端配置快照（运行状态另由 AgentMcpAccess 提供）。 */
data class AgentServerState(
    val enabled: Boolean = false,
    val port: Int = AgentServerPreferences.DEFAULT_PORT,
    val allowRemote: Boolean = false,
    val allowWriteTools: Boolean = false,
    val token: String = "",
)
