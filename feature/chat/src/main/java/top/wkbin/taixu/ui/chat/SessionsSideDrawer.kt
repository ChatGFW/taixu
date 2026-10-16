package top.wkbin.taixu.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.imePadding
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import top.wkbin.taixu.core.database.HarnessSessionEntity
import top.wkbin.taixu.core.model.SessionRunState
import top.wkbin.taixu.feature.chat.R
import top.wkbin.taixu.runtime.ProjectType
import top.wkbin.taixu.runtime.WorkspaceProject
import top.wkbin.taixu.ui.components.RuntimeAlertDialog
import top.wkbin.taixu.ui.components.RuntimeButton
import top.wkbin.taixu.ui.components.RuntimeIcon
import top.wkbin.taixu.ui.components.RuntimeIconButton
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
        projectGroups.add(ProjectSessionGroup(ws.name, ws.linuxPath, ws.projectType, projSessions))
    }

    for ((wsPath, orphanSessions) in sessionsByWs) {
        if (!handledWsPaths.contains(wsPath)) {
            val extractedName = wsPath.substringAfterLast('/').ifBlank { wsPath }
            projectGroups.add(ProjectSessionGroup(extractedName, wsPath, ProjectType.GENERAL, orphanSessions.sortedByDescending { it.updatedAt }))
        }
    }

    val sortedProjectGroups = projectGroups.sortedWith(
        compareByDescending<ProjectSessionGroup> { it.sessions.firstOrNull()?.updatedAt ?: 0L }
            .thenBy { it.projectName.lowercase() },
    )
    return SessionPartitionResult(sortedProjectGroups, recentSessions)
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
 * Solab 风格的会话侧栏：固定搜索与底部功能入口，会话列表独立滚动。
 * - 屏幕左侧展开，含背景遮罩与进退场滑动动画。
 * - 搜索会话标题/工作区，顶部 + 创建新会话，底部展开主功能与插件。
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
    onSettings: () -> Unit,
    onSwitch: (String) -> Unit,
    onNew: () -> Unit,
    onCreateInWorkspace: (WorkspaceProject) -> Unit,
    onDelete: (String) -> Unit,
    onRename: (String, String) -> Unit,
    onCollapse: (() -> Unit)? = null, navigationContent: @Composable () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    if (!visible) return

    var renameTargetId by rememberSaveable { mutableStateOf<String?>(null) }
    var deleteTargetId by rememberSaveable { mutableStateOf<String?>(null) }
    var query by rememberSaveable { mutableStateOf("") }
    val partition = remember(sessions, workspaces, query) {
        val term = query.trim()
        val filtered = if (term.isEmpty()) sessions else sessions.filter {
            it.title.contains(term, ignoreCase = true) || it.workspace.contains(term, ignoreCase = true)
        }
        val result = partitionSessions(filtered, workspaces)
        if (term.isEmpty()) result else result.copy(projectGroups = result.projectGroups.filter { it.sessions.isNotEmpty() })
    }
    var expandedProjects by rememberSaveable {
        val initialExpanded = partition.projectGroups
            .filter { group -> group.sessions.any { it.id == currentSessionId } }
            .map { it.workspacePath }
            .toSet()
        mutableStateOf(initialExpanded)
    }

    Surface(
        modifier = modifier.fillMaxHeight().fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = 0.dp,
    ) {
        Column(
            modifier = Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding(),
        ) {
            SessionDrawerSearch(query, { query = it }, onNew, onCollapse)

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp),
            ) {
                if (query.isNotBlank() && partition.projectGroups.isEmpty() && partition.recentSessions.isEmpty()) {
                    Text(stringResource(R.string.chat_drawer_no_matches),
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 16.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (query.isBlank() || partition.projectGroups.isNotEmpty()) {
                    ProjectSection(
                        projectGroups = partition.projectGroups,
                        currentSessionId = currentSessionId,
                        sessionRunStates = sessionRunStates,
                        expandedProjects = if (query.isBlank()) expandedProjects
                            else partition.projectGroups.map { it.workspacePath }.toSet(),
                        onToggleExpand = { path ->
                            expandedProjects = if (expandedProjects.contains(path)) expandedProjects - path else expandedProjects + path
                        },
                        onSwitch = onSwitch,
                        onCreateInProject = { group ->
                            val matchedWs = workspaces.firstOrNull { it.linuxPath == group.workspacePath }
                                ?: WorkspaceProject(
                                    name = group.projectName,
                                    path = group.workspacePath,
                                    linuxPath = group.workspacePath,
                                    sizeBytes = 0L,
                                    projectType = group.projectType,
                                )
                            onCreateInWorkspace(matchedWs)
                        },
                        onRename = { id -> renameTargetId = id },
                        onDelete = { id -> deleteTargetId = id },
                    )
                }

                if (query.isBlank() || partition.recentSessions.isNotEmpty()) {
                    RecentSection(
                        recentSessions = partition.recentSessions,
                        currentSessionId = currentSessionId,
                        sessionRunStates = sessionRunStates,
                        onSwitch = onSwitch,
                        onRename = { id -> renameTargetId = id },
                        onDelete = { id -> deleteTargetId = id },
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))
            }
            SessionDrawerFooter(sessions.size, onSettings, navigationContent)
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
                style = MaterialTheme.typography.titleSmall,
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
                shape = MaterialTheme.shapes.large,
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
                            .clip(MaterialTheme.shapes.large)
                            .clickable { onToggleExpand(group.workspacePath) }
                            .padding(start = 8.dp, end = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        RuntimeIcon(
                            name = if (isExpanded) RuntimeIconName.FolderOpen
                            else RuntimeIconName.Folder,
                            modifier = Modifier.size(24.dp),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                        Text(
                            text = group.projectName,
                            style = MaterialTheme.typography.titleMedium,
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
                                modifier = Modifier.size(24.dp),
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        }

                        RuntimeIcon(
                            name = if (isExpanded) RuntimeIconName.ChevronDown
                            else RuntimeIconName.ChevronRight,
                            modifier = Modifier.size(20.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    // 展开的会话子项
                    if (isExpanded) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 12.dp, end = 4.dp, bottom = 4.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
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
                style = MaterialTheme.typography.titleSmall,
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
                shape = MaterialTheme.shapes.large,
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
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
    val isRunningOrWarning = runState == SessionRunState.RUNNING || runState == SessionRunState.WAITING_APPROVAL || runState == SessionRunState.FAILED
    val dotColor = when (runState) {
        SessionRunState.RUNNING -> top.wkbin.taixu.ui.theme.semanticColors().warning
        SessionRunState.WAITING_APPROVAL -> top.wkbin.taixu.ui.theme.semanticColors().agent
        SessionRunState.FAILED -> MaterialTheme.colorScheme.error
        else -> Color.Transparent
    }
    val timeLabel = formatSessionTime(session.updatedAt)
    Surface(
        shape = CircleShape,
        color = if (isCurrent) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onSwitch)
                .semantics { selected = isCurrent }
                .padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (isRunningOrWarning) {
                Box(Modifier.size(8.dp).clip(CircleShape).background(dotColor))
            } else {
                RuntimeIcon(
                    name = RuntimeIconName.Chat,
                    modifier = Modifier.size(24.dp),
                    tint = if (isCurrent) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = session.title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = if (isCurrent) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (isCurrent) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (timeLabel.isNotBlank()) {
                    Text(
                        text = timeLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Box {
                RuntimeIconButton(
                    onClick = { showMenu = true },
                    modifier = Modifier.size(48.dp),
                    contentDescription = stringResource(R.string.chat_more_controls),
                ) {
                    RuntimeIcon(RuntimeIconName.More, Modifier.size(24.dp), MaterialTheme.colorScheme.onSurfaceVariant)
                }
                DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.chat_rename_session)) },
                        leadingIcon = { RuntimeIcon(RuntimeIconName.Edit, Modifier.size(18.dp)) },
                        onClick = { showMenu = false; onRename() },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.chat_delete_session), color = MaterialTheme.colorScheme.error) },
                        leadingIcon = { RuntimeIcon(RuntimeIconName.Trash, Modifier.size(18.dp), MaterialTheme.colorScheme.error) },
                        onClick = { showMenu = false; onDelete() },
                    )
                }
            }
        }
    }
}
