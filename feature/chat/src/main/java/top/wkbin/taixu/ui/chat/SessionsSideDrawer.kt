package top.wkbin.taixu.ui.chat

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import top.wkbin.taixu.ui.theme.LocalLiquidGlassSurfaceBackdrop
import top.wkbin.taixu.ui.components.RuntimeDialogSystemBars
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import top.wkbin.taixu.core.database.HarnessSessionEntity
import top.wkbin.taixu.core.model.SessionRunState
import top.wkbin.taixu.feature.chat.R
import top.wkbin.taixu.runtime.ProjectType
import top.wkbin.taixu.runtime.WorkspaceProject
import top.wkbin.taixu.ui.components.RuntimeAlertDialog
import top.wkbin.taixu.ui.components.RuntimeButton
import top.wkbin.taixu.ui.components.RuntimeFilledTonalButton
import top.wkbin.taixu.ui.components.RuntimeIcon
import top.wkbin.taixu.ui.components.RuntimeIconButton
import top.wkbin.taixu.ui.components.TaiXuBrandBadge
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import top.wkbin.taixu.ui.components.RuntimeIconName
import top.wkbin.taixu.ui.components.RuntimeTextButton

/** 按项目划分的会话组数据结构。 */
data class ProjectSessionGroup(
    val projectName: String,
    val workspacePath: String,
    val projectType: ProjectType,
    val sessions: List<HarnessSessionEntity>,
)

/** 会话分区结果：项目分组与未关联的最近会话。 */
data class SessionPartitionResult(
    val projectGroups: List<ProjectSessionGroup>,
    val recentSessions: List<HarnessSessionEntity>,
)

/**
 * 将全部会话与工作区进行归集与分区：
 * 1. 关联了工作区/项目的会话归类入对应项目分组（按 updatedAt 倒序）。
 * 2. 未关联工作区的纯沙箱会话归入「最近」（按 updatedAt 倒序）。
 */
fun partitionSessions(
    sessions: List<HarnessSessionEntity>,
    workspaces: List<WorkspaceProject>,
): SessionPartitionResult {
    val (withWs, withoutWs) = sessions.partition { it.workspace.isNotBlank() }
    val recentSessions = withoutWs.sortedByDescending { it.updatedAt }

    val sessionsByWs = withWs.groupBy { it.workspace.trimEnd('/') }
    val handledWsPaths = mutableSetOf<String>()
    val projectGroups = mutableListOf<ProjectSessionGroup>()

    for (ws in workspaces) {
        val normPath = ws.linuxPath.trimEnd('/')
        handledWsPaths.add(normPath)
        val projSessions = sessionsByWs[normPath].orEmpty().sortedByDescending { it.updatedAt }
        projectGroups.add(
            ProjectSessionGroup(
                projectName = ws.name,
                workspacePath = ws.linuxPath,
                projectType = ws.projectType,
                sessions = projSessions,
            ),
        )
    }

    // 容错：处理工作区已被删除但仍保留 workspace 路径的历史孤立会话
    for ((wsPath, orphanSessions) in sessionsByWs) {
        if (!handledWsPaths.contains(wsPath)) {
            val extractedName = wsPath.substringAfterLast('/').ifBlank { wsPath }
            projectGroups.add(
                ProjectSessionGroup(
                    projectName = extractedName,
                    workspacePath = wsPath,
                    projectType = ProjectType.GENERAL,
                    sessions = orphanSessions.sortedByDescending { it.updatedAt },
                ),
            )
        }
    }

    val sortedProjectGroups = projectGroups.sortedWith(
        compareByDescending<ProjectSessionGroup> { it.sessions.firstOrNull()?.updatedAt ?: 0L }
            .thenBy { it.projectName.lowercase() },
    )

    return SessionPartitionResult(
        projectGroups = sortedProjectGroups,
        recentSessions = recentSessions,
    )
}

@Composable
private fun formatSessionTime(timestamp: Long): String {
    if (timestamp <= 0L) return ""
    val now = System.currentTimeMillis()
    val diff = now - timestamp
    return when {
        diff < 60_000L -> stringResource(R.string.chat_time_just_now)
        diff < 3600_000L -> stringResource(R.string.chat_time_minutes_ago, (diff / 60_000L).coerceAtLeast(1))
        diff < 86400_000L -> stringResource(R.string.chat_time_hours_ago, (diff / 3600_000L).coerceAtLeast(1))
        diff < 86400_000L * 2 -> stringResource(R.string.chat_time_yesterday)
        diff < 86400_000L * 7 -> stringResource(R.string.chat_time_days_ago, (diff / 86400_000L).coerceAtLeast(1))
        else -> SimpleDateFormat("MM-dd", Locale.getDefault()).format(Date(timestamp))
    }
}

