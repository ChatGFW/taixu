package top.wkbin.taixu.ui.workflow

import top.wkbin.taixu.ui.miuix.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

import android.content.ClipData
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.unit.sp
import top.wkbin.taixu.ui.components.RuntimeIconButton
import top.wkbin.taixu.ui.components.RuntimeFilledTextField
import top.yukonga.miuix.kmp.basic.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import top.wkbin.taixu.core.model.workflow.HostWorkflowActions
import top.wkbin.taixu.core.model.workflow.HostWorkflowPrivilege
import top.wkbin.taixu.core.model.workflow.NodeRunStatus
import top.wkbin.taixu.core.model.workflow.WorkflowEdge
import top.wkbin.taixu.core.model.workflow.WorkflowNode
import top.wkbin.taixu.core.model.workflow.WorkflowNodeType
import top.wkbin.taixu.core.model.workflow.WorkflowRunStatus
import top.wkbin.taixu.core.model.workflow.WorkflowRuntimeState
import top.wkbin.taixu.ui.components.RuntimeAlertDialog
import top.wkbin.taixu.ui.components.RuntimeButton
import top.wkbin.taixu.ui.components.RuntimeCard
import top.wkbin.taixu.ui.components.RuntimeDropdownField
import top.wkbin.taixu.ui.components.RuntimeIcon
import top.wkbin.taixu.ui.components.RuntimeIconName
import top.wkbin.taixu.ui.components.RuntimeOutlinedButton

/** 共享的 pretty Json：构建实例开销大，不在 remember/重组中反复创建。 */
private val PrettyJson = Json { prettyPrint = true }

@Composable
fun WorkflowEditorView(
    state: WorkflowEditorUiState,
    activeRunState: WorkflowRuntimeState? = null,
    onRun: () -> Unit = {},
    onCancelRun: () -> Unit = {},
    onSelectNode: (String?) -> Unit,
    onMoveNode: (String, Float, Float) -> Unit,
    onBeginConnection: (String) -> Unit,
    onCancelConnection: () -> Unit,
    onConnect: (String) -> Unit,
    onAddNode: (WorkflowNodeType) -> Unit,
    onUpdateNode: (WorkflowNode) -> Unit,
    onUpdateMetadata: (String, String, String, Int) -> Unit,
    onRemoveNode: () -> Unit,
    onRemoveNodeById: (String) -> Unit = {},
    onConnectNodes: (String, String, String, String?) -> Unit = { _, _, _, _ -> },
    onDisconnectNodes: (String, String) -> Unit = { _, _ -> },
    onDisconnectAllForNode: (String) -> Unit = {},
    onUpdateEdge: (WorkflowEdge) -> Unit,
    onRemoveEdge: (String) -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onSave: () -> Unit,
    onAutoLayout: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isRunning = activeRunState?.status in setOf(WorkflowRunStatus.RUNNING, WorkflowRunStatus.WAITING_APPROVAL)
    var sheetExpanded by remember { mutableStateOf(false) }
    var showRunConsole by remember { mutableStateOf(false) }
    var nodePendingDelete by remember { mutableStateOf<WorkflowNode?>(null) }

    BoxWithConstraints(modifier) {
        val editorHeight = maxHeight
        val isWideScreen = top.wkbin.taixu.ui.components.widthClassOf(maxWidth.value) == top.wkbin.taixu.ui.components.RuntimeWidthClass.Expanded
        val selectedNode = state.definition.nodes.firstOrNull { it.id == state.selectedNodeId }
        val isInspectorOpen = !isWideScreen && sheetExpanded && selectedNode != null

        Column(Modifier.fillMaxSize()) {
            state.message?.let { message ->
                Surface(
                    color = if (message == "已保存" || message == "请选择目标节点") {
                        RuntimeTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                    } else {
                        RuntimeTheme.colorScheme.errorContainer.copy(alpha = 0.45f)
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = message,
                        style = RuntimeTheme.typography.labelSmall,
                        color = if (message == "已保存" || message == "请选择目标节点") {
                            RuntimeTheme.colorScheme.primary
                        } else {
                            RuntimeTheme.colorScheme.error
                        },
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    )
                }
            }

            Box(Modifier.weight(1f).fillMaxWidth()) {
                val canvas: @Composable (Modifier) -> Unit = { canvasModifier ->
                    WorkflowCanvas2D(
                        definition = state.definition,
                        state = activeRunState,
                        editable = !isRunning,
                        selectedNodeId = state.selectedNodeId,
                        connectionSourceId = state.connectionSourceId,
                        onNodeSelected = onSelectNode,
                        onNodeClicked = { nodeId ->
                            onSelectNode(nodeId)
                            sheetExpanded = true
                        },
                        onCanvasTapped = {
                            sheetExpanded = false
                            onSelectNode(null)
                        },
                        onNodeMoved = onMoveNode,
                        onConnectionRequested = { _, target -> onConnect(target) },
                        onBeginConnection = onBeginConnection,
                        onRemoveNode = { nodeId ->
                            val target = state.definition.nodes.firstOrNull { it.id == nodeId }
                            if (target != null) nodePendingDelete = target
                        },
                        onConfigureNode = { nodeId ->
                            onSelectNode(nodeId)
                            sheetExpanded = true
                        },
                        modifier = canvasModifier,
                    )
                }
                val inspector: @Composable (Modifier) -> Unit = { inspectorModifier ->
                    EditorInspector(
                        state = state,
                        activeRunState = activeRunState,
                        onUpdateNode = onUpdateNode,
                        onUpdateMetadata = onUpdateMetadata,
                        onBeginConnection = onBeginConnection,
                        onCancelConnection = onCancelConnection,
                        onRemoveNode = {
                            val selected = state.definition.nodes.firstOrNull { it.id == state.selectedNodeId }
                            if (selected != null) nodePendingDelete = selected
                        },
                        onConnectNodes = onConnectNodes,
                        onDisconnectAllForNode = onDisconnectAllForNode,
                        onUpdateEdge = onUpdateEdge,
                        onRemoveEdge = onRemoveEdge,
                        onOpenConsole = { showRunConsole = true },
                        modifier = inspectorModifier,
                    )
                }

                if (isWideScreen) {
                    Row(Modifier.fillMaxSize()) {
                        canvas(Modifier.weight(1f).fillMaxSize())
                        if (sheetExpanded && selectedNode != null) {
                            VerticalDivider()
                            Box(Modifier.width(420.dp).fillMaxSize()) {
                                Column(Modifier.fillMaxSize()) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 16.dp, vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    ) {
                                        val theme = selectedNode.type.visualTheme()
                                        Surface(
                                            color = theme.accentColor.copy(alpha = 0.16f),
                                            shape = RuntimeTheme.shapes.extraSmall,
                                        ) {
                                            Text(
                                                text = theme.tag,
                                                style = RuntimeTheme.typography.labelSmall,
                                                color = theme.accentColor,
                                                fontWeight = FontWeight.Medium,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                            )
                                        }
                                        Text(
                                            text = selectedNode.title,
                                            style = RuntimeTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Medium,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f),
                                        )
                                        RuntimeIconButton(
                                            onClick = {
                                                sheetExpanded = false
                                                onSelectNode(null)
                                            },
                                            modifier = Modifier.size(32.dp),
                                            contentDescription = "关闭面板",
                                        ) {
                                            RuntimeIcon(RuntimeIconName.Close, Modifier.size(18.dp))
                                        }
                                    }
                                    HorizontalDivider()
                                    inspector(Modifier.fillMaxSize())
                                }
                            }
                        }
                    }
                } else {
                    EditorBottomSheetLayout(
                        maxHeight = editorHeight,
                        selectedNode = selectedNode,
                        sheetExpanded = sheetExpanded && selectedNode != null,
                        onSheetExpandedChange = { expanded ->
                            sheetExpanded = expanded
                            if (!expanded) onSelectNode(null)
                        },
                        isRunning = isRunning,
                        canvas = canvas,
                        inspector = inspector,
                    )
                }
            }

            AnimatedVisibility(
                visible = !isInspectorOpen,
                enter = slideInVertically(initialOffsetY = { it }, animationSpec = tween(220, easing = FastOutSlowInEasing)) +
                    expandVertically(animationSpec = tween(220, easing = FastOutSlowInEasing)) +
                    fadeIn(tween(160)),
                exit = slideOutVertically(targetOffsetY = { it }, animationSpec = tween(180, easing = FastOutSlowInEasing)) +
                    shrinkVertically(animationSpec = tween(180, easing = FastOutSlowInEasing)) +
                    fadeOut(tween(140)),
            ) {
                EditorBottomDock(
                    state = state,
                    activeRunState = activeRunState,
                    onAddNode = onAddNode,
                    onDeleteSelectedNode = {
                        val selected = state.definition.nodes.firstOrNull { it.id == state.selectedNodeId }
                        if (selected != null) nodePendingDelete = selected
                    },
                    onToggleConsole = { showRunConsole = true },
                    onUndo = onUndo,
                    onRedo = onRedo,
                    onSave = onSave,
                    onAutoLayout = onAutoLayout,
                    onRun = {
                        onRun()
                        showRunConsole = true
                    },
                    onCancelRun = onCancelRun,
                )
            }
        }
    }

    // 节点删除确认弹窗
    nodePendingDelete?.let { target ->
        RuntimeAlertDialog(
            onDismissRequest = { nodePendingDelete = null },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    RuntimeIcon(RuntimeIconName.Trash, Modifier.size(20.dp), tint = RuntimeTheme.colorScheme.error)
                    Text("确认删除节点", style = RuntimeTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
                }
            },
            text = {
                Text(
                    text = "是否确定删除「${target.title}」？\n与该节点相连的所有输入和输出连线也将被一并断开移除。",
                    style = RuntimeTheme.typography.bodyMedium,
                )
            },
            confirmButton = {
                RuntimeButton(
                    onClick = {
                        val id = target.id
                        nodePendingDelete = null
                        if (state.selectedNodeId == id) onRemoveNode() else onRemoveNodeById(id)
                    },
                ) {
                    Text("确认删除")
                }
            },
            dismissButton = {
                RuntimeOutlinedButton(onClick = { nodePendingDelete = null }) {
                    Text("取消")
                }
            },
        )
    }

    // 运行日志与控制台弹窗
    if (showRunConsole && activeRunState != null) {
        RunConsoleModal(
            activeRunState = activeRunState,
            onDismiss = { showRunConsole = false },
            onCancelRun = onCancelRun,
            onRerun = onRun,
        )
    }
}

