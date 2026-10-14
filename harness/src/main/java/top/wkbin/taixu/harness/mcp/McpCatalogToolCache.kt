package top.wkbin.taixu.harness.mcp

import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import top.wkbin.taixu.core.common.McpCatalogRevisions
import top.wkbin.taixu.core.model.McpToolInfo

internal data class CachedMcpTools(
    val fingerprint: String,
    val tools: List<McpToolInfo>,
    val distribution: String,
    val revision: Long,
)

/** 升级只撤销同一发行版的旧工具目录，不取消已派发的工具调用。 */
internal fun CoroutineScope.expireCatalogTools(
    cache: ConcurrentHashMap<String, CachedMcpTools>,
    expired: suspend (String) -> Unit,
) = launch {
    McpCatalogRevisions.changes.collect { changes ->
        cache.entries.toList().forEach { (key, value) ->
            val revision = changes[McpCatalogRevisions.Key(value.distribution, key.mcpServerKey())] ?: 0L
            if (value.distribution.isNotBlank() && revision > value.revision && cache.remove(key, value)) expired(key)
        }
    }
}
