package top.wkbin.taixu.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.compose.koinInject
import top.wkbin.taixu.core.datastore.RemoteServerRepository
import top.wkbin.taixu.core.model.RemoteServer
import top.wkbin.taixu.ui.components.RuntimeCard
import top.wkbin.taixu.ui.components.RuntimeIcon
import top.wkbin.taixu.ui.components.RuntimeIconName
import top.wkbin.taixu.ui.miuix.RuntimeTheme
import top.yukonga.miuix.kmp.basic.Text

/** 首页远程入口：有连接档案才展示，不切换本机主系统。 */
@Composable
internal fun HomeRemoteServersSection(onOpen: (serverId: String) -> Unit) {
    val remoteServers by koinInject<RemoteServerRepository>().servers.collectAsStateWithLifecycle()
    if (remoteServers.isNotEmpty()) {
        RemoteServersOverviewCard(servers = remoteServers, onOpen = onOpen)
    }
}

/** 首页远程服务器入口：打开独立概览，不切换本机主系统。 */
@Composable
private fun RemoteServersOverviewCard(
    servers: List<RemoteServer>,
    onOpen: (serverId: String) -> Unit,
) {
    RuntimeCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RuntimeTheme.shapes.extraLarge,
        containerColor = RuntimeTheme.colorScheme.surfaceContainer,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("远程服务器", style = RuntimeTheme.typography.titleMedium)
            Text(
                "查看指标不切换本机主系统；也可在聊天侧边栏按服务器新建对话。",
                style = RuntimeTheme.typography.bodySmall,
                color = RuntimeTheme.colorScheme.onSurfaceVariant,
            )
            servers.forEach { server ->
                RuntimeCard(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { onOpen(server.id) },
                    shape = RuntimeTheme.shapes.large,
                    containerColor = RuntimeTheme.colorScheme.surfaceContainerHigh,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        RuntimeIcon(RuntimeIconName.Server, Modifier.size(22.dp))
                        Column(Modifier.weight(1f)) {
                            Text(server.name, style = RuntimeTheme.typography.titleSmall, maxLines = 1)
                            Text(
                                "${server.username}@${server.host}:${server.port}",
                                style = RuntimeTheme.typography.bodySmall,
                                color = RuntimeTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                            )
                        }
                        RuntimeIcon(RuntimeIconName.ChevronRight, Modifier.size(18.dp), RuntimeTheme.colorScheme.outline)
                    }
                }
            }
        }
    }
}