@Composable
private fun EditorBottomSheetLayout(
    maxHeight: Dp,
    selectedNode: WorkflowNode?,
    sheetExpanded: Boolean,
    onSheetExpandedChange: (Boolean) -> Unit,
    isRunning: Boolean,
    canvas: @Composable (Modifier) -> Unit,
    inspector: @Composable (Modifier) -> Unit,
) {
    Box(Modifier.fillMaxSize()) {
        // 画布全屏铺满，彻底消除常驻 56dp 底栏占用
        canvas(Modifier.fillMaxSize())

        // 仅在单击节点并且展开时才向上弹出，非持久化
        AnimatedVisibility(
            visible = sheetExpanded && selectedNode != null,
            enter = slideInVertically(initialOffsetY = { it }, animationSpec = tween(280, easing = FastOutSlowInEasing)) + fadeIn(tween(200)),
            exit = slideOutVertically(targetOffsetY = { it }, animationSpec = tween(220, easing = FastOutSlowInEasing)) + fadeOut(tween(180)),
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            if (selectedNode != null) {
                val theme = selectedNode.type.visualTheme()
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(maxHeight * 0.62f)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) {},
                    shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
                    color = RuntimeTheme.colorScheme.surfaceContainerHigh,
                ) {
                    Column(
                        Modifier
                            .fillMaxSize()
                            .navigationBarsPadding(),
                    ) {
                        // 顶部居中拖拽把手指示条
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp, bottom = 4.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(width = 36.dp, height = 4.dp)
                                    .background(
                                        RuntimeTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f),
                                        CircleShape,
                                    ),
                            )
                        }

                        // 弹窗顶部栏：类型彩色药丸、节点标题、关闭按钮
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Surface(
                                color = theme.accentColor.copy(alpha = 0.16f),
                                shape = RuntimeTheme.shapes.extraSmall,
                            ) {
                                Text(
                                    text = theme.tag,
                                    style = RuntimeTheme.typography.labelSmall,
                                    color = theme.accentColor,
                                    fontWeight = FontWeight.Medium,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                )
                            }
                            Text(
                                text = selectedNode.title,
                                style = RuntimeTheme.typography.titleSmall,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                            )
                            RuntimeIconButton(
                                onClick = { onSheetExpandedChange(false) },
                                modifier = Modifier.size(32.dp),
                                contentDescription = "收起面板",
                            ) {
                                RuntimeIcon(RuntimeIconName.Close, Modifier.size(18.dp), tint = RuntimeTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        HorizontalDivider()

                        // 属性配置与连线检查器
                        inspector(Modifier.fillMaxSize())
                    }
                }
            }
        }
    }
}

