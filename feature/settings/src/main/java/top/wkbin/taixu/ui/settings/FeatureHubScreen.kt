package top.wkbin.taixu.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.koin.compose.viewmodel.koinViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import top.wkbin.taixu.ui.components.RuntimeIconName
import top.wkbin.taixu.ui.components.RuntimeTopBar

/**
 * 乾坤 · 特色功能二级页面：聚合 5 个特色功能入口（原首页快捷入口统一收纳于此）——
 * 一句话做 App · AI 圆桌会议 · 晨报哨兵 · WebChat 电脑大屏协作 · A2UI 界面。
 * 晨报哨兵与 WebChat 内联控制（开关与运行详情），其余跳转对应页面。
 */
@Composable
fun FeatureHubScreen(
    onBack: () -> Unit,
    onOpenCustomIteration: () -> Unit,
    onStartRoundtable: () -> Unit,
    onOpenA2uiPoc: () -> Unit,
    viewModel: FeatureHubViewModel = koinViewModel(),
) {
    val sentinelState by viewModel.sentinelState.collectAsStateWithLifecycle()
    val webChatStatus by viewModel.webChatStatus.collectAsStateWithLifecycle()

    Scaffold(
        topBar = { RuntimeTopBar("特色功能", onBack) },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                FeatureLinkRow(
                    icon = RuntimeIconName.Code,
                    accent = MaterialTheme.colorScheme.primary,
                    title = "一句话做 App",
                    subtitle = "描述需求，太墟自动构建并安装到手机",
                    onClick = onOpenCustomIteration,
                )
            }
            item {
                FeatureLinkRow(
                    icon = RuntimeIconName.Hub,
                    accent = MaterialTheme.colorScheme.tertiary,
                    title = "AI 圆桌会议",
                    subtitle = "三视角并行评审，总裁判汇总辩论报告",
                    onClick = onStartRoundtable,
                )
            }
            item {
                SentinelControlRow(
                    state = sentinelState,
                    onEnable = viewModel::enableSentinel,
                    onDisable = viewModel::disableSentinel,
                )
            }
            item {
                WebChatControlRow(
                    status = webChatStatus,
                    onToggle = viewModel::toggleWebChat,
                )
            }
            item {
                FeatureLinkRow(
                    icon = RuntimeIconName.Terminal,
                    accent = MaterialTheme.colorScheme.secondary,
                    title = "A2UI 界面",
                    subtitle = "智能体把回答画成原生界面 · 查看最近界面或注入示例",
                    onClick = onOpenA2uiPoc,
                )
            }
        }
    }
}
