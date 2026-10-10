package top.wkbin.taixu.ui.chat

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.unit.dp
import org.koin.compose.viewmodel.koinViewModel

/**
 * 沙箱工具链面板宿主 —— 自持全部状态。
 *
 * 之所以做成独立宿主：architecture-policy.json 的行数棘轮要求
 * ChatScreen.kt / ChatWorkbenchPanels.kt 只许缩减、不许增长，
 * 因此工具链相关的状态、面板挂载一律外置，ChatScreen 侧只保留入口接线。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ToolchainHost(visible: Boolean, onDismiss: () -> Unit, onGapChanged: (Boolean) -> Unit) {
    if (visible) ToolchainSheet(onDismiss, onGapChanged)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ToolchainSheet(
    onDismiss: () -> Unit,
    onReportChanged: (top.wkbin.taixu.core.model.ToolchainReport) -> Unit,
) {
    val viewModel: ToolchainViewModel = koinViewModel()
    val report by viewModel.report.collectAsState()

    // 后台静默探一次：用户还没点开，顶部入口就已经有红点提示
    LaunchedEffect(Unit) {
        viewModel.refresh()
        report?.let { onReportChanged(it) }
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Box(modifier = Modifier.padding(bottom = 24.dp)) {
            ToolchainPanel(viewModel = viewModel)
        }
    }
}
