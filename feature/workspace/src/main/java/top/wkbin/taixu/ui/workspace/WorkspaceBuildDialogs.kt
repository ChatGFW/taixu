package top.wkbin.taixu.ui.workspace

import androidx.compose.runtime.*

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import top.wkbin.taixu.ui.miuix.RuntimeTheme
import top.wkbin.taixu.ui.miuix.Surface
import top.yukonga.miuix.kmp.basic.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import top.wkbin.taixu.feature.workspace.R
import top.wkbin.taixu.runtime.build.BuildRunProgress
import top.wkbin.taixu.ui.components.RuntimeAlertDialog
import top.wkbin.taixu.ui.components.RuntimeButton as Button
import top.wkbin.taixu.ui.components.RuntimeCircularProgressIndicator
import top.wkbin.taixu.ui.components.RuntimeIcon
import top.wkbin.taixu.ui.components.RuntimeIconName
import top.wkbin.taixu.ui.components.RuntimeLinearProgressIndicator as LinearProgressIndicator
import top.wkbin.taixu.ui.components.RuntimeTextButton as TextButton
import top.wkbin.taixu.ui.components.RuntimeActionRow
/**
 * 后台构建常驻状态栏（列表首项 Banner）：
 * 进行中显示可点击进度条，结束后显示成功/失败与安装、详情操作。
 */
@Composable
internal fun WorkspaceBuildStatusBanner(
    buildProgress: BuildRunProgress?,
    activeBuildingProjectName: String?,
    isBuildDialogVisible: Boolean,
    onShowDialog: () -> Unit,
    onLaunchInstaller: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    if (activeBuildingProjectName != null && !isBuildDialogVisible) {
        Surface(
            color = RuntimeTheme.colorScheme.primaryContainer,
            shape = RuntimeTheme.shapes.small,
            modifier = Modifier.fillMaxWidth().clickable { onShowDialog() },
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                RuntimeCircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.5.dp,
                    color = RuntimeTheme.colorScheme.primary,
                )
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = stringResource(R.string.workspace_building_project, activeBuildingProjectName.orEmpty()),
                        style = RuntimeTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                        color = RuntimeTheme.colorScheme.onPrimaryContainer,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = buildProgress?.step ?: stringResource(R.string.workspace_building),
                        style = RuntimeTheme.typography.labelSmall,
                        color = RuntimeTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                TextButton(onClick = onShowDialog) {
                    Text(stringResource(R.string.workspace_view_logs), fontWeight = FontWeight.Medium)
                }
            }
        }
    } else if (activeBuildingProjectName == null && buildProgress != null && !isBuildDialogVisible) {
        val progress = buildProgress
        Surface(
            color = if (progress.isSuccess == true) RuntimeTheme.colorScheme.secondaryContainer else RuntimeTheme.colorScheme.errorContainer,
            shape = RuntimeTheme.shapes.small,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                RuntimeIcon(
                    if (progress.isSuccess == true) RuntimeIconName.Check else RuntimeIconName.Close,
                    Modifier.size(18.dp),
                    tint = if (progress.isSuccess == true) RuntimeTheme.colorScheme.onSecondaryContainer else RuntimeTheme.colorScheme.onErrorContainer,
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(if (progress.isSuccess == true) R.string.workspace_build_ready else R.string.workspace_build_failed),
                        style = RuntimeTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                        color = if (progress.isSuccess == true) RuntimeTheme.colorScheme.onSecondaryContainer else RuntimeTheme.colorScheme.onErrorContainer,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    progress.message?.let {
                        Text(
                            text = it,
                            style = RuntimeTheme.typography.labelSmall,
                            color = if (progress.isSuccess == true) RuntimeTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f) else RuntimeTheme.colorScheme.onErrorContainer.copy(alpha = 0.8f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                }
                RuntimeActionRow() {
                    if (progress.isSuccess == true && progress.apkPath != null) {
                        TextButton(onClick = { onLaunchInstaller(progress.apkPath!!) }) {
                            Text(stringResource(R.string.workspace_install), fontWeight = FontWeight.Medium)
                        }
                    }
                    TextButton(onClick = onShowDialog) {
                        Text(stringResource(R.string.workspace_details))
                    }
                    val dismissLabel = stringResource(R.string.workspace_cd_dismiss)
                    TextButton(onClick = onDismiss, modifier = Modifier.semantics { contentDescription = dismissLabel }) { Text(dismissLabel) }
                }
            }
        }
    }
}

