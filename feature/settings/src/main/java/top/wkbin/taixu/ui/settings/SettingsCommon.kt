package top.wkbin.taixu.ui.settings

import top.wkbin.taixu.ui.settings.LocalizedText as Text
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import top.wkbin.taixu.ui.miuix.RuntimeTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import top.wkbin.taixu.ui.components.RuntimeAlertDialog
import top.wkbin.taixu.ui.components.RuntimeButton as Button
import top.wkbin.taixu.ui.components.RuntimeCard
import top.wkbin.taixu.ui.components.RuntimeCircularProgressIndicator as CircularProgressIndicator
import top.wkbin.taixu.ui.components.RuntimeIcon
import top.wkbin.taixu.ui.components.RuntimeIconName
import top.wkbin.taixu.ui.components.RuntimeSwitch
import top.wkbin.taixu.ui.components.RuntimeTextButton as TextButton
import top.wkbin.taixu.runtime.webchat.WebChatServerStatus
import top.wkbin.taixu.runtime.remote.LocalNetworkAccess
import top.wkbin.taixu.runtime.remote.needsLocalNetworkPermission
@Composable
fun WebChatBridgeDialog(
    status: WebChatServerStatus,
    toggling: Boolean = false,
    onToggle: (Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val currentOnToggle by rememberUpdatedState(onToggle)
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) currentOnToggle(true)
        else Toast.makeText(context, top.wkbin.taixu.feature.settings.R.string.settings_local_network_permission_required, Toast.LENGTH_LONG).show()
    }
    RuntimeAlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                RuntimeIcon(RuntimeIconName.Globe, Modifier.size(24.dp), tint = RuntimeTheme.colorScheme.onSurface)
                Text("太墟智枢 Web 协作台")
            }
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = "在同一 Wi-Fi / 局域网下，通过电脑浏览器连接太墟智枢，同步处理 Agent 任务、对话与 Linux 工作区文件。",
                    style = RuntimeTheme.typography.bodyMedium,
                )

                RuntimeCard(
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = "协作服务状态",
                                style = RuntimeTheme.typography.titleSmall,
                            )
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                if (toggling) {
                                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                                }
                                RuntimeSwitch(
                                    checked = status.isRunning,
                                    onCheckedChange = { enabled ->
                                        if (enabled && context.needsLocalNetworkPermission()) {
                                            permissionLauncher.launch(LocalNetworkAccess.PERMISSION)
                                        } else currentOnToggle(enabled)
                                    },
                                    enabled = !toggling,
                                )
                            }
                        }

                        if (status.isRunning) {

                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text("浏览器访问地址", style = RuntimeTheme.typography.labelSmall, color = RuntimeTheme.colorScheme.onSurfaceVariant)
                                Row(
                                    Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        text = status.accessUrl,
                                        style = RuntimeTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium, fontFamily = FontFamily.Monospace),
                                        color = RuntimeTheme.colorScheme.primary,
                                    )
                                    TextButton(
                                        onClick = {
                                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                            clipboard.setPrimaryClip(ClipData.newPlainText("太墟智枢协作地址", status.accessUrl))
                                            Toast.makeText(context, "已复制基础链接", Toast.LENGTH_SHORT).show()
                                        }
                                    ) { Text("复制") }
                                }
                            }

                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text("安全配对码 (PIN)", style = RuntimeTheme.typography.labelSmall, color = RuntimeTheme.colorScheme.onSurfaceVariant)
                                Row(
                                    Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        text = status.pinCode,
                                        style = RuntimeTheme.typography.titleMedium.copy(fontWeight = FontWeight.Medium, fontFamily = FontFamily.Monospace),
                                        color = RuntimeTheme.colorScheme.secondary,
                                    )
                                    TextButton(
                                        onClick = {
                                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                            clipboard.setPrimaryClip(ClipData.newPlainText("PIN", status.pinCode))
                                            Toast.makeText(context, "已复制配对码", Toast.LENGTH_SHORT).show()
                                        }
                                    ) { Text("复制") }
                                }
                            }

                            val directUrl = "${status.accessUrl}?token=${status.pinCode}"
                            Button(
                                onClick = {
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    clipboard.setPrimaryClip(ClipData.newPlainText("太墟智枢直连地址", directUrl))
                                    Toast.makeText(context, "已复制免密直达链接，在电脑浏览器打开即可！", Toast.LENGTH_SHORT).show()
                                },
                                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                                shape = RuntimeTheme.shapes.medium,
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    RuntimeIcon(RuntimeIconName.Copy, Modifier.size(16.dp))
                                    Text("一键复制免密直达链接 (免输PIN)")
                                }
                            }

                            Text(
                                text = "请确认电脑与手机同一 Wi-Fi；若手机开着 VPN/代理，请临时关闭或允许局域网，否则可能出现地址能打开但页面无响应。当前在线：${status.activeConnections} 台",
                                style = RuntimeTheme.typography.labelSmall,
                                color = RuntimeTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("完成")
            }
        },
    )
}

/** 应用版本号；三处界面共用一份查询逻辑 */
@Composable
fun rememberAppVersion(): String {
    val context = androidx.compose.ui.platform.LocalContext.current
    return remember {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.getPackageInfo(
                    context.packageName,
                    PackageManager.PackageInfoFlags.of(0),
                ).versionName
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(context.packageName, 0).versionName
            }
        } catch (_: Exception) {
            null
        } ?: "unknown"
    }
}
