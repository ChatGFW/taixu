package top.wkbin.taixu.ui.developer

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.compose.koinInject
import top.wkbin.taixu.core.datastore.RemoteServerRepository
import top.wkbin.taixu.runtime.remote.RemoteSystemInspection
import top.wkbin.taixu.runtime.remote.RemoteSystemService
import top.wkbin.taixu.ui.components.*
import top.wkbin.taixu.ui.components.runtimeContentPadding
import top.wkbin.taixu.ui.components.runtimeScrollContentPadding
import top.wkbin.taixu.ui.components.runtimeScrollViewport
import top.wkbin.taixu.ui.miuix.RuntimeSpacing
import top.wkbin.taixu.ui.miuix.RuntimeTheme
import top.wkbin.taixu.ui.miuix.Scaffold

@Composable
fun DeveloperScreen(onBack: () -> Unit, viewModel: DeveloperViewModel? = null) {
    val repository = koinInject<RemoteServerRepository>()
    val active by repository.activeId.collectAsStateWithLifecycle()
    val servers by repository.servers.collectAsStateWithLifecycle()
    if (active == null) LocalDeveloperScreen(onBack, viewModel ?: org.koin.compose.viewmodel.koinViewModel())
    else key(active, servers.firstOrNull { it.id == active }) { RemoteDeveloperScreen(checkNotNull(active), onBack) }
}

@Composable
internal fun RemoteDeveloperScreen(serverId: String, onBack: () -> Unit, service: RemoteSystemService? = previewOrNull { koinInject() }) {
    var refresh by remember { mutableIntStateOf(0) }
    val target = service?.let { runCatching { it.server(serverId) }.getOrNull() }
    val state by remember(serverId, refresh, service) {
        service?.observe(serverId, target) ?: kotlinx.coroutines.flow.flowOf(RemoteSystemInspection(loading = false))
    }.collectAsStateWithLifecycle(RemoteSystemInspection(loading = service != null))
    Scaffold(containerColor = RuntimeTheme.colorScheme.background,
        topBar = { RuntimeTopBar(title = "系统运行状态", statusText = target?.name ?: "服务器", onBack = onBack,
            actions = { RuntimeIconButton(onClick = { refresh++ }, contentDescription = "刷新") { RuntimeIcon(RuntimeIconName.Refresh) } }) },
    ) { padding ->
        Column(Modifier.fillMaxSize().then(runtimeScrollViewport(padding)).verticalScroll(rememberScrollState()).padding(runtimeScrollContentPadding(padding, runtimeContentPadding(top = RuntimeSpacing.pageTop, bottom = RuntimeSpacing.pageBottom))), verticalArrangement = Arrangement.spacedBy(RuntimeSpacing.cardGap)) {
            SystemOverviewCard(target?.name ?: "服务器", target?.let { "${it.username}@${it.host}:${it.port}" }.orEmpty(),
                target?.workspace.orEmpty(), state.loading, state.error, state.metrics?.let { listOf(
                    "发行版" to it.distribution, "架构" to it.architecture, "内核" to it.kernel,
                    "进程" to it.processCount.toString(), "内存" to "${it.memoryUsedMb} / ${it.memoryTotalMb} MB", "负载" to it.load,
                ) }.orEmpty())
            service?.let { RemoteCommandPanel(serverId, it) }
        }
    }
}
