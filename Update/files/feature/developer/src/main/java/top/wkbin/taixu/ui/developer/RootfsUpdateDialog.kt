package top.wkbin.taixu.ui.developer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import top.wkbin.taixu.runtime.RegistryRoute
import top.wkbin.taixu.runtime.toRegistryRoute
import top.wkbin.taixu.ui.components.RuntimeAlertDialog
import top.wkbin.taixu.ui.components.RuntimeOutlinedButton as OutlinedButton
import top.wkbin.taixu.ui.components.RuntimeTextButton as TextButton
import top.wkbin.taixu.ui.developer.LocalizedText as Text

/**
 * RootFS 更新确认弹窗：在更新前选择实际下载线路。
 *
 * 更新前可显式选择线路；选择结果写回镜像策略，后续检查与更新会沿用。
 *
 * 三个选项竖排全宽：横排时按钮宽度不足，「国内镜像」会被折断成两行。
 */
@Composable
internal fun RootfsUpdateDialog(viewModel: DeveloperViewModel?, onDismiss: () -> Unit) {
    val savedPolicy by viewModel?.mirrorPolicy?.collectAsStateWithLifecycle(initialValue = "auto")
        ?: remember { mutableStateOf("auto") }
    var route by remember(savedPolicy) { mutableStateOf(savedPolicy.toRegistryRoute()) }
    RuntimeAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("更新 Linux RootFS？") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("将在线下载并校验新版本，保留 /root 与 /opt/taixu。更新期间后台 Linux 进程会停止，失败时自动恢复旧版本。")
                Text("下载线路（选择后长期生效）：")
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    RouteChip("自动加速", route == RegistryRoute.AUTO) { route = RegistryRoute.AUTO }
                    RouteChip("官方直连", route == RegistryRoute.OFFICIAL) { route = RegistryRoute.OFFICIAL }
                    RouteChip("国内镜像", route == RegistryRoute.CHINA_ACCELERATED) { route = RegistryRoute.CHINA_ACCELERATED }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onDismiss(); viewModel?.updateRootfs(route) }) { Text("确认更新") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

@Composable
private fun RouteChip(label: String, selected: Boolean, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Text(if (selected) "● $label" else "○ $label", maxLines = 1)
    }
}
