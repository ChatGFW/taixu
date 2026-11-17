package top.wkbin.taixu.ui.workspace

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import top.wkbin.taixu.core.tools.InstalledAndroidSdk
import top.wkbin.taixu.ui.components.RuntimeActionRow
import top.wkbin.taixu.ui.components.RuntimeButton
import top.wkbin.taixu.ui.components.RuntimeDropdownField
import top.wkbin.taixu.ui.components.RuntimeLinearProgressIndicator
import top.wkbin.taixu.ui.components.RuntimeOutlinedButton
import top.wkbin.taixu.ui.components.SectionHeader
import top.wkbin.taixu.ui.miuix.RuntimeTheme
import top.yukonga.miuix.kmp.basic.Text

internal fun LazyListScope.workshopSdkItems(
    state: WorkshopSdkState,
    sdkPath: String,
    aapt2Path: String,
    viewModel: WorkshopSdkViewModel?,
    onChoose: (InstalledAndroidSdk) -> Unit,
) {
    item(key = "sdk-heading") {
        SectionHeader("Android SDK 版本", "Linux ARM64 · ${state.distroId} · HomuHomu833/android-sdk-custom")
    }
    item(key = "sdk-installed") {
        val selected = state.installed.indexOfFirst { it.path == sdkPath && it.aapt2Path == aapt2Path }
        val options = listOf("请选择已安装版本") + state.installed.map { it.version }
        RuntimeDropdownField(
            title = "已安装版本",
            items = options,
            selectedIndex = selected + 1,
            onSelectedIndexChange = { index ->
                if (!state.busy) state.installed.getOrNull(index - 1)?.let { viewModel?.choose(it, onChoose) }
            },
            summary = "选择后点击保存环境生效；同时设置 SDK 和 AAPT2 路径",
        )
    }
    item(key = "sdk-download") { SdkDownloadControls(state, viewModel) }
}

@Composable
private fun SdkDownloadControls(state: WorkshopSdkState, viewModel: WorkshopSdkViewModel?) {
    var version by rememberSaveable { mutableStateOf("") }
    val index = state.releases.indexOfFirst { it.version == version }.coerceAtLeast(0)
    val release = state.releases.getOrNull(index)
    val installed = release != null && state.installed.any { it.version == release.version }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (release != null) {
            RuntimeDropdownField(
                title = "在线版本",
                items = state.releases.map { it.version },
                selectedIndex = index,
                onSelectedIndexChange = { version = state.releases[it].version },
                summary = "${if (release.size > 0) "约 ${release.size / (1024 * 1024)} MiB" else "大小未知"} · ${if (release.sha256 != null) "SHA-256 校验" else "缺少 SHA-256，不可安装"}",
            )
        }
        Text(
            "在线包提供 SDK 工具。Android API 平台与基础 Build-Tools 35 复用本地套件，支持下载多个版本并随时切换。",
            style = RuntimeTheme.typography.bodySmall,
            color = RuntimeTheme.colorScheme.onSurfaceVariant,
        )
        RuntimeActionRow(modifier = Modifier.fillMaxWidth()) {
            RuntimeOutlinedButton(onClick = { viewModel?.refresh() }, enabled = !state.busy) {
                Text(if (state.fetched) "刷新版本" else "获取在线版本")
            }
            if (release != null) {
                RuntimeButton(onClick = { viewModel?.download(release) }, enabled = !state.busy && !installed && release.sha256 != null) {
                    Text(if (installed) "已安装" else "下载所选版本")
                }
            }
            if (state.busy) RuntimeOutlinedButton(onClick = { viewModel?.cancel() }) { Text("取消") }
        }
        if (state.busy) {
            val progress = state.progress
            RuntimeLinearProgressIndicator(progress = progress?.let { { it } }, modifier = Modifier.fillMaxWidth())
        }
        if (state.message.isNotBlank()) Text(state.message, style = RuntimeTheme.typography.bodySmall)
        state.error?.let {
            Text(it, color = RuntimeTheme.colorScheme.error, style = RuntimeTheme.typography.bodySmall, maxLines = 5, overflow = TextOverflow.Ellipsis)
        }
    }
}
