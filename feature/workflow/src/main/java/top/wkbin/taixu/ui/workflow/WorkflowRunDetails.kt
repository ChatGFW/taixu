package top.wkbin.taixu.ui.workflow
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import top.wkbin.taixu.ui.miuix.*
import top.yukonga.miuix.kmp.basic.Text
import top.wkbin.taixu.ui.components.RuntimeFilledTextField
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import top.wkbin.taixu.core.database.AiModelEntity
import top.wkbin.taixu.core.model.workflow.*
import top.wkbin.taixu.ui.components.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.compose.foundation.shape.CircleShape

@Composable
internal fun WorkflowElapsed(state: WorkflowRuntimeState) {
    var now by remember(state.executionId) { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(state.executionId, state.finishedAt) {
        while (state.finishedAt == null) { now = System.currentTimeMillis(); delay(1000) }
    }
    val elapsed = ((state.finishedAt ?: now) - (state.startedAt ?: now)).coerceAtLeast(0) / 1000
    Text("运行耗时 ${elapsed / 60} 分 ${elapsed % 60} 秒", style = RuntimeTheme.typography.labelSmall)
}

@Composable
internal fun WorkflowStartDialog(
    definition: WorkflowDefinition,
    supplied: Map<String, String>,
    models: List<AiModelEntity>,
    availableApks: List<DiscoveredApk> = emptyList(),
    onRefreshApks: (() -> Unit)? = null,
    onDismiss: () -> Unit,
    onStart: (Map<String, String>) -> Unit,
) {
    val required = remember(definition) {
        definition.nodes.filter { it.type == WorkflowNodeType.TRIGGER }
            .flatMap { it.config["requiredVariables"].orEmpty().split(',') }
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
    }
    val editableDefaults = remember(definition) {
        definition.defaultVariables.keys.filterNot { it in required || it.startsWith("WORKFLOW_MODEL") }.sorted()
    }
    val needsModel = remember(definition) {
        definition.nodes.any {
            it.type == WorkflowNodeType.AGENT_INFERENCE ||
                it.type == WorkflowNodeType.SUBAGENT_DELEGATE ||
                (it.type == WorkflowNodeType.HOST_ACTION && it.config["action"] == "gui_pilot")
        }
    }
    val values = remember(definition.id, supplied) {
        mutableStateMapOf<String, String>().apply { putAll(definition.defaultVariables + supplied) }
    }

    val isApkWorkflow = "APK_PATH" in required
    LaunchedEffect(definition.id, availableApks) {
        if (isApkWorkflow && values["APK_PATH"].isNullOrBlank() && availableApks.isNotEmpty()) {
            values["APK_PATH"] = availableApks.first().sandboxPath
        }
    }

    val active = models.firstOrNull { it.isActive }
    var selectedModelId by remember(definition.id, models) {
        mutableStateOf(
            values["WORKFLOW_MODEL_ID"]
                ?.takeIf { id -> models.any { it.id == id } }
                ?: active?.id
                ?: models.firstOrNull()?.id.orEmpty(),
        )
    }
    val selectedProfile = models.firstOrNull { it.id == selectedModelId }
    val variants = remember(selectedProfile?.model) {
        selectedProfile?.model.orEmpty().split(',').map { it.trim() }.filter { it.isNotEmpty() }.distinct()
    }
    var selectedVariant by remember(selectedModelId, variants) {
        mutableStateOf(
            values["WORKFLOW_MODEL_VARIANT"]?.takeIf { it in variants }
                ?: variants.firstOrNull().orEmpty(),
        )
    }

    val canStart = required.all { !values[it].isNullOrBlank() } &&
        (!needsModel || (selectedModelId.isNotBlank() && models.any { it.id == selectedModelId }))

    RuntimeAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("运行 ${definition.name}") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 480.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(definition.description)
                if (needsModel) {
                    Text("执行模型（点选即可，避免 403 请换可用账号）", style = RuntimeTheme.typography.labelLarge)
                    if (models.isEmpty()) {
                        Text(
                            "尚未配置可用模型。请先到设置 → 模型管理添加账号，再回来运行含智能体节点的工作流。",
                            color = RuntimeTheme.colorScheme.error,
                            style = RuntimeTheme.typography.bodySmall,
                        )
                    } else {
                        models.forEach { model ->
                            val selected = model.id == selectedModelId
                            Surface(
                                onClick = {
                                    selectedModelId = model.id
                                    val nextVariants = model.model.split(',')
                                        .map { it.trim() }
                                        .filter { it.isNotEmpty() }
                                    selectedVariant = nextVariants.firstOrNull().orEmpty()
                                },
                                shape = RuntimeTheme.shapes.small,
                                color = if (selected) {
                                    RuntimeTheme.colorScheme.primaryContainer
                                } else {
                                    RuntimeTheme.colorScheme.surfaceContainerHigh
                                },
                                border = BorderStroke(
                                    width = 1.dp,
                                    color = if (selected) {
                                        RuntimeTheme.colorScheme.primary
                                    } else {
                                        RuntimeTheme.colorScheme.outlineVariant
                                    },
                                ),
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
                                    Text(
                                        text = buildString {
                                            append(if (selected) "✓ " else "")
                                            append(model.name)
                                            if (model.isActive) append("（当前默认）")
                                        },
                                        style = RuntimeTheme.typography.bodyMedium,
                                        fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
                                    )
                                    Text(
                                        text = "${model.provider} · ${model.model.substringBefore(',').ifBlank { model.model }}",
                                        style = RuntimeTheme.typography.labelSmall,
                                        color = RuntimeTheme.colorScheme.onSurfaceVariant,
                                    )
                                    if (model.pureChatMode) {
                                        Text(
                                            "纯聊天模式：可能无法调用 host 工具",
                                            style = RuntimeTheme.typography.labelSmall,
                                            color = RuntimeTheme.colorScheme.error,
                                        )
                                    }
                                }
                            }
                        }
                        if (variants.size > 1) {
                            Text("模型变体", style = RuntimeTheme.typography.labelMedium)
                            variants.forEach { variant ->
                                val selected = variant == selectedVariant
                                Surface(
                                    onClick = { selectedVariant = variant },
                                    shape = CircleShape,
                                    color = if (selected) {
                                        RuntimeTheme.colorScheme.secondaryContainer
                                    } else {
                                        RuntimeTheme.colorScheme.surfaceContainerHighest
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Text(
                                        text = if (selected) "✓ $variant" else variant,
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                        style = RuntimeTheme.typography.bodySmall,
                                    )
                                }
                            }
                        } else if (variants.size == 1) {
                            Text("变体：${variants.first()}", style = RuntimeTheme.typography.bodySmall)
                        }
                        Text(
                            "此处选择只影响本次工作流，不会改全局默认模型。",
                            style = RuntimeTheme.typography.bodySmall,
                            color = RuntimeTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                if (isApkWorkflow) {
                    ApkSelectorSection(
                        selectedPath = values["APK_PATH"].orEmpty(),
                        availableApks = availableApks,
                        onSelectApk = { values["APK_PATH"] = it },
                        onRefresh = onRefreshApks,
                    )
                }
                required.filterNot { it == "APK_PATH" }.forEach { key ->
                    RuntimeFilledTextField(
                        value = values[key].orEmpty(),
                        onValueChange = { values[key] = it },
                        labelText = "$key（必填）",
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                editableDefaults.forEach { key ->
                    RuntimeFilledTextField(
                        value = values[key].orEmpty(),
                        onValueChange = { values[key] = it },
                        labelText = key,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        },
        dismissButton = { RuntimeOutlinedButton(onClick = onDismiss) { Text("取消") } },
        confirmButton = {
            RuntimeButton(
                onClick = {
                    val payload = values.toMutableMap()
                    if (needsModel && selectedModelId.isNotBlank()) {
                        payload["WORKFLOW_MODEL_ID"] = selectedModelId
                        val variant = selectedVariant.ifBlank { variants.firstOrNull().orEmpty() }
                        if (variant.isNotBlank()) payload["WORKFLOW_MODEL_VARIANT"] = variant
                    }
                    onStart(payload)
                },
                enabled = canStart,
            ) { Text("开始执行") }
        },
    )
}

@Composable
private fun ApkSelectorSection(
    selectedPath: String,
    availableApks: List<DiscoveredApk>,
    onSelectApk: (String) -> Unit,
    onRefresh: (() -> Unit)?,
) {
    var showManualInput by remember { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
        ) {
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                RuntimeIcon(RuntimeIconName.Android, Modifier.size(18.dp), tint = RuntimeTheme.colorScheme.primary)
                Spacer(Modifier.width(6.dp))
                Text("选择安装 APK", style = RuntimeTheme.typography.labelLarge, fontWeight = FontWeight.Medium)
            }
            if (onRefresh != null) {
                TextButton(
                    onClick = onRefresh,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                ) {
                    RuntimeIcon(RuntimeIconName.Refresh, Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("重新扫描", style = RuntimeTheme.typography.labelSmall)
                }
            }
        }

        if (availableApks.isNotEmpty()) {
            availableApks.forEachIndexed { index, apk ->
                val isSelected = selectedPath == apk.sandboxPath ||
                    selectedPath == apk.file.absolutePath ||
                    selectedPath == apk.relativePath
                Surface(
                    onClick = { onSelectApk(apk.sandboxPath) },
                    shape = RuntimeTheme.shapes.small,
                    color = if (isSelected) RuntimeTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                        else RuntimeTheme.colorScheme.surfaceContainerHigh,
                    border = BorderStroke(
                        width = if (isSelected) 1.5.dp else 1.dp,
                        color = if (isSelected) RuntimeTheme.colorScheme.primary
                            else RuntimeTheme.colorScheme.outlineVariant,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                        ) {
                            if (isSelected) {
                                RuntimeIcon(RuntimeIconName.Check, Modifier.size(16.dp), tint = RuntimeTheme.colorScheme.primary)
                                Spacer(Modifier.width(6.dp))
                            }
                            Text(
                                text = apk.name,
                                style = RuntimeTheme.typography.bodyMedium,
                                fontWeight = if (isSelected) FontWeight.Medium else FontWeight.Medium,
                                modifier = Modifier.weight(1f),
                                maxLines = 1,
                            )
                            if (index == 0) {
                                Surface(
                                    shape = RuntimeTheme.shapes.tiny,
                                    color = RuntimeTheme.colorScheme.primary,
                                ) {
                                    Text(
                                        text = "✨ 最新生成",
                                        style = RuntimeTheme.typography.labelSmall,
                                        color = RuntimeTheme.colorScheme.onPrimary,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                    )
                                }
                            }
                        }
                        Text(
                            text = apk.relativePath,
                            style = RuntimeTheme.typography.bodySmall,
                            color = RuntimeTheme.colorScheme.onSurfaceVariant,
                            fontFamily = FontFamily.Monospace,
                            maxLines = 2,
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                text = formatApkSize(apk.sizeBytes),
                                style = RuntimeTheme.typography.labelSmall,
                                color = RuntimeTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                text = formatApkTime(apk.lastModified),
                                style = RuntimeTheme.typography.labelSmall,
                                color = RuntimeTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }

            TextButton(
                onClick = { showManualInput = !showManualInput },
                contentPadding = PaddingValues(0.dp),
            ) {
                RuntimeIcon(
                    if (showManualInput) RuntimeIconName.ChevronUp else RuntimeIconName.ChevronDown,
                    Modifier.size(14.dp),
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    if (showManualInput) "收起手动输入" else "手动指定其他 APK 路径...",
                    style = RuntimeTheme.typography.labelSmall,
                )
            }
        } else {
            Surface(
                shape = RuntimeTheme.shapes.small,
                color = RuntimeTheme.colorScheme.surfaceContainerHigh,
                border = BorderStroke(1.dp, RuntimeTheme.colorScheme.outlineVariant),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        RuntimeIcon(RuntimeIconName.Info, Modifier.size(16.dp), tint = RuntimeTheme.colorScheme.primary)
                        Spacer(Modifier.width(6.dp))
                        Text("未在工程中扫描到 APK", style = RuntimeTheme.typography.labelMedium, fontWeight = FontWeight.Medium)
                    }
                    Text(
                        "若您已在终端完成编译构建，可点击右上角「重新扫描」；或在下方直接输入 APK 文件路径。",
                        style = RuntimeTheme.typography.bodySmall,
                        color = RuntimeTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        if (availableApks.isEmpty() || showManualInput) {
            RuntimeFilledTextField(
                value = selectedPath,
                onValueChange = onSelectApk,
                labelText = "APK 路径 (绝对路径或相对路径)",
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
        }
    }
}

private fun formatApkSize(bytes: Long): String = when {
    bytes <= 0L -> "0 B"
    bytes < 1024L -> "$bytes B"
    bytes < 1024L * 1024L -> "%.1f KB".format(bytes / 1024.0)
    else -> "%.1f MB".format(bytes / (1024.0 * 1024.0))
}

private fun formatApkTime(timestamp: Long): String {
    if (timestamp <= 0L) return ""
    val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
    return sdf.format(Date(timestamp))
}
