package top.wkbin.taixu.ui.chat

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import top.wkbin.taixu.ui.components.AppNavigationSection
import top.wkbin.taixu.ui.components.MainDestination

/**
 * 左右联动滑动的侧边抽屉容器（Push Drawer）：
 * - 侧边栏与主内容处于同一 Window 坐标系，无覆盖式 Dialog。
 * - 抽屉展开时，主内容整体向右平移（露出右侧边缘），支持全手势左右滑动。
 */
@Composable
fun ChatNavigationDrawer(
    viewModel: ChatViewModel,
    visible: Boolean,
    onDismiss: () -> Unit,
    onOpen: (() -> Unit)? = null,
    newSessionVisible: Boolean,
    selected: MainDestination,
    onNewSession: () -> Unit,
    onDismissNewSession: () -> Unit,
    onNavigate: (MainDestination) -> Unit,
    onOpenTerminal: () -> Unit,
    onOpenTools: () -> Unit,
    content: @Composable () -> Unit,
) {
    val sessions by viewModel.sessions.collectAsStateWithLifecycle()
    val catalog by viewModel.workspaceCatalog.collectAsStateWithLifecycle()
    val currentSessionId by viewModel.currentSessionId.collectAsStateWithLifecycle()
    val runStates by viewModel.sessionRunStates.collectAsStateWithLifecycle()

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val drawerWidth = min(maxWidth * 0.78f, 320.dp)
        val drawerWidthPx = with(density) { drawerWidth.toPx() }

        val coroutineScope = rememberCoroutineScope()
        val offsetAnim = remember { Animatable(0f) }

        // 同步外部状态（如点击汉堡图标触发 visible 变化）
        LaunchedEffect(visible, drawerWidthPx) {
            val target = if (visible) drawerWidthPx else 0f
            if (offsetAnim.value != target) {
                offsetAnim.animateTo(
                    targetValue = target,
                    animationSpec = spring(
                        stiffness = Spring.StiffnessMediumLow,
                        dampingRatio = Spring.DampingRatioNoBouncy,
                    ),
                )
            }
        }

        val currentOffset = offsetAnim.value
        val progress = if (drawerWidthPx > 0f) (currentOffset / drawerWidthPx).coerceIn(0f, 1f) else 0f
        val isOpen = progress > 0.01f

        // 返回键拦截：展开时按下返回键平滑收起
        BackHandler(enabled = visible || isOpen) {
            coroutineScope.launch {
                offsetAnim.animateTo(0f, spring(stiffness = Spring.StiffnessMediumLow))
                onDismiss()
            }
        }

        // 左侧抽屉视图（伴随视差平移渐显）
        if (progress > 0.001f) {
            val parallaxOffset = (progress - 1f) * drawerWidthPx * 0.22f
            Box(
                modifier = Modifier
                    .width(drawerWidth)
                    .fillMaxHeight()
                    .graphicsLayer {
                        translationX = parallaxOffset
                        alpha = 0.5f + 0.5f * progress
                    }
                    .pointerInput(drawerWidthPx) {
                        detectHorizontalDragGestures(
                            onDragEnd = {
                                val shouldClose = offsetAnim.value < drawerWidthPx * 0.65f
                                val target = if (shouldClose) 0f else drawerWidthPx
                                coroutineScope.launch {
                                    offsetAnim.animateTo(target, spring(stiffness = Spring.StiffnessMediumLow))
                                    if (target == 0f) onDismiss()
                                }
                            },
                            onHorizontalDrag = { change, dragAmount ->
                                change.consume()
                                coroutineScope.launch {
                                    val newOffset = (offsetAnim.value + dragAmount).coerceIn(0f, drawerWidthPx)
                                    offsetAnim.snapTo(newOffset)
                                }
                            }
                        )
                    }
            ) {
                SessionsSideDrawer(
                    visible = true,
                    sessions = sessions,
                    currentSessionId = currentSessionId,
                    workspaces = catalog.projects,
                    sessionRunStates = runStates,
                    onDismiss = onDismiss,
                    onSwitch = { id -> viewModel.switchSession(id); onNavigate(MainDestination.Agent); onDismiss() },
                    onNew = { onDismiss(); onNewSession() },
                    onCreateInWorkspace = { project ->
                        viewModel.createSession("", project.linuxPath, project.projectType.name)
                        onNavigate(MainDestination.Agent); onDismiss()
                    },
                    onDelete = viewModel::deleteSession,
                    onRename = viewModel::renameSession,
                    navigationContent = {
                        AppNavigationSection(
                            selected = selected,
                            onNavigate = { destination -> onDismiss(); onNavigate(destination) },
                            onOpenTerminal = { onDismiss(); onOpenTerminal() },
                            onOpenTools = { onDismiss(); onOpenTools() },
                        )
                    },
                )
            }
        }

        // 右侧主内容（向右推移，露出右侧边缘，带轻微圆角与阴影遮罩）
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    translationX = currentOffset
                    shape = RoundedCornerShape(
                        topStart = (18f * progress).dp,
                        bottomStart = (18f * progress).dp,
                    )
                    clip = progress > 0.01f
                    shadowElevation = (16f * progress).dp.toPx()
                }
                .background(MaterialTheme.colorScheme.background)
        ) {
            content()

            // 展开时的半透明遮罩与点击/滑动返回拦截
            if (progress > 0.001f) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.22f * progress))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            enabled = progress > 0.05f,
                            onClick = onDismiss,
                        )
                        .pointerInput(drawerWidthPx) {
                            detectHorizontalDragGestures(
                                onDragEnd = {
                                    val shouldClose = offsetAnim.value < drawerWidthPx * 0.65f
                                    val target = if (shouldClose) 0f else drawerWidthPx
                                    coroutineScope.launch {
                                        offsetAnim.animateTo(target, spring(stiffness = Spring.StiffnessMediumLow))
                                        if (target == 0f) onDismiss()
                                    }
                                },
                                onHorizontalDrag = { change, dragAmount ->
                                    change.consume()
                                    coroutineScope.launch {
                                        val newOffset = (offsetAnim.value + dragAmount).coerceIn(0f, drawerWidthPx)
                                        offsetAnim.snapTo(newOffset)
                                    }
                                }
                            )
                        }
                )
            }
        }

        // 关闭状态下：屏幕最左侧轻微边缘检测区（支持从左边缘向右滑动拉出抽屉）
        if (!isOpen && !visible) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(32.dp)
                    .align(Alignment.CenterStart)
                    .pointerInput(drawerWidthPx) {
                        detectHorizontalDragGestures(
                            onDragEnd = {
                                val shouldOpen = offsetAnim.value > drawerWidthPx * 0.35f
                                val target = if (shouldOpen) drawerWidthPx else 0f
                                coroutineScope.launch {
                                    offsetAnim.animateTo(target, spring(stiffness = Spring.StiffnessMediumLow))
                                    if (target > 0f) onOpen?.invoke()
                                }
                            },
                            onHorizontalDrag = { change, dragAmount ->
                                change.consume()
                                coroutineScope.launch {
                                    val newOffset = (offsetAnim.value + dragAmount).coerceIn(0f, drawerWidthPx)
                                    offsetAnim.snapTo(newOffset)
                                }
                            }
                        )
                    }
            )
        }
    }

    if (newSessionVisible) {
        NewSessionDialog(
            catalog.projects,
            catalog,
            onDismiss = onDismissNewSession,
            onCreate = { title, workspace, type ->
                viewModel.createSession(title, workspace, type.name)
                onDismissNewSession()
                onNavigate(MainDestination.Agent)
            }
        )
    }
}
