package top.wkbin.taixu.ui.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.compose.viewmodel.koinViewModel
import top.wkbin.taixu.core.model.InstalledDistro
import top.wkbin.taixu.core.model.RuntimeState
import top.wkbin.taixu.runtime.DistributionCatalog
import top.wkbin.taixu.runtime.DistributionSpec
import top.wkbin.taixu.runtime.RegistryRoute
import top.wkbin.taixu.runtime.RuntimeInstallRequest
import top.wkbin.taixu.ui.components.RuntimeActionRow
import top.wkbin.taixu.ui.components.RuntimeAlertDialog
import top.wkbin.taixu.ui.components.RuntimeButton as Button
import top.wkbin.taixu.ui.components.RuntimeCircularProgressIndicator as CircularProgressIndicator
import top.wkbin.taixu.ui.components.RuntimeIcon
import top.wkbin.taixu.ui.components.RuntimeLinearProgressIndicator as LinearProgressIndicator
import top.wkbin.taixu.ui.components.RuntimeOutlinedButton as OutlinedButton
import top.wkbin.taixu.ui.components.RuntimeRadioButton as RadioButton
import top.wkbin.taixu.ui.components.RuntimeSettingsScaffold
import top.wkbin.taixu.ui.components.RuntimeTextButton as TextButton
import top.wkbin.taixu.ui.components.StatusBadge
import top.wkbin.taixu.ui.components.distroIconFor
import top.wkbin.taixu.ui.components.previewOrNull
import top.wkbin.taixu.ui.components.previewStateOf
import top.wkbin.taixu.ui.miuix.*
import top.wkbin.taixu.ui.settings.LocalizedText as Text
import top.yukonga.miuix.kmp.basic.SnackbarHost
import top.yukonga.miuix.kmp.basic.SnackbarHostState
/**
 * 太墟 · Linux 发行版与沙箱多实例管理 (Distro Management Hub)
 */
@Composable
fun DistroManagementScreen(
    onBack: () -> Unit,
    onOpenRemoteMetrics: (serverId: String) -> Unit = {},
    viewModel: SettingsViewModel? = previewOrNull { koinViewModel() },
) {
    val installedDistros by viewModel?.installedDistros?.collectAsStateWithLifecycle() ?: previewStateOf(emptyList())
    val remoteViewModel = previewOrNull { koinViewModel<RemoteServerViewModel>() }
    val activeServerId by (remoteViewModel?.activeId?.collectAsStateWithLifecycle() ?: previewStateOf<String?>(null))
    val activeDistroId by viewModel?.activeDistroId?.collectAsStateWithLifecycle() ?: previewStateOf("ubuntu")
    val runtimeState by viewModel?.runtimeState?.collectAsStateWithLifecycle() ?: previewStateOf(RuntimeState.Ready)
    val restoringDistroId by viewModel?.restoringDistroId?.collectAsStateWithLifecycle() ?: previewStateOf(null)
    val deletingDistroId by viewModel?.deletingDistroId?.collectAsStateWithLifecycle() ?: previewStateOf(null)

    // 安装进度保存在 VM（StateFlow），旋转后进度界面可恢复，安装在后台继续进行
    val installState by viewModel?.distroInstallState?.collectAsStateWithLifecycle() ?: previewStateOf(SettingsViewModel.DistroInstallUiState())
    var showInstallDialog by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }
    var distroToReset by remember { mutableStateOf<InstalledDistro?>(null) }
    var distroToUninstall by remember { mutableStateOf<InstalledDistro?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }

    val scope = rememberCoroutineScope()

    // 安装成功（isInstalling 从 true -> false 且无错误）后自动关闭弹窗
    var wasInstalling by remember { mutableStateOf(false) }
    LaunchedEffect(installState.isInstalling) {
        if (wasInstalling && !installState.isInstalling && installState.errorMessage == null) {
            showInstallDialog = false
        }
        wasInstalling = installState.isInstalling
    }

    RuntimeSettingsScaffold(
        title = "Linux 系统管理", onBack = onBack,
        statusText = "多系统并存与动态切换",
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) {
        item {
            SystemEnvironmentPicker(
                localCount = installedDistros.size,
                onInstallLocal = { showInstallDialog = true },
                onOpenRemoteMetrics = onOpenRemoteMetrics,
                viewModel = remoteViewModel,
            )
        }

        item { Text("本地 (${installedDistros.size})", style = RuntimeTheme.typography.labelLarge, color = RuntimeTheme.colorScheme.primary) }

        items(installedDistros, key = { it.id }) { distro ->
            DistroItemCard(
                distro = distro,
                isActive = activeServerId == null && distro.id.equals(activeDistroId, ignoreCase = true),
                isRestoring = distro.id.equals(restoringDistroId, ignoreCase = true),
                isDeleting = distro.id.equals(deletingDistroId, ignoreCase = true),
                canOperateMulti = installedDistros.size > 1 || activeServerId != null,
                onSwitchActive = {
                    remoteViewModel?.select(null)
                    viewModel?.switchActiveDistro(distro.id)
                },
                onReset = {
                    distroToReset = distro
                },
                onUninstall = {
                    distroToUninstall = distro
                },
            )
        }

        item {
            Spacer(modifier = Modifier.height(32.dp))
        }
    }

    // 安装新系统对话框（旋转后只要安装仍在进行就会重新显示进度）
    if (showInstallDialog || installState.isInstalling) {
        InstallDistroDialog(
            installedIds = installedDistros.map { it.id }.toSet(),
            isInstalling = installState.isInstalling,
            progressText = installState.progressText,
            progressFraction = installState.progressFraction,
            errorMessage = installState.errorMessage,
            onDismiss = {
                if (!installState.isInstalling) {
                    showInstallDialog = false
                    viewModel?.clearDistroInstallError()
                }
            },
            onConfirmInstall = { spec, route ->
                viewModel?.installDistro(
                    request = RuntimeInstallRequest(
                        distributionId = spec.id,
                        registryRoute = route,
                    ),
                )
            },
        )
    }

    // 重置沙箱确认对话框（带 3 秒倒计时锁）
    distroToReset?.let { target ->
        ResetDistroConfirmDialog(
            distro = target,
            onConfirm = {
                val toReset = target.id
                distroToReset = null
                viewModel?.resetDistro(toReset) { success, msg ->
                    scope.launch {
                        snackbarHostState.showSnackbar(msg)
                    }
                }
            },
            onDismiss = { distroToReset = null },
        )
    }

    // 删除系统确认对话框（带 3 秒倒计时锁）
    distroToUninstall?.let { target ->
        DeleteDistroConfirmDialog(
            distro = target,
            onConfirm = {
                val toDelete = target.id
                distroToUninstall = null
                viewModel?.uninstallDistro(toDelete) { success, msg ->
                    scope.launch {
                        snackbarHostState.showSnackbar(msg)
                    }
                }
            },
            onDismiss = { distroToUninstall = null },
        )
    }
}

