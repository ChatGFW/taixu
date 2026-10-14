package top.wkbin.taixu.harness.mcp

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.*
import top.wkbin.taixu.core.model.McpConnectionState

/** Settings show the selected environment's status, never another chat owner's connection. */
internal class McpScopedConnectionStates(private val standalone: StandaloneMcpScope?, scope: CoroutineScope) {
    private val all = MutableStateFlow<Map<String, McpConnectionState>>(emptyMap())
    val visible = combine(all, standalone?.changes ?: flowOf(null)) { states, selected ->
        val suffix = selected?.let { "@environment:$it" }.orEmpty()
        states.filterKeys { it == it.mcpServerKey() + suffix }.mapKeys { it.key.mcpServerKey() }
    }.stateIn(scope, SharingStarted.Eagerly, emptyMap())
    fun update(state: McpConnectionState, key: String) { all.update { it + (key to state) } }
    fun forget(serverId: String) { all.update { states -> states.filterKeys { it.mcpServerKey() != serverId } } }
    fun forgetKey(key: String) { all.update { it - key } }
}
