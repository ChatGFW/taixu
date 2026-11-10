package top.wkbin.taixu.ui.settings
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.compose.viewmodel.koinViewModel
import top.wkbin.taixu.core.model.ToolState
import top.wkbin.taixu.core.tools.LocalPluginImportState
import top.wkbin.taixu.feature.settings.R
import top.wkbin.taixu.ui.components.NoticeBanner
import top.wkbin.taixu.ui.components.RuntimeAlertDialog
import top.wkbin.taixu.ui.components.RuntimeButton as Button
import top.wkbin.taixu.ui.components.RuntimeCard
import top.wkbin.taixu.ui.components.RuntimeCircularProgressIndicator
import top.wkbin.taixu.ui.components.RuntimeFilledTonalButton as FilledTonalButton
import top.wkbin.taixu.ui.components.RuntimeIcon
import top.wkbin.taixu.ui.components.RuntimeIconButton as IconButton
import top.wkbin.taixu.ui.components.RuntimeIconName
import top.wkbin.taixu.ui.components.RuntimeLinearProgressIndicator as LinearProgressIndicator
import top.wkbin.taixu.ui.components.RuntimePullToRefresh
import top.wkbin.taixu.ui.components.RuntimeTextButton as TextButton
import top.wkbin.taixu.ui.components.RuntimeTopBar
import top.wkbin.taixu.ui.components.previewOrNull
import top.wkbin.taixu.ui.components.previewStateOf
import top.wkbin.taixu.ui.components.runtimeContentPadding
import top.wkbin.taixu.ui.components.runtimeScrollContentPadding
import top.wkbin.taixu.ui.components.runtimeScrollViewport
import top.wkbin.taixu.ui.miuix.*
import top.wkbin.taixu.ui.settings.LocalizedText as Text
/** 太墟 · 插件与 AI 工具中心 */
@Composable
internal fun LocalToolCenterContent(
    onBack: () -> Unit,
    onLaunchPty: (toolId: String) -> Unit,
    onOpenToolDetail: (toolId: String) -> Unit = {},
    onStartAiHealing: (toolId: String, toolName: String, errorLogs: List<String>) -> Unit = { _, _, _ -> },
    viewModel: ToolCenterViewModel? = previewOrNull { koinViewModel() },
    onOpenLibrary: () -> Unit = {}, onOpenBuiltinFeatures: () -> Unit = {}, onOpenWebPlugins: () -> Unit = {},
) {
    val tools by viewModel?.tools?.collectAsStateWithLifecycle() ?: previewStateOf(emptyList())
    val installProgress by viewModel?.installProgress?.collectAsStateWithLifecycle() ?: previewStateOf(emptyMap())
    val verifications by viewModel?.verifications?.collectAsStateWithLifecycle() ?: previewStateOf(emptyMap())
    val selectedCategory by viewModel?.selectedCategory?.collectAsStateWithLifecycle() ?: previewStateOf("INSTALLED")
    val viewingLogsToolId by viewModel?.viewingLogsToolId?.collectAsStateWithLifecycle() ?: previewStateOf(null)
    val toolLogs by viewModel?.toolLogs?.collectAsStateWithLifecycle() ?: previewStateOf(emptyList())
    val installedComponentIds by viewModel?.installedComponentIds?.collectAsStateWithLifecycle() ?: previewStateOf(emptySet())
    val activeBundle by viewModel?.activeBundle?.collectAsStateWithLifecycle() ?: previewStateOf(null)
    val selectedComponents by viewModel?.selectedComponents?.collectAsStateWithLifecycle() ?: previewStateOf(emptySet())
    val isInstallingComponents by viewModel?.isInstallingComponents?.collectAsStateWithLifecycle() ?: previewStateOf(false)
    val componentInstallProgress by viewModel?.componentInstallProgress?.collectAsStateWithLifecycle() ?: previewStateOf(null)
    val componentInstallLog by viewModel?.componentInstallLog?.collectAsStateWithLifecycle() ?: previewStateOf(emptyList())
    val localPluginImport by viewModel?.localPluginImport?.collectAsStateWithLifecycle() ?: previewStateOf(LocalPluginImportState.Idle)
    val isSyncing by viewModel?.isSyncing?.collectAsStateWithLifecycle() ?: previewStateOf(false)
    val operationError by viewModel?.operationError?.collectAsStateWithLifecycle() ?: previewStateOf(null)
    var showBundleInstallLog by remember { mutableStateOf(false) }
    var pendingUninstallId by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingUninstallName by rememberSaveable { mutableStateOf<String?>(null) }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let({ arg0 -> viewModel?.importLocalPlugin(arg0) })
    }
    val categories = listOf(
        "INSTALLED" to "已安装",
        "ALL" to "全部插件",
        "BUNDLES" to "开发套件",
        "CODING_AGENT" to "编程助手",
        "AI_AGENT" to "智能体生态",
    )
    val showBundles = selectedCategory == "ALL" || selectedCategory == "BUNDLES"
    val showTools = selectedCategory != "BUNDLES"
    val filteredTools = PluginCenterCatalogPolicy.visibleTools(tools, selectedCategory)
    Box(modifier = Modifier.fillMaxSize()) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = RuntimeTheme.colorScheme.surface,
        topBar = {
            RuntimeTopBar(
                title = androidx.compose.ui.res.stringResource(R.string.plugin_center_title),
                onBack = onBack,
                scrollBehavior = scrollBehavior,
                actions = {
                    IconButton(
                        onClick = {
                            viewModel?.syncRegistry()
                            viewModel?.refreshInstalledStatus()
                        },
                        enabled = !isSyncing,
                        contentDescription = if (isSyncing) "正在刷新插件目录" else "刷新插件目录",
                    ) {
                        if (isSyncing) {
                            RuntimeCircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                            )
                        } else {
                            RuntimeIcon(
                                name = RuntimeIconName.Refresh,
                                modifier = Modifier.size(20.dp),
                                tint = RuntimeTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                },
            )
        },
    ) { innerPadding ->
        RuntimePullToRefresh(
            isRefreshing = isSyncing,
            onRefresh = { viewModel?.syncRegistry(); viewModel?.refreshInstalledStatus() },
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = innerPadding.calculateTopPadding()),
            topAppBarScrollBehavior = scrollBehavior,
        ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .then(runtimeScrollViewport(innerPadding)),
            contentPadding = runtimeScrollContentPadding(innerPadding, runtimeContentPadding(top = RuntimeSpacing.pageTop, bottom = RuntimeSpacing.pageBottom)),
            verticalArrangement = Arrangement.spacedBy(RuntimeSpacing.cardGap),
        ) {
            item("plugin-actions") {
                PluginCenterActions(onOpenLibrary, { importLauncher.launch(arrayOf("application/zip", "application/octet-stream", "*/*")) },
                    onOpenBuiltinFeatures, onOpenWebPlugins,
                    enabled = localPluginImport !is LocalPluginImportState.Reading && localPluginImport !is LocalPluginImportState.Importing && localPluginImport !is LocalPluginImportState.PendingConfirmation)
            }
            item("plugin-categories") {
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(categories) { (catId, label) ->
                    val isSelected = selectedCategory == catId
                    FilterChip(
                        selected = isSelected,
                        onClick = { viewModel?.setCategory(catId) },
                        label = { Text(label, style = RuntimeTheme.typography.labelLarge) },
                        shape = CircleShape,
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = RuntimeTheme.colorScheme.primaryContainer,
                            selectedLabelColor = RuntimeTheme.colorScheme.onPrimaryContainer,
                        ),
                    )
                }
            }
            }
                item(key = "tool_catalog_summary") {
                    Text("已集成 ${tools.count { it.state == ToolState.INSTALLED.name }} 个已就绪工具",
                        style = RuntimeTheme.typography.bodyMedium, color = RuntimeTheme.colorScheme.onSurfaceVariant)
                }
                if (operationError != null) {
                    item(key = "tool_center_operation_error") {
                        NoticeBanner(
                            text = operationError.orEmpty(),
                            isError = true,
                            onDismiss = { viewModel?.consumeOperationError() },
                        )
                    }
                }
                if (localPluginImport !is LocalPluginImportState.Idle) {
                    item {
                        val state = localPluginImport
                        val isImporting = state is LocalPluginImportState.Reading ||
                            state is LocalPluginImportState.Importing
                        val isError = state is LocalPluginImportState.Failed
                        val isPending = state is LocalPluginImportState.PendingConfirmation
                        val containerColor = when {
                            isError -> RuntimeTheme.colorScheme.errorContainer.copy(alpha = 0.55f)
                            isImporting -> RuntimeTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                            else -> RuntimeTheme.colorScheme.tertiaryContainer.copy(alpha = 0.55f)
                        }
                        val contentColor = when {
                            isError -> RuntimeTheme.colorScheme.onErrorContainer
                            isImporting -> RuntimeTheme.colorScheme.onPrimaryContainer
                            else -> RuntimeTheme.colorScheme.onTertiaryContainer
                        }
                        RuntimeCard(
                            modifier = Modifier.fillMaxWidth(),
                            containerColor = containerColor,
                            contentPadding = PaddingValues(16.dp),
                        ) {
                            Column(
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                ) {
                                    if (isImporting) {
                                        RuntimeCircularProgressIndicator(
                                            modifier = Modifier.size(20.dp),
                                            strokeWidth = 2.dp,
                                            color = contentColor,
                                        )
                                    } else {
                                        RuntimeIcon(
                                            name = if (isError) RuntimeIconName.Alert else RuntimeIconName.Check,
                                            modifier = Modifier.size(20.dp),
                                            tint = contentColor,
                                        )
                                    }
                                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                        Text(
                                            text = when (state) {
                                                is LocalPluginImportState.Reading -> "正在读取本地插件信息"
                                                is LocalPluginImportState.PendingConfirmation -> "等待确认安装本地插件"
                                                is LocalPluginImportState.Importing -> "正在安装本地插件"
                                                is LocalPluginImportState.Succeeded -> "本地插件安装完成"
                                                is LocalPluginImportState.AlreadyImported -> "插件已导入"
                                                is LocalPluginImportState.Failed -> "本地插件安装失败"
                                                LocalPluginImportState.Idle -> ""
                                            },
                                            style = RuntimeTheme.typography.titleSmall.copy(fontWeight = FontWeight.Medium),
                                            color = contentColor,
                                        )
                                        Text(
                                            text = when (state) {
                                                is LocalPluginImportState.Reading -> "${state.fileName}：正在读取 manifest.json，尚未复制或解压插件资源"
                                                is LocalPluginImportState.PendingConfirmation -> "${state.fileName} 已读取清单，请在弹窗中确认安装"
                                                is LocalPluginImportState.Importing -> {
                                                    val percent = state.progress?.let { " ${(it * 100).toInt()}%" }.orEmpty()
                                                    "${state.fileName}$percent · ${state.currentEntry ?: "正在解包插件资源"}，大型 ARM64 插件可能需要几分钟，请保持应用打开"
                                                }
                                                is LocalPluginImportState.Succeeded -> "${state.pluginName} v${state.version} 已解压、安装并完成验证"
                                                is LocalPluginImportState.AlreadyImported -> "${state.pluginName} v${state.version} 已存在，无需重复导入；可在下方插件列表中安装或重试"
                                                is LocalPluginImportState.Failed -> state.message
                                                LocalPluginImportState.Idle -> ""
                                            },
                                            style = RuntimeTheme.typography.bodySmall,
                                            color = contentColor,
                                            maxLines = if (isError) 5 else 3,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                    if (!isImporting && !isPending) {
                                        TextButton(onClick = { viewModel?.clearLocalPluginImportState() }) {
                                            Text("关闭", color = contentColor)
                                        }
                                    }
                                }
                                if (isImporting && state is LocalPluginImportState.Importing) {
                                    LinearProgressIndicator(
                                        progress = { state.progress ?: 0.03f },
                                        modifier = Modifier.fillMaxWidth().height(4.dp).clip(RuntimeTheme.shapes.hairline),
                                        color = contentColor,
                                    )
                                }
                                if (state is LocalPluginImportState.Reading) {
                                    LinearProgressIndicator(
                                        modifier = Modifier.fillMaxWidth().height(4.dp).clip(RuntimeTheme.shapes.hairline),
                                        color = contentColor,
                                    )
                                }
                            }
                        }
                    }
                }
                // 🚀 后台装配进行中提示卡片 (Background Installing Banner)
                if (isInstallingComponents || componentInstallLog.isNotEmpty()) {
                    item {
                        RuntimeCard(
                            modifier = Modifier.fillMaxWidth(),
                            containerColor = RuntimeTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
                            borderColor = RuntimeTheme.colorScheme.primary,
                            contentPadding = PaddingValues(16.dp),
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                ) {
                                    RuntimeCircularProgressIndicator(
                                        modifier = Modifier.size(18.dp),
                                        strokeWidth = 2.dp,
                                        color = RuntimeTheme.colorScheme.primary,
                                    )
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = if (isInstallingComponents) "后台正在装配开发套件..." else "最近一次开发套件装配",
                                            style = RuntimeTheme.typography.titleSmall.copy(fontWeight = FontWeight.Medium),
                                            color = RuntimeTheme.colorScheme.primary,
                                        )
                                        Text(
                                            text = componentInstallProgress ?: if (isInstallingComponents) "正在执行后台批量装配流水线，你可自由切换到其他页面" else "装配任务已结束，可查看完整日志",
                                            style = RuntimeTheme.typography.bodySmall,
                                            color = RuntimeTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 2,
                                        )
                                    }
                                }
                                if (isInstallingComponents) {
                                    LinearProgressIndicator(
                                        modifier = Modifier.fillMaxWidth().height(4.dp).clip(RuntimeTheme.shapes.hairline),
                                        color = RuntimeTheme.colorScheme.primary,
                                    )
                                }
                                TextButton(
                                    onClick = { showBundleInstallLog = true },
                                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp),
                                ) {
                                    Text("查看安装日志", style = RuntimeTheme.typography.labelMedium)
                                }
                            }
                        }
                    }
                }
                // 1. 聚合大套件专区 (Plugin Bundles)
                if (showBundles) {
                    item {
                        Text(
                            text = "开发者全栈套件 (Dev Bundles)",
                            style = RuntimeTheme.typography.titleSmall.copy(fontWeight = FontWeight.Medium),
                            color = RuntimeTheme.colorScheme.primary,
                            modifier = Modifier.padding(bottom = 2.dp),
                        )
                    }
                    items(viewModel?.pluginBundles.orEmpty(), key = { it.id }) { bundle ->
                        val installedCount = bundle.components.count { it.id in installedComponentIds }
                        val isCoreReady = bundle.components.filter { it.isRequired }.all { it.id in installedComponentIds }
                        RuntimeCard(
                            modifier = Modifier.fillMaxWidth(),
                            onClick = { viewModel?.openBundleSetup(bundle) },
                            containerColor = RuntimeTheme.colorScheme.surfaceContainerLow,
                            borderColor = if (isCoreReady) RuntimeTheme.colorScheme.primary else Color.Transparent,
                            contentPadding = PaddingValues(16.dp),
                        ) {
                            Column(
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(40.dp)
                                            .clip(CircleShape)
                                            .background(
                                                if (isCoreReady) RuntimeTheme.colorScheme.primaryContainer
                                                else RuntimeTheme.colorScheme.surfaceContainerHighest,
                                            ),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        RuntimeIcon(
                                            name = bundleIcon(bundle.iconName),
                                            modifier = Modifier.size(22.dp),
                                            tint = if (isCoreReady) RuntimeTheme.colorScheme.onSurface else RuntimeTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }

                                    Column(
                                        modifier = Modifier.weight(1f),
                                        verticalArrangement = Arrangement.spacedBy(4.dp),
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                        ) {
                                            Text(
                                                text = bundle.name,
                                                style = RuntimeTheme.typography.titleMedium,
                                                color = RuntimeTheme.colorScheme.onSurface,
                                                modifier = Modifier.weight(1f, fill = false),
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                            Spacer(Modifier.width(6.dp))
                                            Surface(
                                                shape = RuntimeTheme.shapes.tiny,
                                                color = if (isCoreReady) RuntimeTheme.colorScheme.primary.copy(alpha = 0.12f)
                                                else RuntimeTheme.colorScheme.surfaceContainerHighest,
                                                modifier = Modifier.wrapContentWidth(),
                                            ) {
                                                Text(
                                                    text = if (isCoreReady) "已就绪 ($installedCount/${bundle.components.size})" else "未装配 ($installedCount/${bundle.components.size})",
                                                    style = RuntimeTheme.typography.labelSmall.copy(fontSize = 11.sp),
                                                    color = if (isCoreReady) RuntimeTheme.colorScheme.primary else RuntimeTheme.colorScheme.onSurfaceVariant,
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                                    maxLines = 1,
                                                )
                                            }
                                        }
                                        Text(
                                            text = bundle.summary,
                                            style = RuntimeTheme.typography.bodySmall,
                                            color = RuntimeTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                }

                                // 子组件标签胶囊列表（FlowRow 自动换行，绝不挤压）
                                @OptIn(ExperimentalLayoutApi::class)
                                FlowRow(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp),
                                ) {
                                    bundle.components.forEach { comp ->
                                        val isInstalled = comp.id in installedComponentIds
                                        Surface(
                                            shape = RuntimeTheme.shapes.tiny,
                                            color = if (isInstalled) RuntimeTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                                            else RuntimeTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.6f),
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                            ) {
                                                if (comp.isRequired) {
                                                    RuntimeIcon(RuntimeIconName.Shield, Modifier.size(10.dp), tint = RuntimeTheme.colorScheme.onSurface)
                                                }
                                                Text(
                                                    text = if (comp.isRequired) "${comp.name} (必选)" else comp.name,
                                                    style = RuntimeTheme.typography.labelSmall.copy(fontSize = 11.sp),
                                                    color = if (isInstalled) RuntimeTheme.colorScheme.onPrimaryContainer else RuntimeTheme.colorScheme.onSurfaceVariant,
                                                )
                                            }
                                        }
                                    }
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.End,
                                ) {
                                    FilledTonalButton(
                                        onClick = { viewModel?.openBundleSetup(bundle) },
                                        shape = RuntimeTheme.shapes.medium,
                                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                            RuntimeIcon(RuntimeIconName.Tune, Modifier.size(14.dp))
                                            Text(
                                                text = if (isCoreReady) "组件管理与配置" else "装配套件",
                                                style = RuntimeTheme.typography.labelMedium.copy(fontWeight = FontWeight.Medium),
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // 2. 独立 Agent 工具与服务专区
                if (showTools && filteredTools.isNotEmpty()) {
                    if (showBundles) {
                        item {
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = "智能体生态与服务 (Agents & Services)",
                                style = RuntimeTheme.typography.titleSmall.copy(fontWeight = FontWeight.Medium),
                                color = RuntimeTheme.colorScheme.primary,
                                modifier = Modifier.padding(bottom = 2.dp),
                            )
                        }
                    }

                    items(filteredTools, key = { it.id }) { tool ->
                        ToolCard(
                            tool = tool,
                            source = viewModel?.toolSource(tool.id).orEmpty(),
                            progress = installProgress[tool.id],
                            verification = verifications[tool.id],
                            onInstall = { viewModel?.installTool(tool.id) },
                            onUpdate = { viewModel?.updateTool(tool.id) },
                            onUninstall = {
                                pendingUninstallId = tool.id
                                pendingUninstallName = tool.name
                            },
                            onVerify = { viewModel?.verifyTool(tool.id) },
                            onLaunch = { onLaunchPty(tool.id) },
                            onViewLogs = { viewModel?.viewLogs(tool.id) },
                            onOpenDetail = { onOpenToolDetail(tool.id) },
                            onStartAiHealing = {
                                // 与日志弹窗「AI 自愈」入口一致：始终传入该工具的全量日志
                                viewModel?.fullToolLogs(tool.id) { logs ->
                                    onStartAiHealing(tool.id, tool.name, logs)
                                }
                            },
                        )
                    }
                }

                // 分类下无任何工具时给出空态提示，避免列表区完全空白
                if (showTools && filteredTools.isEmpty()) {
                    item(key = "tool_center_empty_category") {
                        val emptyTitle = androidx.compose.ui.res.stringResource(if (selectedCategory == "INSTALLED") R.string.plugin_center_empty_title else R.string.settings_tool_center_empty_category_title)
                        val emptySubtitle = androidx.compose.ui.res.stringResource(if (selectedCategory == "INSTALLED") R.string.plugin_center_empty_summary else R.string.settings_tool_center_empty_category_subtitle)
                        RuntimeCard(
                            modifier = Modifier.fillMaxWidth(),
                            containerColor = RuntimeTheme.colorScheme.surfaceContainerLow,
                            borderColor = Color.Transparent,
                            contentPadding = PaddingValues(16.dp),
                        ) {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                RuntimeIcon(
                                    name = RuntimeIconName.Package,
                                    modifier = Modifier.size(40.dp),
                                    tint = RuntimeTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                                )
                                Text(
                                    text = emptyTitle,
                                    style = RuntimeTheme.typography.titleSmall,
                                    color = RuntimeTheme.colorScheme.onSurface,
                                )
                                Text(
                                    text = emptySubtitle,
                                    style = RuntimeTheme.typography.bodySmall,
                                    color = RuntimeTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
        }
        }

        // A package is only committed/installed after explicit confirmation.
        (localPluginImport as? LocalPluginImportState.PendingConfirmation)?.let { pending ->
            LocalPluginImportConfirmation(pending, { viewModel?.cancelLocalPluginImport() }, { viewModel?.confirmLocalPluginImport() })
        }

        // Install Logs Dialog
        if (viewingLogsToolId != null) {
            val context = androidx.compose.ui.platform.LocalContext.current
            val toolId = viewingLogsToolId ?: ""
            val toolName = tools.firstOrNull { it.id == toolId }?.name ?: toolId
            val hasErrors = toolLogs.any { it.event.contains("FAIL", ignoreCase = true) || it.message.startsWith("ERR") }

            RuntimeAlertDialog(
                onDismissRequest = { viewModel?.viewLogs(null) },
                title = { Text("工具执行日志 ($toolName)") },
                text = {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 200.dp, max = 360.dp),
                        shape = RuntimeTheme.shapes.chip,
                        color = RuntimeTheme.colorScheme.surfaceContainerLowest,
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(10.dp)
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            if (toolLogs.isEmpty()) {
                                Text(
                                    text = "暂无相关事件日志",
                                    style = RuntimeTheme.typography.labelSmall,
                                    color = RuntimeTheme.colorScheme.outline,
                                )
                            } else {
                                toolLogs.forEach { log ->
                                    Text(
                                        text = "[${log.event}] ${log.message}",
                                        style = RuntimeTheme.typography.labelSmall.copy(
                                            fontFamily = FontFamily.Monospace,
                                            fontSize = 11.sp,
                                        ),
                                        color = if (log.event.contains("FAIL", ignoreCase = true) || log.message.startsWith("ERR:")) {
                                            RuntimeTheme.colorScheme.error
                                        } else {
                                            RuntimeTheme.colorScheme.onSurface
                                        },
                                    )
                                }
                            }
                        }
                    }
                },
                confirmButton = {

                        if (hasErrors) {
                            Button(
                                onClick = {
                                    val fullLogs = toolLogs.map { "[${it.event}] ${it.message}" }
                                    viewModel?.viewLogs(null)
                                    onStartAiHealing(toolId, toolName, fullLogs)
                                },
                                shape = RuntimeTheme.shapes.medium,
                            ) {
                                RuntimeIcon(
                                    name = RuntimeIconName.Brain,
                                    modifier = Modifier.size(14.dp),
                                )
                                Spacer(Modifier.width(4.dp))
                                Text("🧠 AI 自愈", fontWeight = FontWeight.Medium)
                            }
                        }
                        TextButton(
                            onClick = {
                                val fullLogs = toolLogs.joinToString("\n") { "[${it.event}] ${it.message}" }
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                                val clip = ClipData.newPlainText("tool_logs", fullLogs)
                                clipboard?.setPrimaryClip(clip)
                                Toast.makeText(context, "日志已复制到剪贴板", Toast.LENGTH_SHORT).show()
                            },
                            enabled = toolLogs.isNotEmpty(),
                        ) {
                            Text("复制")
                        }
                        TextButton(
                            onClick = { viewModel?.clearLogs(toolId) },
                            enabled = toolLogs.isNotEmpty(),
                        ) {
                            Text("清空")
                        }
                        TextButton(onClick = { viewModel?.viewLogs(null) }) {
                            Text("关闭")
                        }

                },
            )
        }

        if (showBundleInstallLog) {
            val context = androidx.compose.ui.platform.LocalContext.current
            RuntimeAlertDialog(
                onDismissRequest = { showBundleInstallLog = false },
                title = { Text("开发套件安装日志") },
                text = {
                    Surface(
                        modifier = Modifier.fillMaxWidth().heightIn(min = 220.dp, max = 420.dp),
                        shape = RuntimeTheme.shapes.chip,
                        color = RuntimeTheme.colorScheme.surfaceContainerLowest,
                    ) {
                        Column(
                            modifier = Modifier.fillMaxSize().padding(10.dp).verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            if (componentInstallLog.isEmpty()) {
                                Text("安装日志正在接收...", style = RuntimeTheme.typography.labelSmall, color = RuntimeTheme.colorScheme.outline)
                            } else {
                                componentInstallLog.forEach { line ->
                                    val isError = line.contains("error", ignoreCase = true) ||
                                        line.contains("failed", ignoreCase = true) ||
                                        line.contains("失败") ||
                                        line.contains("returned an error", ignoreCase = true) ||
                                        line.startsWith("E:")
                                    Text(
                                        line,
                                        style = RuntimeTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace, fontSize = 11.sp),
                                        color = if (isError) RuntimeTheme.colorScheme.error else RuntimeTheme.colorScheme.onSurface,
                                    )
                                }
                            }
                        }
                    }
                },
                confirmButton = {

                        TextButton(
                            onClick = {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                                clipboard?.setPrimaryClip(ClipData.newPlainText("TaiXu Bundle Install Log", componentInstallLog.joinToString("\n")))
                                // 与工具日志复制行为保持一致，复制后给出反馈
                                Toast.makeText(context, "日志已复制到剪贴板", Toast.LENGTH_SHORT).show()
                            },
                            enabled = componentInstallLog.isNotEmpty(),
                        ) { Text("复制") }
                        TextButton(onClick = { showBundleInstallLog = false }) { Text("关闭") }

                },
            )
        }

        // 卡片级卸载二次确认（与工具详情页同一弹窗组件）
        pendingUninstallId?.let { uninstallId ->
            UninstallToolConfirmDialog(
                toolName = pendingUninstallName.orEmpty(),
                onConfirm = {
                    viewModel?.uninstallTool(uninstallId)
                    pendingUninstallId = null
                    pendingUninstallName = null
                },
                onDismiss = {
                    pendingUninstallId = null
                    pendingUninstallName = null
                },
            )
        }

        activeBundle?.let { bundle ->
            BundleSetupDialogWithUpdates(
                bundle = bundle,
                installedComponentIds = installedComponentIds,
                selectedComponents = selectedComponents,
                isInstallingComponents = isInstallingComponents,
                componentInstallProgress = componentInstallProgress,
                onToggle = { arg0 -> viewModel?.toggleComponent(arg0) },
                onInstall = { viewModel?.installActiveBundleComponents() },
                onReinstall = { arg0 -> viewModel?.reinstallComponent(arg0) },
                onUninstall = { arg0 -> viewModel?.uninstallComponent(arg0) },
                uninstallBlockedReason = { arg0 -> viewModel?.uninstallBlockedReason(arg0) },
                onDismiss = { viewModel?.closeBundleSetup() },
            )
        }
    }

    } // Box
}

private fun formatBytes(bytes: Long): String = when {
    bytes >= 1024L * 1024L * 1024L -> "%.2f GB".format(bytes / (1024.0 * 1024.0 * 1024.0))
    bytes >= 1024L * 1024L -> "%.1f MB".format(bytes / (1024.0 * 1024.0))
    bytes >= 1024L -> "%.1f KB".format(bytes / 1024.0)
    else -> "$bytes B"
}