@Composable
private fun ResetDistroConfirmDialog(
    distro: InstalledDistro,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    var countdown by remember { mutableStateOf(3) }

    LaunchedEffect(distro.id) {
        while (countdown > 0) {
            delay(1000)
            countdown--
        }
    }

    RuntimeAlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "重置 ${distro.displayName} 沙箱？",
                style = RuntimeTheme.typography.titleMedium,
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "• 将清除自行安装的软件包与系统修改",
                    style = RuntimeTheme.typography.bodyMedium,
                    color = RuntimeTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = "• 不会删除 /workspace 中的项目代码",
                    style = RuntimeTheme.typography.bodyMedium,
                    color = RuntimeTheme.colorScheme.primary,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                enabled = countdown == 0,
                colors = ButtonDefaults.buttonColors(
                    containerColor = RuntimeTheme.colorScheme.error,
                    disabledContainerColor = RuntimeTheme.colorScheme.error.copy(alpha = 0.3f),
                ),
            ) {
                Text(
                    text = if (countdown > 0) "确认重置 (${countdown}s)" else "确认重置",
                    color = if (countdown == 0) RuntimeTheme.colorScheme.onError else RuntimeTheme.colorScheme.onError.copy(alpha = 0.6f),
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = "取消")
            }
        },
    )
}

@Composable
private fun DeleteDistroConfirmDialog(
    distro: InstalledDistro,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    var countdown by remember { mutableStateOf(3) }

    LaunchedEffect(distro.id) {
        while (countdown > 0) {
            delay(1000)
            countdown--
        }
    }

    RuntimeAlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "删除 ${distro.displayName} 系统？",
                style = RuntimeTheme.typography.titleMedium,
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "• 将彻底清除该 Linux 发行版与已安装软件",
                    style = RuntimeTheme.typography.bodyMedium,
                    color = RuntimeTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = "• 不会删除 /workspace 中的项目代码",
                    style = RuntimeTheme.typography.bodyMedium,
                    color = RuntimeTheme.colorScheme.primary,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                enabled = countdown == 0,
                colors = ButtonDefaults.buttonColors(
                    containerColor = RuntimeTheme.colorScheme.error,
                    disabledContainerColor = RuntimeTheme.colorScheme.error.copy(alpha = 0.3f),
                ),
            ) {
                Text(
                    text = if (countdown > 0) "确认删除 (${countdown}s)" else "确认删除",
                    color = if (countdown == 0) RuntimeTheme.colorScheme.onError else RuntimeTheme.colorScheme.onError.copy(alpha = 0.6f),
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = "取消")
            }
        },
    )
}