/**
 * 侧边弹窗形式的会话管理与新建抽屉（参考千问办公侧边栏风格）：
 * - 屏幕左侧展开，含背景遮罩与进退场滑动动画。
 * - 顶部置顶醒目「新任务 / 新建会话」大按钮与快捷扩展入口。
 * - 「项目」分组展示关联工作区工程，支持展开/折叠与单项目一键「+」新建会话。
 * - 「最近」分组展示未关联工作区的会话，按最近修改时间倒序排列。
 */
@Composable
internal fun SessionsSideDrawer(
    visible: Boolean,
    sessions: List<HarnessSessionEntity>,
    currentSessionId: String,
    workspaces: List<WorkspaceProject>,
    sessionRunStates: Map<String, SessionRunState>,
    onDismiss: () -> Unit,
    onSwitch: (String) -> Unit,
    onNew: () -> Unit,
    onCreateInWorkspace: (WorkspaceProject) -> Unit,
    onDelete: (String) -> Unit,
    onRename: (String, String) -> Unit,
    navigationContent: @Composable () -> Unit = {},
) {
    if (!visible) return

    val coroutineScope = rememberCoroutineScope()
    var isVisible by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        isVisible = true
    }

    val handleDismiss: () -> Unit = {
        coroutineScope.launch {
            isVisible = false
            delay(180)
            onDismiss()
        }
    }

    // 重命名与删除二次确认状态
    var renameTargetId by rememberSaveable { mutableStateOf<String?>(null) }
    var deleteTargetId by rememberSaveable { mutableStateOf<String?>(null) }

    val partition = remember(sessions, workspaces) { partitionSessions(sessions, workspaces) }
    var expandedProjects by rememberSaveable {
        val initialExpanded = partition.projectGroups
            .filter { group -> group.sessions.any { it.id == currentSessionId } }
            .map { it.workspacePath }
            .toSet()
        mutableStateOf(initialExpanded)
    }

    Dialog(
        onDismissRequest = handleDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
        ),
    ) {
        RuntimeDialogSystemBars()
        BackHandler { handleDismiss() }

        Box(modifier = Modifier.fillMaxSize()) {
            AnimatedVisibility(
                visible = isVisible,
                enter = fadeIn(tween(180)),
                exit = fadeOut(tween(160)),
                modifier = Modifier.fillMaxSize(),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.24f))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = handleDismiss,
                        ),
                )
            }

            AnimatedVisibility(
                visible = isVisible,
                enter = slideInHorizontally(
                    initialOffsetX = { -it },
                    animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                ),
                exit = slideOutHorizontally(
                    targetOffsetX = { -it },
                    animationSpec = tween(180),
                ),
                modifier = Modifier
                    .fillMaxHeight()
                    .align(Alignment.CenterStart),
            ) {
                CompositionLocalProvider(LocalLiquidGlassSurfaceBackdrop provides null) {
                Surface(
                    modifier = Modifier
                        .fillMaxHeight()
                        .widthIn(max = 360.dp)
                        .fillMaxWidth(0.88f),
                    shape = RoundedCornerShape(topEnd = 28.dp, bottomEnd = 28.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 1f),
                    tonalElevation = 0.dp,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .statusBarsPadding()
                            .navigationBarsPadding(),
                    ) {
                        DrawerHeader(
                            totalSessionsCount = sessions.size,
                            onClose = handleDismiss,
                        )

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp, vertical = 8.dp),
                        ) {
                            RuntimeButton(
                                onClick = {
                                    handleDismiss()
                                    onNew()
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp),
                                shape = MaterialTheme.shapes.large,
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    RuntimeIcon(
                                        name = RuntimeIconName.Edit,
                                        modifier = Modifier.size(17.dp),
                                        tint = MaterialTheme.colorScheme.onPrimary,
                                    )
                                    Text(
                                        text = stringResource(R.string.chat_drawer_new_task),
                                        style = MaterialTheme.typography.labelLarge,
                                    )
                                }
                            }
                        }

                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
                        )

                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .verticalScroll(rememberScrollState())
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(24.dp),
                        ) {
                            navigationContent()
                            ProjectSection(
                                projectGroups = partition.projectGroups,
                                currentSessionId = currentSessionId,
                                sessionRunStates = sessionRunStates,
                                expandedProjects = expandedProjects,
                                onToggleExpand = { path ->
                                    expandedProjects = if (expandedProjects.contains(path)) {
                                        expandedProjects - path
                                    } else {
                                        expandedProjects + path
                                    }
                                },
                                onSwitch = { id ->
                                    handleDismiss()
                                    onSwitch(id)
                                },
                                onCreateInProject = { group ->
                                    val matchedWs = workspaces.firstOrNull { it.linuxPath == group.workspacePath }
                                        ?: WorkspaceProject(
                                            name = group.projectName,
                                            path = group.workspacePath,
                                            linuxPath = group.workspacePath,
                                            sizeBytes = 0L,
                                            projectType = group.projectType,
                                        )
                                    handleDismiss()
                                    onCreateInWorkspace(matchedWs)
                                },
                                onRename = { id -> renameTargetId = id },
                                onDelete = { id -> deleteTargetId = id },
                            )

                            RecentSection(
                                recentSessions = partition.recentSessions,
                                currentSessionId = currentSessionId,
                                sessionRunStates = sessionRunStates,
                                onSwitch = { id ->
                                    handleDismiss()
                                    onSwitch(id)
                                },
                                onRename = { id -> renameTargetId = id },
                                onDelete = { id -> deleteTargetId = id },
                            )

                            Spacer(modifier = Modifier.height(16.dp))
                        }
                    }
                }
                }
            }
        }
    }

    // 重命名会话弹窗
    val renameTarget = renameTargetId?.let { id -> sessions.firstOrNull { it.id == id } }
    renameTarget?.let { target ->
        RenameSessionDialog(
            currentTitle = target.title,
            onDismiss = { renameTargetId = null },
            onRename = { newTitle ->
                onRename(target.id, newTitle)
                renameTargetId = null
            },
        )
    }

    // 删除会话二次确认弹窗
    deleteTargetId?.let { targetId ->
        RuntimeAlertDialog(
            onDismissRequest = { deleteTargetId = null },
            title = { Text(stringResource(R.string.chat_delete_session_title), fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        stringResource(R.string.chat_delete_session_message),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            confirmButton = {
                RuntimeButton(
                    onClick = {
                        deleteTargetId = null
                        onDelete(targetId)
                    },
                ) {
                    Text(stringResource(R.string.chat_confirm_delete), color = MaterialTheme.colorScheme.onError)
                }
            },
            dismissButton = {
                RuntimeTextButton(onClick = { deleteTargetId = null }) {
                    Text(stringResource(R.string.chat_cancel))
                }
            },
        )
    }
}

/** 抽屉顶部标题行。 */
@Composable
private fun DrawerHeader(totalSessionsCount: Int, onClose: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(start = 24.dp, end = 12.dp, top = 16.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        TaiXuBrandBadge(size = 28.dp)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(stringResource(R.string.chat_title), style = MaterialTheme.typography.titleLarge,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(stringResource(R.string.chat_session_count, totalSessionsCount),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        RuntimeIconButton(onClick = onClose, modifier = Modifier.size(48.dp),
            contentDescription = stringResource(R.string.chat_cancel)) {
            RuntimeIcon(RuntimeIconName.Close, Modifier.size(20.dp), MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
/** 项目分区。 */
@Composable
private fun ProjectSection(
    projectGroups: List<ProjectSessionGroup>,
    currentSessionId: String,
    sessionRunStates: Map<String, SessionRunState>,
    expandedProjects: Set<String>,
    onToggleExpand: (String) -> Unit,
    onSwitch: (String) -> Unit,
    onCreateInProject: (ProjectSessionGroup) -> Unit,
    onRename: (String) -> Unit,
    onDelete: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 6.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = stringResource(R.string.chat_drawer_projects),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = "${projectGroups.size}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline,
            )
        }

        if (projectGroups.isEmpty()) {
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainer,
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = stringResource(R.string.chat_drawer_no_projects),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    modifier = Modifier.padding(10.dp),
                )
            }
        } else {
            projectGroups.forEach { group ->
                val isExpanded = expandedProjects.contains(group.workspacePath)
                Column(
                    modifier = Modifier
                        .fillMaxWidth(),
                ) {
                    // 项目文件夹行
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { onToggleExpand(group.workspacePath) }
                            .padding(start = 8.dp, end = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        RuntimeIcon(
                            name = if (isExpanded) RuntimeIconName.FolderOpen
                            else RuntimeIconName.Folder,
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                        Text(
                            text = group.projectName,
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        if (group.sessions.isNotEmpty()) {
                            Text(
                                text = "${group.sessions.size}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.outline,
                            )
                        }

                        // 单项目快捷新建会话入口
                        RuntimeIconButton(
                            onClick = { onCreateInProject(group) },
                            modifier = Modifier.size(48.dp), contentDescription = stringResource(R.string.chat_drawer_new_task),
                        ) {
                            RuntimeIcon(
                                name = RuntimeIconName.Plus,
                                modifier = Modifier.size(13.dp),
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        }

                        RuntimeIcon(
                            name = if (isExpanded) RuntimeIconName.ChevronDown
                            else RuntimeIconName.ChevronRight,
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    // 展开的会话子项
                    if (isExpanded) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 12.dp, end = 4.dp, bottom = 4.dp),
                            verticalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            if (group.sessions.isEmpty()) {
                                Text(
                                    text = stringResource(R.string.chat_drawer_no_sessions_in_project),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                                )
                            } else {
                                group.sessions.forEach { session ->
                                    SessionDrawerItem(
                                        session = session,
                                        isCurrent = session.id == currentSessionId,
                                        runState = sessionRunStates[session.id] ?: SessionRunState.IDLE,
                                        onSwitch = { onSwitch(session.id) },
                                        onRename = { onRename(session.id) },
                                        onDelete = { onDelete(session.id) },
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

/** 最近分区（未关联项目的会话）。 */
@Composable
private fun RecentSection(
    recentSessions: List<HarnessSessionEntity>,
    currentSessionId: String,
    sessionRunStates: Map<String, SessionRunState>,
    onSwitch: (String) -> Unit,
    onRename: (String) -> Unit,
    onDelete: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 6.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = stringResource(R.string.chat_drawer_recent),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = "${recentSessions.size}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline,
            )
        }

        if (recentSessions.isEmpty()) {
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainer,
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = stringResource(R.string.chat_drawer_no_recent_sessions),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    modifier = Modifier.padding(10.dp),
                )
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                recentSessions.forEach { session ->
                    SessionDrawerItem(
                        session = session,
                        isCurrent = session.id == currentSessionId,
                        runState = sessionRunStates[session.id] ?: SessionRunState.IDLE,
                        onSwitch = { onSwitch(session.id) },
                        onRename = { onRename(session.id) },
                        onDelete = { onDelete(session.id) },
                    )
                }
            }
        }
    }
}

/** 单条会话项：选择会话与管理操作分开，避免重复动作淹没标题。 */
@Composable
private fun SessionDrawerItem(
    session: HarnessSessionEntity, isCurrent: Boolean, runState: SessionRunState,
    onSwitch: () -> Unit, onRename: () -> Unit, onDelete: () -> Unit,
) {
    var showMenu by remember(session.id) { mutableStateOf(false) }
    val dotColor = when (runState) {
        SessionRunState.RUNNING -> top.wkbin.taixu.ui.theme.semanticColors().warning
        SessionRunState.WAITING_APPROVAL -> top.wkbin.taixu.ui.theme.semanticColors().agent
        SessionRunState.FAILED -> MaterialTheme.colorScheme.error
        else -> Color.Transparent
    }
    val timeLabel = formatSessionTime(session.updatedAt)
    Surface(shape = MaterialTheme.shapes.medium,
        color = if (isCurrent) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent,
        modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().clickable(onClick = onSwitch)
            .semantics { selected = isCurrent }.padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.size(6.dp).clip(CircleShape).background(dotColor))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(session.title, style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (isCurrent) FontWeight.Medium else FontWeight.Normal,
                    color = if (isCurrent) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurface,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (timeLabel.isNotBlank()) Text(timeLabel, style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Box {
                RuntimeIconButton(onClick = { showMenu = true }, modifier = Modifier.size(48.dp),
                    contentDescription = stringResource(R.string.chat_more_controls)) {
                    RuntimeIcon(RuntimeIconName.More, Modifier.size(18.dp), MaterialTheme.colorScheme.onSurfaceVariant)
                }
                DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                    DropdownMenuItem(text = { Text(stringResource(R.string.chat_rename_session)) },
                        leadingIcon = { RuntimeIcon(RuntimeIconName.Edit, Modifier.size(18.dp)) },
                        onClick = { showMenu = false; onRename() })
                    DropdownMenuItem(text = { Text(stringResource(R.string.chat_delete_session), color = MaterialTheme.colorScheme.error) },
                        leadingIcon = { RuntimeIcon(RuntimeIconName.Trash, Modifier.size(18.dp), MaterialTheme.colorScheme.error) },
                        onClick = { showMenu = false; onDelete() })
                }
            }
        }
    }
}