@Composable
private fun EditorBottomDock(
    state: WorkflowEditorUiState,
    activeRunState: WorkflowRuntimeState?,
    onAddNode: (WorkflowNodeType) -> Unit,
    onDeleteSelectedNode: () -> Unit,
    onToggleConsole: () -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onSave: () -> Unit,
    onAutoLayout: () -> Unit,
    onRun: () -> Unit,
    onCancelRun: () -> Unit,
) {
    var addDialogVisible by remember { mutableStateOf(false) }
    val isRunning = activeRunState?.status in setOf(WorkflowRunStatus.RUNNING, WorkflowRunStatus.WAITING_APPROVAL)

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(0.dp), // 去除圆角，平铺沉底贴边
        color = RuntimeTheme.colorScheme.surfaceContainer,
        tonalElevation = 3.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding(),
        ) {
            HorizontalDivider(
                color = RuntimeTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
                thickness = 1.dp,
            )
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
            DockActionButton(
                icon = RuntimeIconName.Plus,
                label = "添加",
                onClick = { addDialogVisible = true },
                tint = RuntimeTheme.colorScheme.primary,
            )

            // 运行 / 停止调试
            if (isRunning) {
                DockActionButton(
                    icon = RuntimeIconName.Stop,
                    label = "停止",
                    onClick = onCancelRun,
                    tint = RuntimeTheme.colorScheme.error,
                )
            } else {
                DockActionButton(
                    icon = RuntimeIconName.Play,
                    label = "运行",
                    onClick = onRun,
                    tint = RuntimeTheme.colorScheme.primary,
                )
            }

            // 控制台 / 日志
            if (activeRunState != null) {
                DockActionButton(
                    icon = RuntimeIconName.Terminal,
                    label = if (isRunning) "运行中" else "日志",
                    onClick = onToggleConsole,
                    tint = if (isRunning) RuntimeTheme.colorScheme.primary else RuntimeTheme.colorScheme.onSurfaceVariant,
                    badge = if (isRunning) {
                        {
                            Box(
                                modifier = Modifier
                                    .size(7.dp)
                                    .background(RuntimeTheme.colorScheme.primary, CircleShape)
                                    .align(Alignment.TopEnd)
                            )
                        }
                    } else null,
                )
            }

            // 删除节点（当选中节点且未在运行时呈现）
            if (state.selectedNodeId != null && !isRunning) {
                DockActionButton(
                    icon = RuntimeIconName.Trash,
                    label = "删除",
                    onClick = onDeleteSelectedNode,
                    tint = RuntimeTheme.colorScheme.error,
                )
            }

            DockActionButton(
                icon = RuntimeIconName.Tune,
                label = "排版",
                onClick = onAutoLayout,
            )

            DockActionButton(
                icon = RuntimeIconName.Back,
                label = "撤销",
                enabled = state.canUndo && !isRunning,
                onClick = onUndo,
            )

            DockActionButton(
                icon = RuntimeIconName.Reverse,
                label = "重做",
                enabled = state.canRedo && !isRunning,
                onClick = onRedo,
            )

            }
            DockActionButton(
                icon = RuntimeIconName.Save,
                label = when {
                    state.isSaving -> "保存中"
                    state.isDirty -> "保存*"
                    else -> "已保存"
                },
                enabled = !state.isSaving && state.isDirty && !isRunning,
                tint = if (state.isDirty) RuntimeTheme.colorScheme.tertiary else RuntimeTheme.colorScheme.onSurfaceVariant,
                onClick = onSave,
            )
        }
    }
}

    if (addDialogVisible) {
        NodePickerModal(
            onDismiss = { addDialogVisible = false },
            onSelect = { type ->
                addDialogVisible = false
                onAddNode(type)
            },
        )
    }
}

@Composable
private fun DockActionButton(
    icon: RuntimeIconName,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    tint: Color = RuntimeTheme.colorScheme.onSurfaceVariant,
    badge: (@Composable BoxScope.() -> Unit)? = null,
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(0.dp), // 去除圆角
        color = Color.Transparent,
        modifier = modifier
            .alpha(if (enabled) 1f else 0.38f)
            .padding(horizontal = 2.dp),
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                RuntimeIcon(icon, Modifier.size(19.dp), tint = tint)
                badge?.invoke(this)
            }
            Spacer(Modifier.height(3.dp))
            Text(
                text = label,
                style = RuntimeTheme.typography.labelSmall.copy(fontSize = 11.sp),
                color = tint,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun RunConsoleModal(
    activeRunState: WorkflowRuntimeState,
    onDismiss: () -> Unit,
    onCancelRun: () -> Unit,
    onRerun: () -> Unit,
) {
    val clipboard = LocalClipboard.current
    val clipboardScope = rememberCoroutineScope()
    val isRunning = activeRunState.status in setOf(WorkflowRunStatus.RUNNING, WorkflowRunStatus.WAITING_APPROVAL)
    val startedAt = activeRunState.startedAt ?: 0L
    val finishedAt = activeRunState.finishedAt ?: 0L
    val durationMs = if (finishedAt > 0L && startedAt > 0L) {
        finishedAt - startedAt
    } else if (startedAt > 0L) {
        (System.currentTimeMillis() - startedAt).coerceAtLeast(0L)
    } else {
        0L
    }

    val consoleOutput = remember(activeRunState) {
        buildString {
            appendLine("=== 太墟工作流「${activeRunState.definition.name}」控制台输出 ===")
            appendLine("全局状态: ${runStatusLabel(activeRunState.status)}  |  执行耗时: ${durationMs}ms")
            activeRunState.error?.let {
                appendLine("❌ 异常信息: $it")
            }
            appendLine()
            activeRunState.definition.nodes.forEach { node ->
                val run = activeRunState.nodeStates[node.id]
                val status = run?.status ?: NodeRunStatus.PENDING
                appendLine("--------------------------------------------------")
                appendLine("[${statusLabel(status)}] 节点: ${node.title} (${node.id})")
                val progress = run?.progressMessage
                val textOutput = run?.output?.textOutput
                val errorOutput = run?.output?.error
                if (!progress.isNullOrBlank()) {
                    appendLine("进度: $progress")
                }
                if (!textOutput.isNullOrBlank()) {
                    appendLine(textOutput)
                }
                if (!errorOutput.isNullOrBlank()) {
                    appendLine("stderr: $errorOutput")
                }
            }
            appendLine("--------------------------------------------------")
            appendLine("=== 执行输出结束 ===")
        }
    }

    val scrollState = rememberScrollState()
    LaunchedEffect(consoleOutput) {
        scrollState.animateScrollTo(scrollState.maxValue)
    }

    RuntimeAlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                RuntimeIcon(RuntimeIconName.Terminal, Modifier.size(20.dp), tint = RuntimeTheme.colorScheme.primary)
                Text("运行调试控制台", style = RuntimeTheme.typography.titleMedium, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                Surface(
                    color = runStatusColor(activeRunState.status).copy(alpha = 0.16f),
                    shape = CircleShape,
                ) {
                    Text(
                        text = runStatusLabel(activeRunState.status),
                        style = RuntimeTheme.typography.labelSmall,
                        color = runStatusColor(activeRunState.status),
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    activeRunState.definition.nodes.forEach { node ->
                        val nodeRun = activeRunState.nodeStates[node.id]
                        val st = nodeRun?.status ?: NodeRunStatus.PENDING
                        Surface(
                            shape = RuntimeTheme.shapes.chip,
                            color = statusColor(st).copy(alpha = 0.14f),
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                Text(
                                    text = statusIconText(st),
                                    style = RuntimeTheme.typography.labelSmall,
                                    color = statusColor(st),
                                )
                                Text(
                                    text = node.title,
                                    style = RuntimeTheme.typography.labelSmall,
                                    color = statusColor(st),
                                    maxLines = 1,
                                )
                            }
                        }
                    }
                }

                Surface(
                    color = Color(0xFF0F172A),
                    shape = RuntimeTheme.shapes.chip,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(300.dp),
                ) {
                    Box(Modifier.fillMaxSize().padding(10.dp)) {
                        Text(
                            text = consoleOutput,
                            color = Color(0xFFE2E8F0),
                            style = RuntimeTheme.typography.bodySmall.copy(
                                fontFamily = FontFamily.Monospace,
                            ),
                            modifier = Modifier.verticalScroll(scrollState),
                        )
                    }
                }
            }
        },
        confirmButton = {

                RuntimeOutlinedButton(onClick = {
                    clipboardScope.launch {
                        clipboard.setClipEntry(ClipEntry(ClipData.newPlainText("workflow_console", consoleOutput)))
                    }
                }) {
                    Text("复制日志")
                }
                if (isRunning) {
                    RuntimeOutlinedButton(onClick = onCancelRun) {
                        Text("停止运行", color = RuntimeTheme.colorScheme.error)
                    }
                } else {
                    RuntimeButton(onClick = onRerun) {
                        Text("重新运行")
                    }
                }

        },
        dismissButton = {
            RuntimeOutlinedButton(onClick = onDismiss) { Text("关闭") }
        },
    )
}

