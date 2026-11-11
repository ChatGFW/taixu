package top.wkbin.taixu.ui.settings

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import top.wkbin.taixu.ui.miuix.ButtonDefaults
import top.wkbin.taixu.ui.miuix.RuntimeTheme
import top.wkbin.taixu.ui.miuix.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import top.wkbin.taixu.core.model.McpUpdateStatus
import top.wkbin.taixu.core.model.PluginBundle
import top.wkbin.taixu.core.model.PluginComponent
import top.wkbin.taixu.feature.settings.R
import top.wkbin.taixu.ui.components.RuntimeAlertDialog
import top.wkbin.taixu.ui.components.RuntimeButton as Button
import top.wkbin.taixu.ui.components.RuntimeCheckbox
import top.wkbin.taixu.ui.components.RuntimeCircularProgressIndicator
import top.wkbin.taixu.ui.components.RuntimeIcon
import top.wkbin.taixu.ui.components.RuntimeIconName
import top.wkbin.taixu.ui.components.RuntimeLinearProgressIndicator as LinearProgressIndicator
import top.wkbin.taixu.ui.components.RuntimeTextButton as TextButton
import top.wkbin.taixu.ui.settings.LocalizedText as Text
import top.wkbin.taixu.ui.components.RuntimeActionRow
/**
 * 套件子组件装配弹窗。已装配的组件可以卸载，或清掉旧文件后重新安装。
 */
@Composable
internal fun BundleComponentSetupDialog(
    bundle: PluginBundle,
    installedComponentIds: Set<String>,
    selectedComponents: Set<String>,
    isInstallingComponents: Boolean,
    componentInstallProgress: String?,
    onToggle: (PluginComponent) -> Unit,
    onInstall: () -> Unit,
    onReinstall: (PluginComponent) -> Unit,
    onUninstall: (PluginComponent) -> Unit,
    uninstallBlockedReason: (PluginComponent) -> String?,
    onDismiss: () -> Unit,
    /** 版本相关参数：只有 MCP 能力仓库会传；其余套件保持默认值即可，更新相关的行整块不渲染。 */
    updateStatuses: Map<String, McpUpdateStatus> = emptyMap(),
    checkingUpdates: Boolean = false,
    upgradingComponentId: String? = null,
    updateNotice: String? = null,
    onCheckUpdates: (() -> Unit)? = null,
    onUpgradeComponent: ((PluginComponent) -> Unit)? = null,
) {
    var pendingUninstallId by rememberSaveable { mutableStateOf<String?>(null) }
    var blockedReason by androidx.compose.runtime.remember { mutableStateOf<String?>(null) }
    val pendingUninstall = bundle.components.firstOrNull { it.id == pendingUninstallId }
    RuntimeAlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                RuntimeIcon(
                    name = bundleIcon(bundle.iconName),
                    modifier = Modifier.size(22.dp),
                    tint = RuntimeTheme.colorScheme.onSurface,
                )
                Text("装配 ${bundle.name}", fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 360.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    bundle.description,
                    style = RuntimeTheme.typography.bodySmall,
                    color = RuntimeTheme.colorScheme.onSurfaceVariant,
                )
                if (isInstallingComponents) {
                    InstallProgress(componentInstallProgress)
                }
                blockedReason?.let { reason ->
                    Text(reason, style = RuntimeTheme.typography.bodySmall, color = RuntimeTheme.colorScheme.error)
                }
                val uninstalled = bundle.components.filter { it.id !in installedComponentIds }
                val installed = bundle.components.filter { it.id in installedComponentIds }
                if (uninstalled.isNotEmpty()) {
                    SectionLabel("待装配组件 (${uninstalled.size})：", RuntimeTheme.colorScheme.primary)
                    uninstalled.forEach { component ->
                        UninstalledComponentRow(
                            component = component,
                            checked = component.isRequired || component.id in selectedComponents,
                            enabled = !isInstallingComponents,
                            onToggle = { onToggle(component) },
                        )
                    }
                }
                if (installed.isNotEmpty()) {
                    BundleSectionHeader(
                        text = "已装配就绪 (${installed.size})：",
                        color = successStatusColor(),
                        checking = checkingUpdates,
                        enabled = !isInstallingComponents,
                        onCheck = onCheckUpdates,
                    )
                    Text(
                        text = stringResource(R.string.settings_component_installed_hint),
                        style = RuntimeTheme.typography.bodySmall,
                        color = RuntimeTheme.colorScheme.onSurfaceVariant,
                    )
                    updateNotice?.let { McpUpdateNoticeBanner(it) }
                    installed.forEach { component ->
                        InstalledComponentRow(
                            component = component,
                            enabled = !isInstallingComponents,
                            onReinstall = { onReinstall(component) },
                            onUninstall = {
                                val reason = uninstallBlockedReason(component)
                                if (reason == null) {
                                    blockedReason = null
                                    pendingUninstallId = component.id
                                } else {
                                    blockedReason = reason
                                }
                            },
                            updateStatus = updateStatuses[component.id],
                            upgrading = upgradingComponentId == component.id,
                            onUpgrade = onUpgradeComponent,
                        )
                    }
                }
            }
        },
        confirmButton = {
            val uninstalled = bundle.components.filter { it.id !in installedComponentIds }
            if (uninstalled.isEmpty()) {
                TextButton(onClick = onDismiss) { Text("完成") }
            } else {
                TextButton(
                    onClick = onInstall,
                    enabled = !isInstallingComponents && selectedComponents.isNotEmpty(),
                ) {
                    Text(if (selectedComponents.isEmpty()) "请勾选待装配组件" else "开始装配 (${selectedComponents.size})")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )
    pendingUninstall?.let { component ->
        RuntimeAlertDialog(
            onDismissRequest = { pendingUninstallId = null },
            title = { Text(stringResource(R.string.settings_component_uninstall_title, component.name), fontWeight = FontWeight.Medium) },
            text = { Text(stringResource(R.string.settings_component_uninstall_body)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        pendingUninstallId = null
                        onUninstall(component)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = RuntimeTheme.colorScheme.error),
                ) {
                    Text(stringResource(R.string.settings_component_confirm_uninstall))
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingUninstallId = null }) { Text("取消") }
            },
        )
    }
}

