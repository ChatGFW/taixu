package top.wkbin.taixu.ui.chat

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import org.koin.compose.viewmodel.koinViewModel
import top.wkbin.taixu.ui.components.RuntimeIconName

/**
 * 沙箱工具链入口项 —— 从 [CollapsibleChatWorkbenchStrip] 抽出，单独成文件。
 *
 * 红点主动亮：入口项随工具条首次组合时即触发一次全量检测，
 * 检测到缺口时 [hasGap] 置真、红点亮起，无需用户先打开面板。
 */
@Composable
internal fun ToolchainStripEntry(
    toolchainHighlight: Boolean,
    onOpenToolchain: () -> Unit,
) {
    val viewModel: ToolchainViewModel = koinViewModel()
    val hasGap by viewModel.hasGap.collectAsState()

    // 首次组合主动检测，让入口红点不依赖面板是否打开
    LaunchedEffect(Unit) { viewModel.refresh() }

    StatusDivider()
    WorkbenchStatusItem(
        icon = RuntimeIconName.Hammer,
        label = if (toolchainHighlight || hasGap) {
            stringResource(R.string.toolchain_entry_gap)
        } else {
            stringResource(R.string.toolchain_entry)
        },
        tint = if (toolchainHighlight || hasGap) Color(0xFFC62828) else MaterialTheme.colorScheme.onSurfaceVariant,
        highlight = toolchainHighlight || hasGap,
        onClick = onOpenToolchain,
    )
}
