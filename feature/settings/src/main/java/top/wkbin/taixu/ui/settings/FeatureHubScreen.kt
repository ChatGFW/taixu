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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.koin.compose.viewmodel.koinViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import top.wkbin.taixu.feature.settings.R
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
        topBar = { RuntimeTopBar(stringResource(R.string.settings_feature_hub_title), onBack) },
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
                    title = stringResource(R.string.settings_feature_custom_iteration_title),
                    subtitle = stringResource(R.string.settings_feature_custom_iteration_subtitle),
                    onClick = onOpenCustomIteration,
                )
            }
            item {
                FeatureLinkRow(
                    icon = RuntimeIconName.Hub,
                    accent = MaterialTheme.colorScheme.tertiary,
                    title = stringResource(R.string.settings_feature_roundtable_title),
                    subtitle = stringResource(R.string.settings_feature_roundtable_subtitle),
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
                    title = stringResource(R.string.settings_feature_a2ui_title),
                    subtitle = stringResource(R.string.settings_feature_a2ui_subtitle),
                    onClick = onOpenA2uiPoc,
                )
            }
        }
    }
}