private fun statusIconText(status: NodeRunStatus) = when (status) {
    NodeRunStatus.SUCCESS -> "✔"
    NodeRunStatus.FAILED -> "✘"
    NodeRunStatus.RUNNING, NodeRunStatus.STREAMING -> "●"
    NodeRunStatus.SKIPPED -> "↷"
    NodeRunStatus.CANCELLED -> "⊘"
    NodeRunStatus.WAITING_APPROVAL -> "🛡"
    NodeRunStatus.IDLE, NodeRunStatus.PENDING -> "⌛"
}

@Composable
private fun runStatusColor(status: WorkflowRunStatus): Color = when (status) {
    WorkflowRunStatus.SUCCESS -> Color(0xFF2E7D32)
    WorkflowRunStatus.FAILED, WorkflowRunStatus.CANCELLED -> RuntimeTheme.colorScheme.error
    WorkflowRunStatus.RUNNING, WorkflowRunStatus.WAITING_APPROVAL -> RuntimeTheme.colorScheme.primary
    WorkflowRunStatus.IDLE -> RuntimeTheme.colorScheme.outline
}

private fun runStatusLabel(status: WorkflowRunStatus): String = when (status) {
    WorkflowRunStatus.IDLE -> "未运行"
    WorkflowRunStatus.RUNNING -> "运行中"
    WorkflowRunStatus.WAITING_APPROVAL -> "等待审批"
    WorkflowRunStatus.SUCCESS -> "执行成功"
    WorkflowRunStatus.FAILED -> "执行失败"
    WorkflowRunStatus.CANCELLED -> "已取消"
}


