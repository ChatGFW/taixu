package top.wkbin.taixu.ui.chat

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.selection.SelectionContainer
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.wkbin.taixu.feature.chat.R
import top.wkbin.taixu.harness.task.DeliveryArtifact
import top.wkbin.taixu.harness.task.DeliveryArtifactKind
import top.wkbin.taixu.runtime.HostFileActions
import top.wkbin.taixu.ui.components.LocalSandboxHostRoots
import top.wkbin.taixu.ui.components.RuntimeIcon
import top.wkbin.taixu.ui.components.RuntimeIconName
import top.wkbin.taixu.ui.miuix.DropdownMenu
import top.wkbin.taixu.ui.miuix.DropdownMenuItem
import top.wkbin.taixu.ui.miuix.RuntimeTheme
import top.wkbin.taixu.ui.theme.semanticColors
import top.yukonga.miuix.kmp.basic.Text

/** 交付产物的当前磁盘状态：只有与记录哈希一致时才敢说「这就是记录的版本」。 */
internal enum class DeliveryIntegrity { UNRESOLVED, MISSING, PENDING, MATCHED, CHANGED }

/**
 * 交付文件行展示路径与变更数，菜单提供完整性提示及打开、分享、另存入口。
 */
@Composable
internal fun DeliveryArtifactRow(
    artifact: DeliveryArtifact,
    workspace: String,
) {
    val context = LocalContext.current
    val hostRoots = LocalSandboxHostRoots.current
    val file = remember(artifact.path, workspace, hostRoots) {
        DeliveryArtifactPaths.hostFile(workspace, artifact.path, hostRoots["workspace"])
    }
    // 指纹随大小/修改时间变化，否则卡片一直停留在旧结论（agent 重写文件后仍显示「一致」）。
    val stamp = file?.let { "${it.length()}|${it.lastModified()}" }
    val existing = remember(file, stamp) { file?.takeIf { it.isFile } }
    var integrity by remember(file, stamp, artifact.sha256) { mutableStateOf(DeliveryIntegrity.PENDING) }

    var menuExpanded by remember(artifact.path) { mutableStateOf(false) }
    LaunchedEffect(file, stamp, artifact.sha256, menuExpanded) {
        integrity = when {
            // 工作区不在宿主内部目录下（如 /sdcard 工程）时不猜路径，也不谎报「文件已丢失」。
            file == null -> DeliveryIntegrity.UNRESOLVED
            existing == null -> DeliveryIntegrity.MISSING
            menuExpanded -> verifyDeliveryFile(existing, artifact.sha256)
            else -> integrity
        }
    }

    val canAct = existing != null
    var preview by remember(artifact.path, workspace) { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    // Every action rechecks disk contents, including after returning from the SAF picker.
    suspend fun checkFile(): Boolean {
        integrity = verifyDeliveryFile(file, artifact.sha256)
        integrityLabel(integrity)?.let { Toast.makeText(context, it, Toast.LENGTH_LONG).show() }
        return integrity == DeliveryIntegrity.MATCHED || integrity == DeliveryIntegrity.CHANGED
    }
    val viewFile: () -> Unit = {
        existing?.let { target ->
            scope.launch {
                if (!checkFile()) return@launch
                when (val result = readDeliveryText(target)) {
                    is DeliveryTextPreview.Text -> preview = result.content
                    else -> {
                        val reason = when (result) {
                            DeliveryTextPreview.TooLarge -> R.string.delivery_preview_too_large
                            DeliveryTextPreview.Binary -> R.string.delivery_preview_binary
                            else -> R.string.delivery_preview_unreadable
                        }
                        Toast.makeText(context, reason, Toast.LENGTH_LONG).show()
                        if (!HostFileActions.open(context, target)) {
                            Toast.makeText(context, R.string.delivery_artifact_open_failed, Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
        }
    }
    preview?.let { content ->
        top.wkbin.taixu.ui.chat.artifact.ArtifactPreviewSheet(
            title = artifact.path.substringAfterLast('/'), content = content,
            relativePath = artifact.path, workspace = workspace, onDismiss = { preview = null },
        )
    }
    val saveMime = remember(existing) { existing?.let { HostFileActions.mimeType(it) } ?: "application/octet-stream" }
    // SAF 另存：流式拷贝，不把 128MB 的包整份读进内存。
    val saveLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(saveMime)) { uri ->
        val target = existing
        if (uri == null || target == null) return@rememberLauncherForActivityResult
        scope.launch {
            if (!checkFile()) return@launch
            val saved = withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openOutputStream(uri)?.use { output ->
                        target.inputStream().use { it.copyTo(output, 64 * 1024) }
                    } ?: error("无法写入目标位置")
                }.isSuccess
            }
            Toast.makeText(
                context,
                if (saved) R.string.delivery_artifact_saved else R.string.delivery_artifact_save_failed,
                Toast.LENGTH_SHORT,
            ).show()
        }
    }
    val launch: (Boolean) -> Unit = { share ->
        val target = existing
        if (target != null) {
            scope.launch {
                if (!checkFile()) return@launch
                val ok = if (share) HostFileActions.share(context, target, subject = target.name)
                    else HostFileActions.open(context, target)
                if (!ok) Toast.makeText(context, R.string.delivery_artifact_open_failed, Toast.LENGTH_SHORT).show()
            }
        }
    }

    val changes = artifact.lineChanges?.takeIf { artifact.kind == DeliveryArtifactKind.FILE }
    val warning = integrityLabel(integrity)
    val openLabel = if (artifact.kind == DeliveryArtifactKind.APK)
        R.string.delivery_artifact_install else R.string.delivery_artifact_open
    val actionsLabel = stringResource(R.string.delivery_artifact_actions)
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (artifact.kind == DeliveryArtifactKind.IMAGE && existing != null) {
            AsyncImage(
                model = existing,
                contentDescription = artifact.path,
                modifier = Modifier
                    .size(18.dp)
                    .clip(RuntimeTheme.shapes.tiny)
                    .clickable(enabled = canAct) { launch(false) },
                contentScale = androidx.compose.ui.layout.ContentScale.Crop,
            )
        } else {
            RuntimeIcon(
                name = when (artifact.kind) {
                    DeliveryArtifactKind.APK -> RuntimeIconName.Package
                    else -> RuntimeIconName.Code
                },
                modifier = Modifier.size(16.dp),
                tint = RuntimeTheme.colorScheme.onSurfaceVariant,
            )
        }
        SelectionContainer(
            Modifier
                .weight(1f)
                .clickable(enabled = canAct) {
                    if (artifact.kind == DeliveryArtifactKind.FILE) viewFile() else launch(false)
                },
        ) {
            Text(
                text = artifact.path,
                style = RuntimeTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (changes != null) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "+${changes.added}",
                    style = RuntimeTheme.typography.labelSmall,
                    color = semanticColors().success,
                    maxLines = 1,
                )
                Text(
                    text = "−${changes.removed}",
                    style = RuntimeTheme.typography.labelSmall,
                    color = RuntimeTheme.colorScheme.error,
                    maxLines = 1,
                )
            }
        } else {
            Text(
                text = stringResource(kindLabel(artifact.kind)),
                style = RuntimeTheme.typography.labelSmall,
                color = RuntimeTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
        if (warning != null) {
            RuntimeIcon(
                RuntimeIconName.Alert,
                modifier = Modifier.size(14.dp),
                tint = RuntimeTheme.colorScheme.error,
            )
        }
        Box {
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(RuntimeTheme.shapes.tiny)
                    .clickable { menuExpanded = true }
                    .semantics { contentDescription = actionsLabel },
                contentAlignment = Alignment.Center,
            ) {
                RuntimeIcon(
                    RuntimeIconName.More,
                    modifier = Modifier.size(14.dp),
                    tint = RuntimeTheme.colorScheme.onSurfaceVariant,
                )
            }
            DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                artifact.origin?.let {
                    Text(
                        text = stringResource(originLabel(it)),
                        style = RuntimeTheme.typography.labelSmall,
                        color = RuntimeTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    )
                }
                warning?.let {
                    Text(
                        text = stringResource(it),
                        style = RuntimeTheme.typography.labelSmall,
                        color = RuntimeTheme.colorScheme.error,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    )
                }
                if (artifact.kind == DeliveryArtifactKind.FILE) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.delivery_artifact_view)) },
                        leadingIcon = { RuntimeIcon(RuntimeIconName.Code, Modifier.size(16.dp)) },
                        enabled = canAct,
                        onClick = { menuExpanded = false; viewFile() },
                    )
                }
                DropdownMenuItem(
                    text = { Text(stringResource(openLabel)) },
                    leadingIcon = { RuntimeIcon(RuntimeIconName.OpenInNew, Modifier.size(16.dp)) },
                    enabled = canAct,
                    onClick = { menuExpanded = false; launch(false) },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.delivery_artifact_share)) },
                    leadingIcon = { RuntimeIcon(RuntimeIconName.Attach, Modifier.size(16.dp)) },
                    enabled = canAct,
                    onClick = { menuExpanded = false; launch(true) },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.delivery_artifact_save)) },
                    leadingIcon = { RuntimeIcon(RuntimeIconName.Download, Modifier.size(16.dp)) },
                    enabled = canAct,
                    onClick = {
                        menuExpanded = false
                        existing?.let { saveLauncher.launch(it.name) }
                    },
                )
            }
        }
    }
}