@Composable
private fun InstallProgress(progress: String?) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RuntimeTheme.shapes.small,
        color = RuntimeTheme.colorScheme.surfaceContainerHighest,
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                RuntimeCircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                Text(progress ?: "正在执行批量原子装配流水线...", style = RuntimeTheme.typography.bodySmall, maxLines = 2)
            }
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth().height(4.dp).clip(RuntimeTheme.shapes.hairline))
        }
    }
}

@Composable
private fun SectionLabel(text: String, color: androidx.compose.ui.graphics.Color) {
    Text(
        text,
        style = RuntimeTheme.typography.labelMedium.copy(fontWeight = FontWeight.Medium),
        color = color,
        modifier = Modifier.padding(top = 4.dp),
    )
}

@Composable
private fun UninstalledComponentRow(
    component: PluginComponent,
    checked: Boolean,
    enabled: Boolean,
    onToggle: () -> Unit,
) {
    val locked = component.isRequired
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RuntimeTheme.shapes.small)
            .clickable(enabled = enabled && !locked, onClick = onToggle),
        shape = RuntimeTheme.shapes.small,
        border = BorderStroke(
            1.dp,
            if (checked) RuntimeTheme.colorScheme.primary else Color.Transparent,
        ),
        color = if (checked) RuntimeTheme.colorScheme.primaryContainer.copy(alpha = 0.22f) else RuntimeTheme.colorScheme.surfaceContainerLow,
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            RuntimeCheckbox(
                checked = checked,
                onCheckedChange = { if (!locked) onToggle() },
                enabled = enabled && !locked,
            )
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(component.name, style = RuntimeTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (locked) {
                        Badge("必选基座", RuntimeTheme.colorScheme.primary, RuntimeTheme.colorScheme.primary.copy(alpha = 0.15f))
                    }
                }
                Text(component.description, style = RuntimeTheme.typography.bodySmall, color = RuntimeTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun InstalledComponentRow(
    component: PluginComponent,
    enabled: Boolean,
    onReinstall: () -> Unit,
    onUninstall: () -> Unit,
    updateStatus: McpUpdateStatus? = null,
    upgrading: Boolean = false,
    onUpgrade: ((PluginComponent) -> Unit)? = null,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RuntimeTheme.shapes.small,
        border = BorderStroke(1.dp, successStatusColor().copy(alpha = 0.25f)),
        color = RuntimeTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.45f),
    ) {
        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(
                    modifier = Modifier.size(20.dp).clip(CircleShape).background(successStatusColor().copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center,
                ) {
                    RuntimeIcon(RuntimeIconName.Check, Modifier.size(12.dp), successStatusColor())
                }
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(component.name, style = RuntimeTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(component.description, style = RuntimeTheme.typography.bodySmall, color = RuntimeTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
            McpComponentUpdateRow(component, updateStatus, upgrading, enabled, onUpgrade)
            RuntimeActionRow(modifier = Modifier.fillMaxWidth()) {
                TextButton(onClick = onReinstall, enabled = enabled) {
                    Text(stringResource(R.string.settings_component_reinstall), maxLines = 1, softWrap = false)
                }
                TextButton(onClick = onUninstall, enabled = enabled) {
                    Text(
                        stringResource(R.string.settings_component_uninstall),
                        color = RuntimeTheme.colorScheme.error,
                        maxLines = 1,
                        softWrap = false,
                    )
                }
            }
        }
    }
}

@Composable
private fun Badge(text: String, contentColor: androidx.compose.ui.graphics.Color, container: androidx.compose.ui.graphics.Color) {
    Surface(shape = RuntimeTheme.shapes.tiny, color = container) {
        Text(
            text,
            style = RuntimeTheme.typography.labelSmall.copy(fontSize = 11.sp),
            color = contentColor,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
            maxLines = 1,
        )
    }
}

/** 开发套件 bundle 的图标名 → RuntimeIconName 映射（工具中心与安装弹窗共用）。 */
internal fun bundleIcon(iconName: String): RuntimeIconName = when (iconName) {
    "Android" -> RuntimeIconName.Android
    "Flutter" -> RuntimeIconName.Flutter
    "Globe" -> RuntimeIconName.Globe
    "Search" -> RuntimeIconName.Search
    "Extension" -> RuntimeIconName.Extension
    else -> RuntimeIconName.Code
}
