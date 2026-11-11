package top.wkbin.taixu.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.compose.viewmodel.koinViewModel
import top.wkbin.taixu.core.model.PluginLibraryChannel
import top.wkbin.taixu.core.tools.LocalPluginImportState
import top.wkbin.taixu.core.tools.RemotePluginSource
import top.wkbin.taixu.feature.settings.R
import top.wkbin.taixu.ui.components.*
import top.wkbin.taixu.ui.miuix.FilterChip
import top.wkbin.taixu.ui.miuix.RuntimeSpacing
import top.wkbin.taixu.ui.miuix.RuntimeTheme
import top.wkbin.taixu.ui.miuix.Scaffold
import top.yukonga.miuix.kmp.basic.Text

@Composable
fun RemotePluginLibraryScreen(onBack: () -> Unit) {
    LocalSettingsGate(stringResource(R.string.plugin_library_title), onBack) { RemotePluginsScreen(onBack) }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun RemotePluginsScreen(onBack: () -> Unit, model: RemotePluginsViewModel = koinViewModel()) {
    androidx.activity.compose.BackHandler(onBack = onBack)
    val state by model.state.collectAsStateWithLifecycle()
    val installed by model.installed.collectAsStateWithLifecycle()
    val web by model.webPackages.collectAsStateWithLifecycle()
    val importing by model.importing.collectAsStateWithLifecycle()
    val importingBusy = importing is LocalPluginImportState.Reading || importing is LocalPluginImportState.Importing || importing is LocalPluginImportState.PendingConfirmation
    Scaffold(topBar = { RuntimeTopBar(title = stringResource(R.string.plugin_library_title), onBack = onBack) }) { insets ->
        LazyColumn(Modifier.fillMaxSize().padding(insets),
            contentPadding = runtimeContentPadding(top = RuntimeSpacing.pageTop, bottom = RuntimeSpacing.pageBottom),
            verticalArrangement = Arrangement.spacedBy(RuntimeSpacing.cardGap)) {
            item {
                RuntimeCard(Modifier.fillMaxWidth()) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("太墟官方插件库", style = RuntimeTheme.typography.titleLarge)
                        Text("仓库渠道", style = RuntimeTheme.typography.labelLarge)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            PluginLibraryChannel.entries.forEach { channel ->
                                FilterChip(
                                    selected = state.channel == channel,
                                    onClick = { model.selectChannel(channel) },
                                    enabled = !state.busy && !importingBusy && state.pendingWeb == null,
                                    label = { Text(if (channel == PluginLibraryChannel.GITHUB) "GitHub" else "Gitee") },
                                )
                            }
                        }
                        Text(if (state.channel == PluginLibraryChannel.GITEE) "从 Gitee 下载官方插件，也可切换到 GitHub。" else "从 GitHub 下载官方插件，也可切换到 Gitee。", style = RuntimeTheme.typography.bodySmall)
                        Text("浏览、下载和更新离线插件。大包支持分片重试，下载完成后确认安装。", style = RuntimeTheme.typography.bodyMedium)
                        RuntimeFilledTonalButton(onClick = model::refresh, enabled = !state.busy && !importingBusy) { Text("检查更新") }
                        if (state.operationPluginId == null) state.message?.let { Text(it, color = if (state.error) RuntimeTheme.colorScheme.error else RuntimeTheme.colorScheme.onSurfaceVariant) }
                        if (state.busy && state.operationPluginId == null) {
                            RuntimeLinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                            RuntimeTextButton(onClick = model::cancelDownload) { Text("取消") }
                        }
                        if (!state.busy && state.releases.isEmpty() && !state.error) Text("插件库尚未发布兼容插件")
                    }
                }
            }
            items(state.releases, key = { it.pluginId }) { release ->
                val local = remotePluginInstallation(release, installed, web)
                val canInstall = !local.installed || local.updateAvailable
                val ownsOperation = state.operationPluginId == release.pluginId
                val available = RemotePluginSource.available(release, state.channel)
                RuntimeCard(Modifier.fillMaxWidth()) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(release.name, style = RuntimeTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(release.description, style = RuntimeTheme.typography.bodyMedium)
                        Text("${release.execution} · ${release.version} · ${pluginDownloadSize(release.size)} · ${release.parts.size} 个文件", style = RuntimeTheme.typography.labelMedium)
                        Text(if (local.installing) stringResource(R.string.plugin_library_installing)
                            else local.version?.let { stringResource(R.string.plugin_library_installed_version, it) }
                                ?: stringResource(R.string.plugin_library_not_installed), style = RuntimeTheme.typography.bodySmall)
                        if (local.updateAvailable) Text(stringResource(R.string.plugin_library_update_available, release.version),
                            style = RuntimeTheme.typography.labelMedium, color = RuntimeTheme.colorScheme.primary)
                        if (!available) Text("未同步到 Gitee，可切换至 GitHub 下载", style = RuntimeTheme.typography.bodySmall)
                        if (ownsOperation) {
                            state.message?.let { Text(it, style = RuntimeTheme.typography.bodySmall,
                                color = if (state.error) RuntimeTheme.colorScheme.error else RuntimeTheme.colorScheme.onSurfaceVariant) }
                            if (state.busy) {
                                RuntimeLinearProgressIndicator(progress = state.progress?.let { value -> { value } }, modifier = Modifier.fillMaxWidth())
                                if (state.downloading) {
                                    val percent = if (state.totalBytes > 0) (state.downloadedBytes * 100 / state.totalBytes).toInt() else 0
                                    FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Text(stringResource(R.string.plugin_library_download_percent, percent), style = RuntimeTheme.typography.labelLarge)
                                        Text("${pluginDownloadSize(state.downloadedBytes)} / ${pluginDownloadSize(state.totalBytes)}",
                                            style = RuntimeTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    }
                                }
                            }
                        }
                        if (ownsOperation && state.downloading) {
                            RuntimeFilledTonalButton(onClick = model::cancelDownload) { Text(stringResource(R.string.plugin_library_stop_download)) }
                        } else RuntimeButton(onClick = { model.download(release) }, enabled = !state.busy && !importingBusy && !local.installing && canInstall && available) {
                            Text(if (local.installing) stringResource(R.string.plugin_library_installing)
                                else if (local.installed && !local.updateAvailable) stringResource(R.string.plugin_library_installed)
                                else if (!available) "当前渠道未提供" else if (local.updateAvailable) "下载更新" else "下载插件")
                        }
                    }
                }
            }
            item {
                when (val value = importing) {
                    is LocalPluginImportState.Reading -> Text("正在验证插件包…")
                    is LocalPluginImportState.Importing -> Text(value.currentEntry ?: "正在安装插件…")
                    is LocalPluginImportState.Succeeded -> Text("${value.pluginName} ${value.version} 安装完成")
                    is LocalPluginImportState.Failed -> Text(value.message, color = RuntimeTheme.colorScheme.error)
                    is LocalPluginImportState.AlreadyImported -> Text("此版本已导入，可返回工具详情安装或重试")
                    else -> Unit
                }
            }
        }
    }
    (importing as? LocalPluginImportState.PendingConfirmation)?.let {
        LocalPluginImportConfirmation(it, model::cancelProcess, model::confirmProcess)
    }
    state.pendingWeb?.let { pending ->
        RuntimeAlertDialog(onDismissRequest = model::cancelWeb,
            title = { Text("导入 Web 插件") },
            text = { Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("${pending.preview.manifest.name} · ${pending.preview.manifest.version}")
                Text(pending.preview.manifest.description)
                Text("导入后可在外部 Web 插件页打开，页面能力仍需原生授权。")
                pending.preview.manifest.plugin?.capabilities?.forEach { Text("${it.name} · ${it.target}") }
            } },
            confirmButton = { RuntimeTextButton(onClick = { model.confirmWeb(pending) }, enabled = !state.busy) { Text("确认导入") } },
            dismissButton = { RuntimeTextButton(onClick = model::cancelWeb, enabled = !state.busy) { Text("取消") } })
    }
}

private fun pluginDownloadSize(bytes: Long): String =
    String.format(java.util.Locale.getDefault(), "%.1f MiB", bytes / 1048576.0)