/**
 * 运行/构建进度与实时日志弹窗（支持后台运行与随时最小化）：
 * 含依赖活动、构建阶段耗时分析与分类日志折叠面板。
 */
@Composable
internal fun BuildProgressDialog(
    progress: BuildRunProgress,
    onOpenToolCenter: () -> Unit,
    onHideDialog: () -> Unit,
    onDismissProgress: () -> Unit,
    onCancelBuild: () -> Unit,
    onLaunchInstaller: (String) -> Unit,
) {
    val context = LocalContext.current
    var showBuildLog by remember { mutableStateOf(false) }
    var showStepAnalysis by remember { mutableStateOf(false) }
    // Category collapse states: dependencies & others collapsed by default; compile & package expanded
    var collapsedDeps by remember { mutableStateOf(true) }
    var collapsedCompile by remember { mutableStateOf(false) }
    var collapsedPackage by remember { mutableStateOf(false) }
    var collapsedOther by remember { mutableStateOf(true) }

    RuntimeAlertDialog(
        onDismissRequest = onHideDialog,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (progress.isRunning) {
                    RuntimeCircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                }
                Text(stringResource(if (progress.isRunning) R.string.workspace_building_device else if (progress.isSuccess == true) R.string.workspace_run_ready else R.string.workspace_run_failed), fontWeight = FontWeight.Medium)
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(progress.step, style = RuntimeTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium))
                if (progress.isRunning) {
                    LinearProgressIndicator(
                        progress = { progress.progress },
                        modifier = Modifier.fillMaxWidth().height(6.dp).clip(RuntimeTheme.shapes.tiny),
                    )
                }
                if (progress.currentDependency != null || progress.dependencyItemsObserved > 0) {
                    Surface(
                        color = RuntimeTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
                        shape = RuntimeTheme.shapes.chip,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = stringResource(R.string.workspace_dependency_activity),
                                    style = RuntimeTheme.typography.labelMedium.copy(fontWeight = FontWeight.Medium),
                                    color = RuntimeTheme.colorScheme.primary,
                                )
                                val countText = progress.dependenciesTotal?.let { total ->
                                    stringResource(R.string.workspace_dependency_count_total, progress.dependencyItemsObserved, total)
                                } ?: stringResource(R.string.workspace_dependency_count, progress.dependencyItemsObserved)
                                Text(countText, style = RuntimeTheme.typography.labelSmall, color = RuntimeTheme.colorScheme.onSurfaceVariant)
                            }
                            progress.currentDependency?.let { dependency ->
                                Text(
                                    text = dependency,
                                    style = RuntimeTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                                    color = RuntimeTheme.colorScheme.onSurface,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            progress.dependencyProgressPercent?.let { percent ->
                                LinearProgressIndicator(
                                    progress = { percent / 100f },
                                    modifier = Modifier.fillMaxWidth().height(4.dp).clip(RuntimeTheme.shapes.hairline),
                                )
                                Text(
                                    text = stringResource(R.string.workspace_dependency_percent, percent),
                                    style = RuntimeTheme.typography.labelSmall,
                                    color = RuntimeTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
                progress.message?.let { msg ->
                    Text(
                        text = msg,
                        style = RuntimeTheme.typography.bodySmall,
                        color = if (progress.isSuccess == false) RuntimeTheme.colorScheme.error else RuntimeTheme.colorScheme.onSurfaceVariant,
                    )
                }

                // ====== 构建阶段耗时分析 ======
                if (progress.stepDurations.isNotEmpty()) {
                    TextButton(
                        onClick = { showStepAnalysis = !showStepAnalysis },
                        contentPadding = PaddingValues(0.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            RuntimeIcon(if (showStepAnalysis) RuntimeIconName.ArrowUp else RuntimeIconName.ChevronDown, Modifier.size(14.dp))
                            Text(stringResource(if (showStepAnalysis) R.string.workspace_hide_timing else R.string.workspace_timing), style = RuntimeTheme.typography.labelMedium)
                        }
                    }

                    if (showStepAnalysis) {
                        Surface(
                            color = RuntimeTheme.colorScheme.surfaceContainerHighest,
                            shape = RuntimeTheme.shapes.chip,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        ) {
                            Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                val maxDuration = (progress.stepDurations.maxOfOrNull { it.durationMs } ?: 1L).coerceAtLeast(1L)
                                progress.stepDurations.forEach { step ->
                                    val barRatio = (step.durationMs.toFloat() / maxDuration.toFloat()).coerceIn(0.02f, 1f)
                                    val barColor = when {
                                        step.step.contains("拉取") || step.step.contains("依赖") -> Color(0xFF2196F3) // 蓝色
                                        step.step.contains("编译") || step.step.contains("Kotlin") || step.step.contains("Java") || step.step.contains("Dex") -> Color(0xFF4CAF50) // 绿色
                                        step.step.contains("打包") || step.step.contains("APK") -> Color(0xFFFF9800) // 橙色
                                        else -> Color(0xFF9E9E9E) // 灰色
                                    }
                                    val durationText = if (step.durationMs >= 1000) "${"%.1f".format(step.durationMs / 1000.0)}s" else "${step.durationMs}ms"
                                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                        ) {
                                            Text(
                                                text = step.step,
                                                style = RuntimeTheme.typography.labelSmall,
                                                color = RuntimeTheme.colorScheme.onSurface,
                                            )
                                            Text(
                                                text = durationText,
                                                style = RuntimeTheme.typography.labelSmall,
                                                color = RuntimeTheme.colorScheme.onSurfaceVariant,
                                            )
                                        }
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(6.dp)
                                                .clip(RuntimeTheme.shapes.tiny)
                                                .background(RuntimeTheme.colorScheme.surfaceVariant),
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxWidth(barRatio)
                                                    .height(6.dp)
                                                    .clip(RuntimeTheme.shapes.tiny)
                                                    .background(barColor),
                                            )
                                        }
                                    }
                                }
                                // 总时长
                                progress.totalDurationMs?.let { total ->
                                    val totalText = if (total >= 1000) "${"%.1f".format(total / 1000.0)}s" else "${total}ms"
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                    ) {
                                        Text(
                                            text = stringResource(R.string.workspace_total),
                                            style = RuntimeTheme.typography.labelMedium.copy(fontWeight = FontWeight.Medium),
                                            color = RuntimeTheme.colorScheme.onSurface,
                                        )
                                        Text(
                                            text = totalText,
                                            style = RuntimeTheme.typography.labelMedium.copy(fontWeight = FontWeight.Medium),
                                            color = RuntimeTheme.colorScheme.primary,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // ====== 构建日志折叠面板 ======
                // The build task survives destination recreation in the
                // coordinator. Its first restored snapshot may not have
                // received log text yet, so keep the entry visible while
                // running (and for completed tasks) instead of tying it
                // to logOutput being non-empty.
                run {
                    RuntimeActionRow(modifier = Modifier.fillMaxWidth()) {
                        TextButton(
                            onClick = { showBuildLog = !showBuildLog },
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                RuntimeIcon(if (showBuildLog) RuntimeIconName.ArrowUp else RuntimeIconName.ChevronDown, Modifier.size(14.dp))
                                Text(stringResource(if (showBuildLog) R.string.workspace_hide_build_log else R.string.workspace_view_build_log))
                            }
                        }

                        if (showBuildLog && progress.logOutput.isNotBlank()) {
                            TextButton(
                                onClick = {
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                                    clipboard?.setPrimaryClip(ClipData.newPlainText("TaiXu Build Log", progress.logOutput))
                                },
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    RuntimeIcon(RuntimeIconName.Copy, Modifier.size(12.dp))
                                    Text(stringResource(R.string.workspace_copy_log))
                                }
                            }
                        }
                    }

                    if (showBuildLog) {
                        if (progress.logOutput.isBlank()) {
                            Text(
                                text = stringResource(if (progress.isRunning) R.string.workspace_build_log_receiving else R.string.workspace_no_build_log),
                                style = RuntimeTheme.typography.bodySmall,
                                color = RuntimeTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        // 分类日志
                        val logLines = progress.logOutput.lines()
                        val depKeywords = listOf("downloading", "fetching", "kilobytes", "megabytes", "get")
                        val compileKeywords = listOf("compile", "kotlin", "javac", "dex")
                        val packageKeywords = listOf("package", "install", "apk")
                        fun classifyLine(line: String): Int {
                            val lower = line.lowercase()
                            return when {
                                depKeywords.any { lower.contains(it) } -> 0
                                compileKeywords.any { lower.contains(it) } -> 1
                                packageKeywords.any { lower.contains(it) } -> 2
                                else -> 3
                            }
                        }
                        val categorized = logLines.map { line -> classifyLine(line) to line }
                        val depsLogs = categorized.filter { it.first == 0 }.map { it.second }
                        val compileLogs = categorized.filter { it.first == 1 }.map { it.second }
                        val packageLogs = categorized.filter { it.first == 2 }.map { it.second }
                        val otherLogs = categorized.filter { it.first == 3 }.map { it.second }

                        data class Category(val type: Int, val emoji: String, val name: String, val logs: List<String>)
                        val categories = listOf(
                            Category(0, "📥", stringResource(R.string.workspace_log_dependencies), depsLogs),
                            Category(1, "🔨", stringResource(R.string.workspace_log_compile), compileLogs),
                            Category(2, "📦", stringResource(R.string.workspace_log_package), packageLogs),
                            Category(3, "📋", stringResource(R.string.workspace_log_other), otherLogs),
                        )

                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            categories.forEach { cat ->
                                if (cat.logs.isNotEmpty()) {
                                    val isCollapsed = when (cat.type) {
                                        0 -> collapsedDeps
                                        1 -> collapsedCompile
                                        2 -> collapsedPackage
                                        else -> collapsedOther
                                    }
                                    TextButton(
                                        onClick = {
                                            when (cat.type) {
                                                0 -> collapsedDeps = !collapsedDeps
                                                1 -> collapsedCompile = !collapsedCompile
                                                2 -> collapsedPackage = !collapsedPackage
                                                else -> collapsedOther = !collapsedOther
                                            }
                                        },
                                        contentPadding = PaddingValues(0.dp),
                                        modifier = Modifier.fillMaxWidth(),
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                                RuntimeIcon(if (isCollapsed) RuntimeIconName.ChevronRight else RuntimeIconName.ArrowUp, Modifier.size(12.dp))
                                                Text("${cat.emoji} ${cat.name}", style = RuntimeTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium))
                                            }
                                            Text(stringResource(R.string.workspace_log_count, cat.logs.size), style = RuntimeTheme.typography.labelSmall, color = RuntimeTheme.colorScheme.onSurfaceVariant)
                                        }
                                    }
                                    if (!isCollapsed) {
                                        Surface(
                                            color = RuntimeTheme.colorScheme.surfaceContainerHighest,
                                            shape = RuntimeTheme.shapes.extraSmall,
                                            modifier = Modifier.fillMaxWidth(),
                                        ) {
                                            Column(modifier = Modifier.padding(6.dp)) {
                                                val displayLogs = if (cat.logs.size > 200) cat.logs.takeLast(200) else cat.logs
                                                displayLogs.forEach { line ->
                                                    Text(
                                                        text = line,
                                                        style = RuntimeTheme.typography.labelSmall.copy(
                                                            fontFamily = FontFamily.Monospace,
                                                            fontSize = 11.sp,
                                                            lineHeight = 14.sp,
                                                        ),
                                                        color = RuntimeTheme.colorScheme.onSurface,
                                                    )
                                                }
                                                if (cat.logs.size > 200) {
                                                    Text(
                                                        text = stringResource(R.string.workspace_log_truncated, cat.logs.size),
                                                        style = RuntimeTheme.typography.labelSmall,
                                                        color = RuntimeTheme.colorScheme.onSurfaceVariant,
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (!progress.isRunning) {
                RuntimeActionRow() {
                    // 缺少环境时展示前往插件中心准备环境按钮
                    if (progress.suggestedSuiteId != null) {
                        Button(
                            onClick = {
                                onDismissProgress()
                                onOpenToolCenter()
                            },
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                RuntimeIcon(RuntimeIconName.Package, Modifier.size(14.dp), tint = RuntimeTheme.colorScheme.onPrimary)
                                Text(stringResource(R.string.workspace_prepare_environment))
                            }
                        }
                    }

                    val path = progress.apkPath
                    if (progress.isSuccess == true && path != null) {
                        Button(onClick = { onLaunchInstaller(path) }) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                RuntimeIcon(RuntimeIconName.Download, Modifier.size(14.dp), tint = RuntimeTheme.colorScheme.onPrimary)
                                Text(stringResource(R.string.workspace_launch_install))
                            }
                        }
                    }
                    TextButton(onClick = onDismissProgress) {
                        Text(stringResource(R.string.workspace_done))
                    }
                }
            } else {
                RuntimeActionRow() {
                    TextButton(onClick = onCancelBuild) {
                        Text(stringResource(R.string.workspace_stop_build), fontWeight = FontWeight.Medium)
                    }
                    TextButton(onClick = onHideDialog) {
                        Text(stringResource(R.string.workspace_background), fontWeight = FontWeight.Medium)
                    }
                }
            }
        },
        dismissButton = {
            if (progress.isRunning) {
                TextButton(onClick = onHideDialog) {
                    Text(stringResource(R.string.workspace_collapse))
                }
            }
        },
    )
}
