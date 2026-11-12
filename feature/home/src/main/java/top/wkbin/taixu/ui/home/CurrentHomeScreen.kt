package top.wkbin.taixu.ui.home

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.util.Locale
import org.koin.compose.koinInject
import top.wkbin.taixu.feature.home.R
import top.wkbin.taixu.runtime.remote.RemoteSystemInspection
import top.wkbin.taixu.runtime.remote.RemoteSystemService
import top.wkbin.taixu.ui.components.*
import top.wkbin.taixu.ui.miuix.RuntimeSpacing
import top.wkbin.taixu.ui.miuix.RuntimeTheme
import top.wkbin.taixu.ui.miuix.Scaffold
import top.wkbin.taixu.ui.miuix.TopAppBarDefaults
import top.yukonga.miuix.kmp.basic.Text

@Composable
fun HomeScreen(
    onNavigate: (MainDestination) -> Unit, onOpenTerminal: () -> Unit, onOpenToolCenter: () -> Unit = {},
    onOpenRemoteHome: (serverId: String) -> Unit = {}, viewModel: HomeViewModel? = null,
    onBack: () -> Unit = { onNavigate(MainDestination.Agent) },
) {
    // 主系统恒为本机；远程概览经 onOpenRemoteHome 独立打开。
    LocalHomeScreen(
        onNavigate = onNavigate, onOpenTerminal = onOpenTerminal, onOpenToolCenter = onOpenToolCenter,
        onOpenRemoteHome = onOpenRemoteHome, viewModel = viewModel ?: org.koin.compose.viewmodel.koinViewModel(),
        onBack = onBack,
    )
}

@Composable
fun RemoteHomeScreen(serverId: String, onNavigate: (MainDestination) -> Unit, service: RemoteSystemService? = previewOrNull { koinInject() }, onBack: () -> Unit = { onNavigate(MainDestination.Agent) }) {
    var refresh by remember { mutableIntStateOf(0) }
    val target = service?.let { runCatching { it.server(serverId) }.getOrNull() }
    val state by remember(serverId, refresh, service) {
        service?.observe(serverId, target) ?: kotlinx.coroutines.flow.flowOf(RemoteSystemInspection(loading = false))
    }.collectAsStateWithLifecycle(RemoteSystemInspection(loading = service != null))
    val metrics = state.metrics
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Scaffold(modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection), containerColor = RuntimeTheme.colorScheme.surface,
        topBar = { RuntimeTopBar(title = androidx.compose.ui.res.stringResource(R.string.home_dashboard_title), onBack = onBack, scrollBehavior = scrollBehavior, actions = {
            RuntimeIconButton(onClick = { refresh++ }, contentDescription = "刷新") { RuntimeIcon(RuntimeIconName.Refresh) }
        }) },
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize()
                .then(runtimeScrollViewport(padding))
                .verticalScroll(rememberScrollState())
                .padding(runtimeScrollContentPadding(padding, runtimeContentPadding(top = RuntimeSpacing.pageTop, bottom = RuntimeSpacing.pageBottom))),
            verticalArrangement = Arrangement.spacedBy(RuntimeSpacing.cardGap),
        ) {
            Text("服务器概览", style = RuntimeTheme.typography.titleMedium)
            SystemOverviewCard(target?.name ?: "服务器", target?.let { "${it.username}@${it.host}:${it.port}" }.orEmpty(),
                target?.workspace.orEmpty(), state.loading, state.error, metrics?.let { listOf(
                    "发行版" to it.distribution, "架构" to it.architecture,
                    "内存" to "${it.memoryUsedMb} / ${it.memoryTotalMb} MB",
                    "存储" to String.format(Locale.ROOT, "%.1f / %.1f GB", it.diskUsedGb, it.diskTotalGb),
                    "负载 · 1/5/15 分钟" to it.load, "进程" to it.processCount.toString(),
                    "运行时间" to "${it.uptimeSeconds / 3600} 小时", "内核" to it.kernel,
                ) }.orEmpty())
            RuntimeActionRow() {
                RuntimeButton(modifier = Modifier, onClick = { onNavigate(MainDestination.Agent) }) { Text("打开智枢") }
            }
            Text("指标采集自该服务器，每 15 秒刷新；不切换本机主系统。", style = RuntimeTheme.typography.bodySmall,
                color = RuntimeTheme.colorScheme.onSurfaceVariant)
        }
    }
}