@Composable
private fun DistroItemCard(
    distro: InstalledDistro,
    isActive: Boolean,
    isRestoring: Boolean,
    isDeleting: Boolean,
    canOperateMulti: Boolean,
    onSwitchActive: () -> Unit,
    onReset: () -> Unit,
    onUninstall: () -> Unit,
) {
    SettingsDetailCard(
        borderColor = if (isActive) RuntimeTheme.colorScheme.primary else Color.Transparent,
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f),
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RuntimeTheme.shapes.small)
                            .background(
                                if (isActive) RuntimeTheme.colorScheme.primaryContainer
                                else RuntimeTheme.colorScheme.surfaceVariant,
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        RuntimeIcon(
                            name = distroIconFor(distro.id),
                            modifier = Modifier.size(24.dp),
                        )
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = distro.displayName,
                            style = RuntimeTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = "标识: ${distro.id} · 包管理: ${distro.packageManager} · 占用: ${formatSize(distro.sizeBytes)}",
                            style = RuntimeTheme.typography.bodySmall,
                            color = RuntimeTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }

                if (isActive) {
                    StatusBadge(text = "主系统", color = successStatusColor())
                }
            }

            HorizontalDivider(
                color = RuntimeTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
            )

            RuntimeActionRow(modifier = Modifier.fillMaxWidth()) {
                // 按钮 1: 设为主系统 (当前为主系统或仅有 1 个系统时置灰禁用)
                OutlinedButton(
                    onClick = onSwitchActive,
                    enabled = !isActive && canOperateMulti && !isRestoring && !isDeleting,
                    shape = RuntimeTheme.shapes.medium,
                    modifier = Modifier,
                ) {
                    Text(
                        text = "设为主系统",
                        maxLines = 1,
                    )
                }

                // 按钮 2: 重置沙箱 (执行中显示正在还原...并锁死)
                OutlinedButton(
                    onClick = onReset,
                    enabled = !isRestoring && !isDeleting,
                    shape = RuntimeTheme.shapes.medium,
                    modifier = Modifier,
                ) {
                    if (isRestoring) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(12.dp),
                            strokeWidth = 1.5.dp,
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(text = "正在还原...", maxLines = 1)
                    } else {
                        Text(text = "重置沙箱", maxLines = 1)
                    }
                }

                // 按钮 3: 删除系统 (仅有 1 个系统时置灰禁用，执行中显示正在删除...并锁死)
                OutlinedButton(
                    onClick = onUninstall,
                    enabled = canOperateMulti && !isRestoring && !isDeleting,
                    shape = RuntimeTheme.shapes.medium,
                    border = BorderStroke(
                        1.dp,
                        if (canOperateMulti && !isRestoring && !isDeleting) RuntimeTheme.colorScheme.error.copy(alpha = 0.5f)
                        else RuntimeTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
                    ),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = RuntimeTheme.colorScheme.error,
                        disabledContentColor = RuntimeTheme.colorScheme.error.copy(alpha = 0.38f),
                    ),
                    modifier = Modifier,
                ) {
                    if (isDeleting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(12.dp),
                            strokeWidth = 1.5.dp,
                            color = RuntimeTheme.colorScheme.error,
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(text = "正在删除...", maxLines = 1)
                    } else {
                        Text(
                            text = "删除系统",
                            maxLines = 1,
                        )
                    }
                }
            }

            // 单系统时「设为主系统」按钮会置灰，补充解释避免用户困惑
            if (!isActive && !canOperateMulti) {
                Text(
                    text = "当前仅有一套系统，无需切换主系统",
                    style = RuntimeTheme.typography.labelSmall,
                    color = RuntimeTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun InstallDistroDialog(
    installedIds: Set<String>,
    isInstalling: Boolean,
    progressText: String?,
    progressFraction: Float,
    errorMessage: String?,
    onDismiss: () -> Unit,
    onConfirmInstall: (DistributionSpec, RegistryRoute) -> Unit,
) {
    val availableDistros = remember {
        DistributionCatalog.supported.filter { it.id !in installedIds }
    }
    var selectedSpec by remember {
        mutableStateOf(availableDistros.firstOrNull() ?: DistributionCatalog.supported.first())
    }
    var selectedRoute by remember { mutableStateOf(RegistryRoute.AUTO) }

    RuntimeAlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (isInstalling) "正在安装 Linux 沙箱..." else "安装新 Linux 发行版",
                style = RuntimeTheme.typography.titleLarge.copy(fontWeight = FontWeight.Medium),
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (isInstalling) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        LinearProgressIndicator(
                            progress = { progressFraction.coerceIn(0f, 1f) },
                            modifier = Modifier.fillMaxWidth().height(8.dp).clip(RuntimeTheme.shapes.tiny),
                        )
                        Text(
                            text = progressText ?: "正在下载并配置 RootFS...",
                            style = RuntimeTheme.typography.bodySmall,
                            color = RuntimeTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else {
                    if (availableDistros.isEmpty()) {
                        Text(text = "所有预置 Linux 发行版均已安装完毕！")
                    } else {
                        Text(
                            text = "选择要下载并安装的 Linux 系统：",
                            style = RuntimeTheme.typography.labelMedium,
                            color = RuntimeTheme.colorScheme.onSurfaceVariant,
                        )

                        availableDistros.forEach { spec ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RuntimeTheme.shapes.chip)
                                    .clickable { selectedSpec = spec }
                                    .padding(vertical = 6.dp, horizontal = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                RadioButton(
                                    selected = selectedSpec.id == spec.id,
                                    onClick = { selectedSpec = spec },
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                RuntimeIcon(
                                    name = distroIconFor(spec.id),
                                    modifier = Modifier.size(22.dp),
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text(
                                        text = spec.displayWithVersion,
                                        style = RuntimeTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                                    )
                                    Text(
                                        text = spec.imageReference,
                                        style = RuntimeTheme.typography.bodySmall,
                                        color = RuntimeTheme.colorScheme.onSurfaceVariant,
                                        fontFamily = FontFamily.Monospace,
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "下载线路与镜像加速：",
                            style = RuntimeTheme.typography.labelMedium,
                            color = RuntimeTheme.colorScheme.onSurfaceVariant,
                        )

                        RuntimeActionRow(modifier = Modifier.fillMaxWidth()) {
                            OutlinedButton(
                                onClick = { selectedRoute = RegistryRoute.AUTO },
                                modifier = Modifier,
                                shape = RuntimeTheme.shapes.medium,
                                colors = if (selectedRoute == RegistryRoute.AUTO) {
                                    ButtonDefaults.outlinedButtonColors(containerColor = RuntimeTheme.colorScheme.primaryContainer)
                                } else ButtonDefaults.outlinedButtonColors(),
                            ) {
                                Text("自动加速")
                            }
                            OutlinedButton(
                                onClick = { selectedRoute = RegistryRoute.CHINA_ACCELERATED },
                                modifier = Modifier,
                                shape = RuntimeTheme.shapes.medium,
                                colors = if (selectedRoute == RegistryRoute.CHINA_ACCELERATED) {
                                    ButtonDefaults.outlinedButtonColors(containerColor = RuntimeTheme.colorScheme.primaryContainer)
                                } else ButtonDefaults.outlinedButtonColors(),
                            ) {
                                Text("国内镜像")
                            }
                        }
                    }

                    errorMessage?.let { error ->
                        Text(
                            text = error,
                            style = RuntimeTheme.typography.bodySmall,
                            color = RuntimeTheme.colorScheme.error,
                        )
                    }
                }
            }
        },
        confirmButton = {
            if (!isInstalling && availableDistros.isNotEmpty()) {
                TextButton(
                    onClick = { onConfirmInstall(selectedSpec, selectedRoute) },
                    shape = RuntimeTheme.shapes.medium,
                ) {
                    Text(text = "开始安装")
                }
            }
        },
        dismissButton = {
            if (!isInstalling) {
                TextButton(onClick = onDismiss) {
                    Text(text = "取消")
                }
            }
        },
    )
}

private fun formatSize(bytes: Long): String {
    if (bytes <= 0L) return "0 MB"
    val mb = bytes.toDouble() / (1024 * 1024)
    return if (mb >= 1024) {
        String.format("%.2f GB", mb / 1024)
    } else {
        String.format("%.1f MB", mb)
    }
}
