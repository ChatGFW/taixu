package top.wkbin.taixu

import android.content.Intent
import android.os.Build
import android.content.pm.PackageManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import kotlinx.coroutines.launch
import top.wkbin.taixu.core.model.AppUpdateInfo
import top.wkbin.taixu.core.network.AppUpdateManager
import top.wkbin.taixu.ui.components.MarkdownText
import top.wkbin.taixu.ui.components.RuntimeAlertDialog
import top.wkbin.taixu.ui.components.RuntimeButton as Button
import top.wkbin.taixu.ui.components.RuntimeIcon
import top.wkbin.taixu.ui.components.RuntimeIconName
import top.wkbin.taixu.ui.components.RuntimeLinearProgressIndicator as LinearProgressIndicator
import top.wkbin.taixu.ui.components.RuntimeTextButton as TextButton
import top.wkbin.taixu.ui.miuix.RuntimeTheme
import top.wkbin.taixu.ui.miuix.Surface
import top.yukonga.miuix.kmp.basic.Text

/** 启动后可选的应用内更新提示；从 [MainActivity] 拆出以满足 maxFileLines。 */
@Composable
internal fun AppUpdatePrompt(
    updateInfo: AppUpdateInfo?,
    onDismiss: () -> Unit,
    appUpdateManager: AppUpdateManager,
    startActivity: (Intent) -> Unit,
) {
    val info = updateInfo ?: return
    var downloadProgress by remember { mutableStateOf<Float?>(null) }
    var isDownloading by remember { mutableStateOf(false) }
    var downloadFailed by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    RuntimeAlertDialog(
        onDismissRequest = { if (!isDownloading) onDismiss() },
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                RuntimeIcon(
                    name = RuntimeIconName.Refresh,
                    modifier = Modifier.size(24.dp),
                    tint = RuntimeTheme.colorScheme.primary,
                )
                Text(stringResource(R.string.taixu_update_available, info.latestVersion), fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Surface(
                    color = RuntimeTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                    shape = RuntimeTheme.shapes.chip,
                ) {
                    Text(
                        text = stringResource(R.string.taixu_update_versions, info.currentVersion, info.latestVersion),
                        style = RuntimeTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = RuntimeTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    )
                }
                if (info.releaseNotes.isNotBlank()) {
                    Surface(
                        color = RuntimeTheme.colorScheme.surfaceContainerHigh,
                        shape = RuntimeTheme.shapes.small,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        MarkdownText(
                            markdown = info.releaseNotes,
                            modifier = Modifier.padding(12.dp),
                        )
                    }
                }
                if (isDownloading) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(stringResource(R.string.taixu_update_downloading_package), style = RuntimeTheme.typography.labelMedium)
                        if (downloadProgress != null) {
                            LinearProgressIndicator(
                                progress = { downloadProgress ?: 0f },
                                modifier = Modifier.fillMaxWidth(),
                                wavy = true,
                            )
                        } else {
                            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        }
                    }
                }
                if (downloadFailed) {
                    Text(
                        text = stringResource(R.string.taixu_update_download_failed),
                        style = RuntimeTheme.typography.labelMedium,
                        color = RuntimeTheme.colorScheme.error,
                    )
                }
            }
        },
        confirmButton = {
            val apkUrl = info.apkDownloadUrl
            if (apkUrl != null) {
                Button(
                    onClick = {
                        isDownloading = true
                        downloadProgress = 0f
                        downloadFailed = false
                        scope.launch {
                            val res = appUpdateManager.downloadApk(apkUrl) { dl, tot ->
                                if (tot != null && tot > 0) downloadProgress = dl.toFloat() / tot.toFloat()
                            }
                            isDownloading = false
                            res.onSuccess { apkFile ->
                                downloadProgress = 1f
                                appUpdateManager.installApk(apkFile)
                                onDismiss()
                            }.onFailure {
                                downloadFailed = true
                            }
                        }
                    },
                    enabled = !isDownloading,
                ) {
                    Text(stringResource(if (isDownloading) R.string.taixu_downloading else R.string.taixu_update_now))
                }
            } else {
                Button(
                    onClick = {
                        runCatching {
                            startActivity(Intent(Intent.ACTION_VIEW, info.releaseUrl.toUri()))
                        }
                        onDismiss()
                    },
                ) {
                    Text(stringResource(R.string.taixu_open_github))
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isDownloading) {
                Text(stringResource(R.string.taixu_later))
            }
        },
    )
}

@Composable
internal fun rememberAppVersionName(): String {
    val context = LocalContext.current
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
        } ?: "0.0.0"
    }
}