@Composable
private fun NodePickerModal(
    onDismiss: () -> Unit,
    onSelect: (WorkflowNodeType) -> Unit,
) {
    RuntimeAlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("添加工作流节点", style = RuntimeTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = "选择要加入画布的节点类型，创建后可在检查面板配置参数：",
                    style = RuntimeTheme.typography.bodySmall,
                    color = RuntimeTheme.colorScheme.onSurfaceVariant,
                )
                WorkflowNodeType.entries.forEach { type ->
                    val meta = type.metadata()
                    Surface(
                        onClick = { onSelect(type) },
                        shape = RuntimeTheme.shapes.small,
                        color = RuntimeTheme.colorScheme.surfaceContainerHigh,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Surface(
                                color = RuntimeTheme.colorScheme.primary.copy(alpha = 0.12f),
                                shape = RuntimeTheme.shapes.chip,
                                modifier = Modifier.size(36.dp),
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    RuntimeIcon(meta.icon, Modifier.size(20.dp), tint = RuntimeTheme.colorScheme.primary)
                                }
                            }
                            Column(Modifier.weight(1f)) {
                                Text(meta.label, style = RuntimeTheme.typography.titleSmall, fontWeight = FontWeight.Medium)
                                Text(
                                    meta.summary,
                                    style = RuntimeTheme.typography.bodySmall,
                                    color = RuntimeTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            RuntimeOutlinedButton(onClick = onDismiss) { Text("取消") }
        },
    )
}

@Composable
private fun EditorInspector(
    state: WorkflowEditorUiState,
    activeRunState: WorkflowRuntimeState?,
    onUpdateNode: (WorkflowNode) -> Unit,
    onUpdateMetadata: (String, String, String, Int) -> Unit,
    onBeginConnection: (String) -> Unit,
    onCancelConnection: () -> Unit,
    onRemoveNode: () -> Unit,
    onConnectNodes: (String, String, String, String?) -> Unit,
    onDisconnectAllForNode: (String) -> Unit,
    onUpdateEdge: (WorkflowEdge) -> Unit,
    onRemoveEdge: (String) -> Unit,
    onOpenConsole: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val selected = state.definition.nodes.firstOrNull { it.id == state.selectedNodeId }
    Column(
        modifier = modifier.verticalScroll(rememberScrollState()).padding(top.wkbin.taixu.ui.components.runtimeContentPadding(top = RuntimeSpacing.pageTop, bottom = RuntimeSpacing.pageBottom)),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        WorkflowMetadataCard(state, onUpdateMetadata)

        if (selected == null) {
            RuntimeCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(16.dp)) {
                Text("请点击画布中的节点以配置参数、管理连线或查看执行日志", color = RuntimeTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            val nodeRun = activeRunState?.nodeStates?.get(selected.id)
            if (nodeRun != null) {
                RuntimeCard(
                    modifier = Modifier.fillMaxWidth(),
                    borderColor = statusColor(nodeRun.status).copy(alpha = 0.5f),
                    contentPadding = PaddingValues(16.dp),
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("调试运行状态", style = RuntimeTheme.typography.titleSmall, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                            Surface(
                                color = statusColor(nodeRun.status).copy(alpha = 0.16f),
                                shape = CircleShape,
                            ) {
                                Text(
                                    text = statusLabel(nodeRun.status),
                                    style = RuntimeTheme.typography.labelSmall,
                                    color = statusColor(nodeRun.status),
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                )
                            }
                        }
                        WorkflowNodeDetails(nodeRun)
                        RuntimeOutlinedButton(onClick = onOpenConsole, modifier = Modifier.fillMaxWidth()) {
                            RuntimeIcon(RuntimeIconName.Terminal, Modifier.size(16.dp))
                            Text("打开完整终端控制台日志", maxLines = 1)
                        }
                    }
                }
            }

            NodeInspectorCard(
                state = state,
                node = selected,
                isConnecting = state.connectionSourceId == selected.id,
                onApply = onUpdateNode,
                onBeginConnection = { onBeginConnection(selected.id) },
                onCancelConnection = onCancelConnection,
                onRemove = onRemoveNode,
                onConnectNodes = onConnectNodes,
                onRemoveEdge = onRemoveEdge,
            )

            NodeConnectionsCard(
                state = state,
                node = selected,
                onConnectNodes = onConnectNodes,
                onDisconnectAll = onDisconnectAllForNode,
                onUpdateEdge = onUpdateEdge,
                onRemoveEdge = onRemoveEdge,
            )
        }
        Spacer(Modifier.height(28.dp))
    }
}

@Composable
private fun NodeInspectorCard(
    state: WorkflowEditorUiState,
    node: WorkflowNode,
    isConnecting: Boolean,
    onApply: (WorkflowNode) -> Unit,
    onBeginConnection: () -> Unit,
    onCancelConnection: () -> Unit,
    onRemove: () -> Unit,
    onConnectNodes: (String, String, String, String?) -> Unit,
    onRemoveEdge: (String) -> Unit,
) {
    val meta = node.type.metadata()
    var title by remember(node.id, node.title) { mutableStateOf(node.title) }
    var description by remember(node.id, node.description) { mutableStateOf(node.description) }
    var timeout by remember(node.id, node.timeoutSeconds) { mutableStateOf(node.timeoutSeconds.toString()) }
    var policy by remember(node.id, node.failurePolicy) { mutableStateOf(node.failurePolicy) }

    val configMap = remember(node.id, node.config) {
        mutableStateMapOf<String, String>().apply { putAll(node.config) }
    }
    var showAdvancedJson by remember { mutableStateOf(false) }
    var rawJsonText by remember(node.id, node.config) {
        mutableStateOf(PrettyJson.encodeToString(node.config))
    }
    val parsedConfig = remember(rawJsonText) {
        runCatching { PrettyJson.decodeFromString<Map<String, String>>(rawJsonText) }.getOrNull()
    }

    RuntimeCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(16.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Surface(
                    color = RuntimeTheme.colorScheme.primary.copy(alpha = 0.14f),
                    shape = CircleShape,
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        RuntimeIcon(meta.icon, Modifier.size(14.dp), tint = RuntimeTheme.colorScheme.primary)
                        Text(meta.label, style = RuntimeTheme.typography.labelSmall, color = RuntimeTheme.colorScheme.primary, fontWeight = FontWeight.Medium)
                    }
                }
                Text(
                    text = node.id,
                    style = RuntimeTheme.typography.labelSmall,
                    color = RuntimeTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                RuntimeOutlinedButton(
                    onClick = onRemove,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                ) {
                    RuntimeIcon(RuntimeIconName.Trash, Modifier.size(14.dp), tint = RuntimeTheme.colorScheme.error)
                    Text("删除", style = RuntimeTheme.typography.labelSmall, color = RuntimeTheme.colorScheme.error)
                }
            }

            Surface(
                color = RuntimeTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.55f),
                shape = RuntimeTheme.shapes.small,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "💡 节点作用：${meta.summary}",
                        style = RuntimeTheme.typography.labelMedium,
                        fontWeight = FontWeight.Medium,
                        color = RuntimeTheme.colorScheme.primary,
                    )
                    Text(
                        text = meta.guide,
                        style = RuntimeTheme.typography.bodySmall,
                        color = RuntimeTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            RuntimeFilledTextField(
                value = title,
                onValueChange = { title = it },
                labelText = "节点标题",
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            RuntimeFilledTextField(
                value = description,
                onValueChange = { description = it },
                labelText = "节点备注 / 说明",
                minLines = 1,
                maxLines = 2,
                modifier = Modifier.fillMaxWidth(),
            )

            Text("核心参数配置", style = RuntimeTheme.typography.titleSmall, fontWeight = FontWeight.Medium)

            when (node.type) {
                WorkflowNodeType.BASH_COMMAND -> {
                    Surface(
                        color = RuntimeTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.5f),
                        shape = RuntimeTheme.shapes.small,
                        border = BorderStroke(1.dp, top.wkbin.taixu.ui.theme.semanticColors().info.copy(alpha = 0.4f)),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                RuntimeIcon(RuntimeIconName.Terminal, Modifier.size(16.dp), tint = top.wkbin.taixu.ui.theme.semanticColors().info)
                                Text("终端 Shell 命令行（在沙箱环境中执行）", style = RuntimeTheme.typography.titleSmall, fontWeight = FontWeight.Medium, color = top.wkbin.taixu.ui.theme.semanticColors().info)
                            }
                            RuntimeFilledTextField(
                                value = configMap["command"].orEmpty(),
                                onValueChange = { configMap["command"] = it },
                                labelText = "Bash 命令（在此输入执行的命令）",
                                placeholder = { Text("例如：git status --short && git diff") },
                                supportingText = { Text("支持引用上游输出：\${previous.output}、工作区：\${WORKSPACE_PATH}") },
                                minLines = 3,
                                maxLines = 6,
                                modifier = Modifier.fillMaxWidth(),
                            )
                            Text("常用命令预设（点击直接填入）：", style = RuntimeTheme.typography.labelSmall, color = RuntimeTheme.colorScheme.onSurfaceVariant)
                            Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                listOf(
                                    "系统内核" to "echo '=== 系统内核 ===' && uname -a && cat /etc/os-release | head -n 8",
                                    "内存磁盘" to "echo '=== 资源使用 ===' && free -h && df -h /",
                                    "Git 状态" to "git status --short && git diff --stat",
                                    "测试网络" to "curl -I -s -m 5 https://www.baidu.com | head -n 4",
                                    "工具排查" to "for cmd in git python3 curl make; do which \$cmd && echo \"✔ \$cmd\" || echo \"✘ \$cmd\"; done",
                                ).forEach { (name, cmd) ->
                                    Surface(
                                        onClick = { configMap["command"] = cmd },
                                        shape = CircleShape,
                                        color = top.wkbin.taixu.ui.theme.semanticColors().info.copy(alpha = 0.14f),
                                    ) {
                                        Text(
                                            text = "+ $name",
                                            style = RuntimeTheme.typography.labelSmall,
                                            color = top.wkbin.taixu.ui.theme.semanticColors().info,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                        )
                                    }
                                }
                            }
                            RuntimeFilledTextField(
                                value = configMap["workingDirectory"].orEmpty(),
                                onValueChange = { configMap["workingDirectory"] = it },
                                labelText = "工作目录（可选）",
                                placeholder = { Text("留空默认为当前工程根目录") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }

                WorkflowNodeType.CONDITION_BRANCH -> {
                    RuntimeFilledTextField(
                        value = configMap["expression"] ?: configMap["condition"].orEmpty(),
                        onValueChange = {
                            configMap["expression"] = it
                            configMap.remove("condition")
                        },
                        labelText = "条件表达式",
                        placeholder = { Text("exitCode == 0  或  \${HOST_PRIVILEGED} == true") },
                        supportingText = {
                            Text("支持 exitCode / output contains / \${VAR} 比较，以及 && ||。成立→exit 0，否则 exit 1。")
                        },
                        minLines = 2,
                        maxLines = 4,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    BranchRouterCard(
                        state = state,
                        node = node,
                        onConnectNodes = onConnectNodes,
                        onRemoveEdge = onRemoveEdge,
                    )
                }

                WorkflowNodeType.PROCESS_SERVICE -> {
                    RuntimeFilledTextField(
                        value = configMap["command"].orEmpty(),
                        onValueChange = { configMap["command"] = it },
                        labelText = "后台服务启动命令（必填）",
                        placeholder = { Text("例如：python3 -m http.server 8080") },
                        supportingText = { Text("将在沙箱后台常驻运行，进程 ID 保存于 \${PROCESS_ID}") },
                        minLines = 2,
                        maxLines = 4,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    RuntimeFilledTextField(
                        value = configMap["workingDirectory"].orEmpty(),
                        onValueChange = { configMap["workingDirectory"] = it },
                        labelText = "工作目录（可选）",
                        placeholder = { Text("留空默认为工程根目录") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                WorkflowNodeType.AGENT_INFERENCE -> WorkflowAgentFields(configMap, delegate = false)
                WorkflowNodeType.SUBAGENT_DELEGATE -> WorkflowAgentFields(configMap, delegate = true)

                WorkflowNodeType.TAIXU_BUILD -> {
                    val currentMode = configMap["mode"] ?: "build"
                    val currentType = configMap["projectType"] ?: "android"
                    val modeOptions = listOf("build" to "标准构建 (build)", "doctor" to "环境体检 (doctor)", "analyze" to "静态分析 (analyze)")
                    val typeOptions = listOf("android", "cmake", "cargo", "gradle")

                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        RuntimeDropdownField(
                            title = "模式",
                            items = modeOptions.map { it.second },
                            selectedIndex = modeOptions.indexOfFirst { it.first == currentMode }.coerceAtLeast(0),
                            onSelectedIndexChange = { index -> modeOptions.getOrNull(index)?.let { configMap["mode"] = it.first } },
                            modifier = Modifier.weight(1f),
                        )
                        RuntimeDropdownField(
                            title = "类型",
                            items = typeOptions,
                            selectedIndex = typeOptions.indexOf(currentType).coerceAtLeast(0),
                            onSelectedIndexChange = { index -> typeOptions.getOrNull(index)?.let { configMap["projectType"] = it } },
                            modifier = Modifier.weight(1f),
                        )
                    }
                    RuntimeFilledTextField(
                        value = configMap["task"] ?: "assembleDebug",
                        onValueChange = { configMap["task"] = it },
                        labelText = "构建 Task 任务",
                        placeholder = { Text("例如：assembleDebug 或 build") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                WorkflowNodeType.HOST_ACTION -> {
                    HostActionConfigFields(configMap = configMap)
                }

                WorkflowNodeType.DELAY -> {
                    WorkflowDelayFields(configMap)
                }

                WorkflowNodeType.SET_VARIABLE -> {
                    RuntimeFilledTextField(
                        value = configMap["variables"].orEmpty(),
                        onValueChange = { configMap["variables"] = it },
                        labelText = "变量赋值（每行 KEY=value）",
                        placeholder = { Text("FOO=bar\nPATH=\${WORKSPACE_PATH}") },
                        supportingText = { Text("支持 \${VAR} / \${previous.output} / \${nodeId.output}") },
                        minLines = 3,
                        maxLines = 8,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                WorkflowNodeType.HUMAN_APPROVAL -> {
                    WorkflowApprovalFields(configMap)
                }

                WorkflowNodeType.TRIGGER -> {
                    Text(
                        text = "工作流启动入口。在顶部点击“运行调试”即可从此处触发整个 DAG 流程。",
                        style = RuntimeTheme.typography.bodySmall,
                        color = RuntimeTheme.colorScheme.onSurfaceVariant,
                    )
                }

                WorkflowNodeType.PLUGIN_ACTION -> PluginNodeConfigFields(configMap)
                WorkflowNodeType.TERMINAL_OUTPUT -> {
                    Text(
                        text = "工作流终点节点。将自动收集并归档上游所有步骤的标准输出、错误日志与产物文件。",
                        style = RuntimeTheme.typography.bodySmall,
                        color = RuntimeTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showAdvancedJson = !showAdvancedJson }
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = if (showAdvancedJson) "收起高级配置 (JSON)" else "展开高级配置 (JSON)",
                    style = RuntimeTheme.typography.labelMedium,
                    color = RuntimeTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f),
                )
                RuntimeIcon(
                    name = if (showAdvancedJson) RuntimeIconName.ChevronUp else RuntimeIconName.ChevronDown,
                    modifier = Modifier.size(16.dp),
                    tint = RuntimeTheme.colorScheme.primary,
                )
            }
            if (showAdvancedJson) {
                RuntimeFilledTextField(
                    value = rawJsonText,
                    onValueChange = {
                        rawJsonText = it
                        parsedConfig?.let { parsed ->
                            configMap.clear()
                            configMap.putAll(parsed)
                        }
                    },
                    labelText = "原始 JSON 配置",
                    isError = parsedConfig == null,
                    minLines = 3,
                    maxLines = 6,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            WorkflowNodePolicyFields(node.type, timeout, { timeout = it }, policy, { policy = it })

            top.wkbin.taixu.ui.components.RuntimeActionRow(modifier = Modifier.fillMaxWidth()) {
                RuntimeOutlinedButton(
                    onClick = if (isConnecting) onCancelConnection else onBeginConnection,
                    modifier = Modifier,
                ) {
                    Text(if (isConnecting) "取消连线" else "在画布连线…", maxLines = 1)
                }
                RuntimeButton(
                    onClick = {
                        val finalConfig = if (showAdvancedJson) parsedConfig ?: return@RuntimeButton else configMap.toMap()
                        val timeoutValue = if (node.type == WorkflowNodeType.HUMAN_APPROVAL) node.timeoutSeconds
                            else timeout.toIntOrNull()?.takeIf { it in 1..3600 } ?: return@RuntimeButton
                        onApply(
                            node.copy(
                                title = title.trim().ifBlank { node.title },
                                description = description.trim(),
                                timeoutSeconds = timeoutValue,
                                config = finalConfig,
                                failurePolicy = policy,
                            ),
                        )
                    },
                    enabled = (node.type == WorkflowNodeType.HUMAN_APPROVAL || timeout.toIntOrNull()?.let { it in 1..3600 } == true) &&
                        (!showAdvancedJson || parsedConfig != null),
                    modifier = Modifier,
                ) {
                    Text("应用参数更改", maxLines = 1)
                }
            }
        }
    }
}

@Composable
private fun HostActionConfigFields(configMap: MutableMap<String, String>) {
    val grouped = remember { HostWorkflowActions.grouped() }
    val currentActionId = configMap["action"] ?: "status"
    val currentDef = HostWorkflowActions.find(currentActionId)
    val currentCategory = currentDef?.category ?: grouped.keys.firstOrNull().orEmpty()
    val categories = grouped.keys.toList()
    val actions = grouped[currentCategory].orEmpty()

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            RuntimeDropdownField(
                title = "分类",
                items = categories,
                selectedIndex = categories.indexOf(currentCategory).coerceAtLeast(0),
                onSelectedIndexChange = { index ->
                    categories.getOrNull(index)?.let { category ->
                        grouped[category]?.firstOrNull()?.let { first -> configMap["action"] = first.id }
                    }
                },
                modifier = Modifier.weight(1f),
            )
            RuntimeDropdownField(
                title = "动作",
                items = actions.map { action ->
                    buildString {
                        append(action.label)
                        if (action.privilege == HostWorkflowPrivilege.PRIVILEGED) append(" · 需特权")
                    }
                },
                selectedIndex = actions.indexOfFirst { it.id == currentActionId }.coerceAtLeast(0),
                onSelectedIndexChange = { index -> actions.getOrNull(index)?.let { configMap["action"] = it.id } },
                enabled = actions.isNotEmpty(),
                modifier = Modifier.weight(1.4f),
            )
        }

        currentDef?.let { def ->
            Text(
                text = def.description + if (def.privilege == HostWorkflowPrivilege.PRIVILEGED) {
                    "\n⚠ 需要 Shizuku 或 Root 生效。"
                } else "",
                style = RuntimeTheme.typography.bodySmall,
                color = RuntimeTheme.colorScheme.onSurfaceVariant,
            )
            def.fields.forEach { field ->
                RuntimeFilledTextField(
                    value = configMap[field.key].orEmpty(),
                    onValueChange = { configMap[field.key] = it },
                    labelText = if (field.required) "${field.label} *" else field.label,
                    placeholder = { Text(field.hint) },
                    singleLine = !field.multiline,
                    minLines = if (field.multiline) 3 else 1,
                    maxLines = if (field.multiline) 6 else 1,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        } ?: RuntimeFilledTextField(
            value = configMap["command"].orEmpty(),
            onValueChange = { configMap["command"] = it },
            labelText = "自定义宿主命令（逃逸舱）",
            supportingText = { Text("未知 action 时将按特权 shell 执行此命令") },
            minLines = 2,
            maxLines = 4,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun BranchRouterCard(
    state: WorkflowEditorUiState,
    node: WorkflowNode,
    onConnectNodes: (String, String, String, String?) -> Unit,
    onRemoveEdge: (String) -> Unit,
) {
    val branchEdges = state.definition.edges.filter { it.fromNodeId == node.id }
    val otherNodes = state.definition.nodes.filter { it.id != node.id }

    val successEdge = branchEdges.firstOrNull { it.conditionExpression?.contains("exitCode == 0") == true || it.fromPort == "success" }
    val failureEdge = branchEdges.firstOrNull { it.conditionExpression?.contains("exitCode !=") == true || it.fromPort == "failure" }

    var successTargetId by remember(otherNodes) { mutableStateOf(otherNodes.firstOrNull()?.id.orEmpty()) }
    var failureTargetId by remember(otherNodes) { mutableStateOf(otherNodes.getOrNull(1)?.id ?: otherNodes.firstOrNull()?.id.orEmpty()) }

    Surface(
        color = RuntimeTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.55f),
        shape = RuntimeTheme.shapes.small,
        border = BorderStroke(1.dp, Color(0xFFEAB308).copy(alpha = 0.4f)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                RuntimeIcon(RuntimeIconName.Link, Modifier.size(16.dp), tint = Color(0xFFEAB308))
                Text("可视化条件分支路由器", style = RuntimeTheme.typography.titleSmall, fontWeight = FontWeight.Medium, color = Color(0xFFEAB308))
            }

            Surface(
                color = RuntimeTheme.colorScheme.surfaceContainerLow,
                shape = RuntimeTheme.shapes.chip,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = "💡 节点会求值上方表达式：成立 exitCode=0，不成立 exitCode=1（节点本身仍成功，便于分流）。\n" +
                        "下面可快速把成功/失败分支连到目标节点。",
                    style = RuntimeTheme.typography.bodySmall,
                    color = RuntimeTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(8.dp),
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Surface(shape = CircleShape, color = top.wkbin.taixu.ui.theme.semanticColors().success.copy(alpha = 0.18f)) {
                    Text("✔ 成功分支 (exitCode == 0)", style = RuntimeTheme.typography.labelSmall, color = top.wkbin.taixu.ui.theme.semanticColors().success, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                }
                if (successEdge != null) {
                    val target = state.definition.nodes.firstOrNull { it.id == successEdge.toNodeId }
                    Row(
                        modifier = Modifier.fillMaxWidth().background(RuntimeTheme.colorScheme.surfaceContainerHigh, RuntimeTheme.shapes.chip).padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text("已连接至: ${target?.title ?: successEdge.toNodeId}", style = RuntimeTheme.typography.labelMedium, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                        RuntimeOutlinedButton(
                            onClick = { onRemoveEdge(successEdge.id) },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                        ) {
                            Text("断开", style = RuntimeTheme.typography.labelSmall, color = RuntimeTheme.colorScheme.error)
                        }
                    }
                } else if (otherNodes.isNotEmpty()) {
                    val currentSuccessNode = otherNodes.firstOrNull { it.id == successTargetId } ?: otherNodes.first()
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        RuntimeDropdownField(
                            title = "成功目标",
                            items = otherNodes.map { it.title },
                            selectedIndex = otherNodes.indexOfFirst { it.id == currentSuccessNode.id }.coerceAtLeast(0),
                            onSelectedIndexChange = { index -> otherNodes.getOrNull(index)?.let { successTargetId = it.id } },
                            modifier = Modifier.weight(1f),
                        )
                        RuntimeButton(
                            onClick = { onConnectNodes(node.id, currentSuccessNode.id, "output", "exitCode == 0") },
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        ) {
                            Text("连接", maxLines = 1)
                        }
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Surface(shape = CircleShape, color = Color(0xFFF43F5E).copy(alpha = 0.18f)) {
                    Text("✘ 失败分支 (exitCode != 0)", style = RuntimeTheme.typography.labelSmall, color = Color(0xFFF43F5E), modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                }
                if (failureEdge != null) {
                    val target = state.definition.nodes.firstOrNull { it.id == failureEdge.toNodeId }
                    Row(
                        modifier = Modifier.fillMaxWidth().background(RuntimeTheme.colorScheme.surfaceContainerHigh, RuntimeTheme.shapes.chip).padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text("已连接至: ${target?.title ?: failureEdge.toNodeId}", style = RuntimeTheme.typography.labelMedium, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                        RuntimeOutlinedButton(
                            onClick = { onRemoveEdge(failureEdge.id) },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                        ) {
                            Text("断开", style = RuntimeTheme.typography.labelSmall, color = RuntimeTheme.colorScheme.error)
                        }
                    }
                } else if (otherNodes.isNotEmpty()) {
                    val currentFailureNode = otherNodes.firstOrNull { it.id == failureTargetId } ?: otherNodes.first()
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        RuntimeDropdownField(
                            title = "失败目标",
                            items = otherNodes.map { it.title },
                            selectedIndex = otherNodes.indexOfFirst { it.id == currentFailureNode.id }.coerceAtLeast(0),
                            onSelectedIndexChange = { index -> otherNodes.getOrNull(index)?.let { failureTargetId = it.id } },
                            modifier = Modifier.weight(1f),
                        )
                        RuntimeButton(
                            onClick = { onConnectNodes(node.id, currentFailureNode.id, "output", "exitCode != 0") },
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        ) {
                            Text("连接", maxLines = 1)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun NodeConnectionsCard(
    state: WorkflowEditorUiState,
    node: WorkflowNode,
    onConnectNodes: (String, String, String, String?) -> Unit,
    onDisconnectAll: (String) -> Unit,
    onUpdateEdge: (WorkflowEdge) -> Unit,
    onRemoveEdge: (String) -> Unit,
) {
    val outgoingEdges = state.definition.edges.filter { it.fromNodeId == node.id }
    val incomingEdges = state.definition.edges.filter { it.toNodeId == node.id }
    val allConnected = outgoingEdges + incomingEdges

    val otherNodes = remember(state.definition.nodes, node.id) {
        state.definition.nodes.filter { it.id != node.id }
    }

    var selectedTargetId by remember(otherNodes) { mutableStateOf(otherNodes.firstOrNull()?.id.orEmpty()) }
    var selectedPort by remember { mutableStateOf("output") }
    var conditionText by remember { mutableStateOf("") }

    RuntimeCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(16.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                RuntimeIcon(RuntimeIconName.Link, Modifier.size(16.dp), tint = RuntimeTheme.colorScheme.primary)
                Spacer(Modifier.width(6.dp))
                Text("节点连线与拓扑 (${allConnected.size})", style = RuntimeTheme.typography.titleSmall, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                if (allConnected.isNotEmpty()) {
                    Surface(
                        onClick = { onDisconnectAll(node.id) },
                        shape = CircleShape,
                        color = RuntimeTheme.colorScheme.error.copy(alpha = 0.14f),
                    ) {
                        Text(
                            text = "一键断开全部",
                            style = RuntimeTheme.typography.labelSmall,
                            color = RuntimeTheme.colorScheme.error,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        )
                    }
                }
            }

            Text("传出连线（连向后续节点）：", style = RuntimeTheme.typography.labelMedium, fontWeight = FontWeight.Medium)
            if (outgoingEdges.isEmpty()) {
                Text("暂无传出连线", style = RuntimeTheme.typography.bodySmall, color = RuntimeTheme.colorScheme.onSurfaceVariant)
            } else {
                outgoingEdges.forEach { edge ->
                    val targetNode = state.definition.nodes.firstOrNull { it.id == edge.toNodeId }
                    Surface(
                        shape = RuntimeTheme.shapes.chip,
                        color = RuntimeTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.6f),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Column(Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Surface(
                                        color = RuntimeTheme.colorScheme.primary.copy(alpha = 0.15f),
                                        shape = CircleShape,
                                    ) {
                                        Text(
                                            text = edge.fromPort.portLabel(),
                                            style = RuntimeTheme.typography.labelSmall,
                                            color = RuntimeTheme.colorScheme.primary,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                        )
                                    }
                                    Text("──►", style = RuntimeTheme.typography.labelSmall, color = RuntimeTheme.colorScheme.onSurfaceVariant)
                                    Text(
                                        text = targetNode?.title ?: edge.toNodeId,
                                        style = RuntimeTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Medium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                                if (!edge.conditionExpression.isNullOrBlank()) {
                                    Text(
                                        text = "条件: ${edge.conditionExpression}",
                                        style = RuntimeTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                                        color = RuntimeTheme.colorScheme.tertiary,
                                        maxLines = 1,
                                    )
                                }
                            }
                            RuntimeOutlinedButton(
                                onClick = { onRemoveEdge(edge.id) },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                            ) {
                                RuntimeIcon(RuntimeIconName.Close, Modifier.size(12.dp), tint = RuntimeTheme.colorScheme.error)
                                Text("断开", style = RuntimeTheme.typography.labelSmall, color = RuntimeTheme.colorScheme.error)
                            }
                        }
                    }
                }
            }

            Text("传入连线（来自前序节点）：", style = RuntimeTheme.typography.labelMedium, fontWeight = FontWeight.Medium)
            if (incomingEdges.isEmpty()) {
                Text("暂无传入连线", style = RuntimeTheme.typography.bodySmall, color = RuntimeTheme.colorScheme.onSurfaceVariant)
            } else {
                incomingEdges.forEach { edge ->
                    val sourceNode = state.definition.nodes.firstOrNull { it.id == edge.fromNodeId }
                    Surface(
                        shape = RuntimeTheme.shapes.chip,
                        color = RuntimeTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.6f),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Column(Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text(
                                        text = sourceNode?.title ?: edge.fromNodeId,
                                        style = RuntimeTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Medium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Text("──►", style = RuntimeTheme.typography.labelSmall, color = RuntimeTheme.colorScheme.onSurfaceVariant)
                                    Text("本节点", style = RuntimeTheme.typography.labelSmall, color = RuntimeTheme.colorScheme.primary)
                                }
                                if (!edge.conditionExpression.isNullOrBlank()) {
                                    Text(
                                        text = "条件: ${edge.conditionExpression}",
                                        style = RuntimeTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                                        color = RuntimeTheme.colorScheme.tertiary,
                                        maxLines = 1,
                                    )
                                }
                            }
                            RuntimeOutlinedButton(
                                onClick = { onRemoveEdge(edge.id) },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                            ) {
                                RuntimeIcon(RuntimeIconName.Close, Modifier.size(12.dp), tint = RuntimeTheme.colorScheme.error)
                                Text("断开", style = RuntimeTheme.typography.labelSmall, color = RuntimeTheme.colorScheme.error)
                            }
                        }
                    }
                }
            }

            HorizontalDivider()

            Text("➕ 添加连线到其他节点：", style = RuntimeTheme.typography.labelMedium, fontWeight = FontWeight.Medium)
            if (otherNodes.isEmpty()) {
                Text("画布中尚无其他可用节点", style = RuntimeTheme.typography.bodySmall, color = RuntimeTheme.colorScheme.onSurfaceVariant)
            } else {
                val currentTargetNode = otherNodes.firstOrNull { it.id == selectedTargetId } ?: otherNodes.first()
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    RuntimeDropdownField(
                        title = "目标节点",
                        items = otherNodes.map { "${it.title} (${it.type.editorLabel()})" },
                        selectedIndex = otherNodes.indexOfFirst { it.id == currentTargetNode.id }.coerceAtLeast(0),
                        onSelectedIndexChange = { index -> otherNodes.getOrNull(index)?.let { selectedTargetId = it.id } },
                        modifier = Modifier.fillMaxWidth(),
                    )

                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        RuntimeDropdownField(
                            title = "端口",
                            items = EdgePorts.map { it.portLabel() },
                            selectedIndex = EdgePorts.indexOf(selectedPort).coerceAtLeast(0),
                            onSelectedIndexChange = { index -> EdgePorts.getOrNull(index)?.let { selectedPort = it } },
                            modifier = Modifier.weight(1f),
                        )
                        RuntimeFilledTextField(
                            value = conditionText,
                            onValueChange = { conditionText = it },
                            labelText = "条件（可选）",
                            placeholder = { Text("如 exitCode == 0") },
                            singleLine = true,
                            modifier = Modifier.weight(1.5f),
                        )
                    }

                    RuntimeButton(
                        onClick = {
                            onConnectNodes(node.id, currentTargetNode.id, selectedPort, conditionText.trim().ifBlank { null })
                            conditionText = ""
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        RuntimeIcon(RuntimeIconName.Plus, Modifier.size(16.dp))
                        Text("立即建立连线", maxLines = 1)
                    }
                }
            }
        }
    }
}

private fun WorkflowNodeType.editorLabel() = metadata().label

private fun String.portLabel() = when (lowercase()) {
    "success" -> "成功 (success)"
    "failure" -> "失败 (failure)"
    else -> "任意输出 (output)"
}

private val EdgePorts = listOf("output", "success", "failure")
