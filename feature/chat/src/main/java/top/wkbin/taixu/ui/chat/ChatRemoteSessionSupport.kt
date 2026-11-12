package top.wkbin.taixu.ui.chat

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import top.wkbin.taixu.runtime.SessionWorkspaceCatalog
import top.wkbin.taixu.runtime.SessionWorkspaceCatalogState
import top.wkbin.taixu.runtime.terminal.TerminalSessionManager

/**
 * 侧边栏远程项目目录订阅、创建校验与远程终端；不改全局主系统。
 * 从 ChatViewModel 抽出以守尺寸棘轮。
 */
internal class ChatRemoteSessionSupport(
    private val scope: CoroutineScope,
    private val workspaceCatalogSource: SessionWorkspaceCatalog,
    private val terminalSessionManager: TerminalSessionManager,
    private val onNotice: (String) -> Unit,
) {
    private val remoteProjectRequests = MutableStateFlow<Set<String>>(emptySet())

    @OptIn(ExperimentalCoroutinesApi::class)
    val remoteProjectsByServer: StateFlow<Map<String, SessionWorkspaceCatalogState>> =
        remoteProjectRequests.flatMapLatest { ids ->
            if (ids.isEmpty()) flowOf(emptyMap())
            else combine(ids.map { id -> workspaceCatalogSource.observeServer(id).map { id to it } }) { pairs ->
                pairs.toMap()
            }
        }.stateIn(scope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    fun watchRemoteProjects(serverId: String) {
        if (serverId.isBlank()) return
        remoteProjectRequests.update { it + serverId }
    }

    suspend fun createSession(
        title: String,
        workspace: String,
        projectType: String,
        distribution: String?,
        localCatalog: SessionWorkspaceCatalogState,
        defaultTitle: String,
        newSession: suspend (String, String, String, String?) -> Unit,
    ) {
        try {
            when {
                distribution == null -> workspaceCatalogSource.validate(localCatalog, workspace)
                distribution.startsWith("ssh:") -> {
                    val serverId = distribution.removePrefix("ssh:")
                    val catalog = remoteProjectsByServer.value[serverId]
                    if (catalog != null && workspace.isNotBlank() && workspace != "/workspace") {
                        workspaceCatalogSource.validateRemote(catalog, serverId, workspace)
                    }
                }
            }
            newSession(title.trim().ifBlank { defaultTitle }, workspace, projectType, distribution)
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            onNotice(e.message ?: "会话创建失败，请检查执行环境")
        }
    }

    fun createRemoteTerminal(
        distribution: String,
        label: String = "",
        onResult: (Boolean) -> Unit = {},
    ) {
        scope.launch {
            val ok = runCatching {
                val name = label.trim().ifBlank {
                    remoteProjectsByServer.value[distribution.removePrefix("ssh:")]?.server?.name
                        ?: distribution.removePrefix("ssh:")
                }
                terminalSessionManager.createSession(
                    label = name,
                    workingDirectory = "/root",
                    distributionId = distribution,
                )
            }.onFailure { onNotice(it.message ?: "远程终端创建失败") }.isSuccess
            onResult(ok)
        }
    }
}
