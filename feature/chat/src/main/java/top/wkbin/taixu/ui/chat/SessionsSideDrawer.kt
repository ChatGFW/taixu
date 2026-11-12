package top.wkbin.taixu.ui.chat

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import top.wkbin.taixu.ui.miuix.RuntimeTheme
import top.wkbin.taixu.ui.miuix.Surface
import top.yukonga.miuix.kmp.basic.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import top.wkbin.taixu.core.database.HarnessSessionEntity
import top.wkbin.taixu.core.database.MessageSearchHitRow
import top.wkbin.taixu.core.model.RemoteServer
import top.wkbin.taixu.core.model.SessionRunState
import top.wkbin.taixu.core.model.bot.BotSessionGroupRef
import top.wkbin.taixu.feature.chat.R
import top.wkbin.taixu.runtime.SessionWorkspaceCatalogState
import top.wkbin.taixu.runtime.WorkspaceProject
import top.wkbin.taixu.ui.components.RuntimeAlertDialog
import top.wkbin.taixu.ui.components.RuntimeTextButton

private data class DrawerPartitions(
    val local: SessionPartitionResult,
    val remotes: List<RemoteServerSessionGroup>,
    val bots: List<HarnessSessionEntity>,
    val pinned: List<HarnessSessionEntity>,
)

/**
 * 会话侧栏：固定搜索与底部功能入口，会话列表独立滚动。
 * - 屏幕左侧展开，含背景遮罩与进退场滑动动画。
 * - 搜索会话标题/工作区，顶部 + 创建新会话，底部展开主功能与插件。
 * - 分区自上而下为「项目」→「远程服务器」→「Bot 会话」→「最近」，统一用名称 + 数量 + 折叠箭头的分区头。
 * - 「项目」展示关联工作区工程，支持展开/折叠与单项目一键「+」新建会话。
 * - 「远程服务器」按连接档案分组，可直接新建绑定该服务器的对话；不切换全局主系统。
 * - 「最近」展示未关联工作区的本机会话，按最近修改时间倒序排列。
 */
@Composable
internal fun SessionsSideDrawer(
    visible: Boolean,
    sessions: List<HarnessSessionEntity>,
    botSessions: List<HarnessSessionEntity> = emptyList(),
    botSessionGroups: Map<String, BotSessionGroupRef> = emptyMap(),
    hiddenBotSessionIds: Set<String> = emptySet(),
    onHideBotSession: (String, Boolean) -> Unit = { _, _ -> },
    currentSessionId: String,
    workspaces: List<WorkspaceProject>,
    remoteServers: List<RemoteServer> = emptyList(),
    remoteProjectsByServer: Map<String, SessionWorkspaceCatalogState> = emptyMap(),
    sessionRunStates: Map<String, SessionRunState>,
    onDismiss: () -> Unit,
    onSettings: () -> Unit,
    onSwitch: (String) -> Unit,
    onNew: () -> Unit,
    onCreateInWorkspace: (WorkspaceProject) -> Unit,
    onCreateOnServer: (RemoteServerSessionGroup) -> Unit = {},
    onCreateInRemoteProject: (RemoteServerSessionGroup, RemoteProjectBucket) -> Unit = { _, _ -> },
    onCreateTerminalOnServer: (RemoteServerSessionGroup) -> Unit = {},
    onOpenRemoteHome: (RemoteServerSessionGroup) -> Unit = {},
    onWatchRemoteProjects: (String) -> Unit = {},
    onDelete: (String) -> Unit,
    onRename: (String, String) -> Unit,
    onCollapse: (() -> Unit)? = null, navigationContent: @Composable () -> Unit = {},
    searchMessages: suspend (String) -> List<MessageSearchHitRow> = { emptyList() },
    onTogglePin: (String, Boolean) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier,
) {
    if (!visible) return

    var renameTargetId by rememberSaveable { mutableStateOf<String?>(null) }
    var deleteTargetId by rememberSaveable { mutableStateOf<String?>(null) }
    var query by rememberSaveable { mutableStateOf("") }
    var projectsExpanded by rememberSaveable { mutableStateOf(true) }
    var remotesExpanded by rememberSaveable { mutableStateOf(true) }
    // Search reveals matching projects without overwriting the user's normal collapsed state.
    var searchProjectsExpanded by rememberSaveable(query.isNotBlank()) { mutableStateOf(true) }
    var searchRemotesExpanded by rememberSaveable(query.isNotBlank()) { mutableStateOf(true) }
    val projectsByServer = remember(remoteProjectsByServer) {
        remoteProjectsByServer.mapValues { (_, state) -> state.projects }
    }
    val drawer = remember(
        sessions, botSessions, botSessionGroups, hiddenBotSessionIds, workspaces, remoteServers, projectsByServer, query,
    ) {
        val term = query.trim()
        fun HarnessSessionEntity.matches() = term.isEmpty() ||
            title.contains(term, ignoreCase = true) || workspace.contains(term, ignoreCase = true)
        val filtered = sessions.filter { it.matches() && it.pinnedAt == 0L }
        val pinnedMatches = sessions.filter { it.matches() && it.pinnedAt != 0L }.sortedByDescending { it.pinnedAt }
        val (remoteSessions, localSessions) = filtered.partition(::isRemoteBoundSession)
        val localPartition = partitionSessions(localSessions, workspaces)
        val remotes = attachRemoteProjects(partitionRemoteSessions(remoteSessions, remoteServers), projectsByServer)
        val bots = botSessions.filter { it.matches() && it.id !in hiddenBotSessionIds }
            // 私有/独立会话默认不进抽屉（Bot 详情页可直达），搜索时才作为历史入口出现。
            .filter { term.isNotEmpty() || it.id in botSessionGroups }
            .sortedByDescending { it.updatedAt }
        DrawerPartitions(
            local = if (term.isEmpty()) localPartition
            else localPartition.copy(projectGroups = localPartition.projectGroups.filter { it.sessions.isNotEmpty() }),
            remotes = if (term.isEmpty()) remotes else remotes.filter { it.sessions.isNotEmpty() || it.projectBuckets.any { b -> b.sessions.isNotEmpty() } },
            bots = bots,
            pinned = pinnedMatches,
        )
    }
    val hiddenBotSessions = remember(botSessions, hiddenBotSessionIds) {
        botSessions.filter { it.id in hiddenBotSessionIds }.sortedByDescending { it.updatedAt }
    }
    val partition = drawer.local
    val remoteGroups = drawer.remotes
    val botMatches = drawer.bots
    val pinnedMatches = drawer.pinned
    var expandedProjects by rememberSaveable {
        val initialExpanded = partition.projectGroups
            .filter { group -> group.sessions.any { it.id == currentSessionId } }
            .map { it.workspacePath }
            .toSet()
        mutableStateOf(initialExpanded)
    }
    var expandedServers by rememberSaveable {
        val initialExpanded = remoteGroups
            .filter { group -> group.sessions.any { it.id == currentSessionId } }
            .map { it.serverId }
            .toSet()
        mutableStateOf(initialExpanded)
    }
    var expandedRemoteProjects by rememberSaveable {
        val initial = remoteGroups.flatMap { group ->
            group.projectBuckets.filter { bucket -> bucket.sessions.any { it.id == currentSessionId } }
                .map { "${group.serverId}:${it.linuxPath}" }
        }.toSet()
        mutableStateOf(initial)
    }
    val attentionSessions = remember(sessions, botSessions, sessionRunStates) {
        (sessions + botSessions)
            .filter {
                sessionRunStates[it.id] == SessionRunState.WAITING_APPROVAL ||
                    sessionRunStates[it.id] == SessionRunState.FAILED
            }
            .sortedWith(
                compareByDescending<HarnessSessionEntity> {
                    sessionRunStates[it.id] == SessionRunState.WAITING_APPROVAL
                }.thenByDescending { it.updatedAt },
            )
    }

    Surface(
        modifier = modifier.fillMaxHeight().fillMaxWidth(),
        color = RuntimeTheme.colorScheme.surfaceContainerLow,
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
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (query.isNotBlank() && partition.projectGroups.isEmpty() && remoteGroups.isEmpty() &&
                    partition.recentSessions.isEmpty() && botMatches.isEmpty() && pinnedMatches.isEmpty()
                ) {
                    Text(stringResource(R.string.chat_drawer_no_matches),
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 16.dp),
                        style = RuntimeTheme.typography.bodySmall,
                        color = RuntimeTheme.colorScheme.onSurfaceVariant)
                }
                if (query.isBlank() && attentionSessions.isNotEmpty()) {
                    SessionAttentionBanner(
                        count = attentionSessions.size,
                        firstTitle = attentionSessions.first().title,
                        onOpen = { onSwitch(attentionSessions.first().id) },
                    )
                }
                if (pinnedMatches.isNotEmpty()) {
                    PinnedSessionsSection(
                        pinnedSessions = pinnedMatches,
                        currentSessionId = currentSessionId,
                        sessionRunStates = sessionRunStates,
                        onSwitch = onSwitch,
                        onTogglePin = onTogglePin,
                        onRename = { id -> renameTargetId = id },
                        onDelete = { id -> deleteTargetId = id },
                    )
                }
                if (partition.projectGroups.isNotEmpty()) {
                    ProjectSection(
                        projectGroups = partition.projectGroups,
                        sectionExpanded = if (query.isBlank()) projectsExpanded else searchProjectsExpanded,
                        onTogglePin = onTogglePin,
                        onToggleSection = {
                            if (query.isBlank()) projectsExpanded = !projectsExpanded
                            else searchProjectsExpanded = !searchProjectsExpanded
                        },
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

                if (query.isBlank() || remoteGroups.isNotEmpty()) {
                    RemoteServersSection(
                        serverGroups = remoteGroups,
                        sectionExpanded = if (query.isBlank()) remotesExpanded else searchRemotesExpanded,
                        onToggleSection = {
                            if (query.isBlank()) remotesExpanded = !remotesExpanded
                            else searchRemotesExpanded = !searchRemotesExpanded
                        },
                        currentSessionId = currentSessionId,
                        sessionRunStates = sessionRunStates,
                        expandedServers = if (query.isBlank()) expandedServers
                        else remoteGroups.map { it.serverId }.toSet(),
                        expandedProjects = expandedRemoteProjects,
                        remoteProjectsByServer = remoteProjectsByServer,
                        onToggleExpand = { id ->
                            expandedServers = if (expandedServers.contains(id)) expandedServers - id else expandedServers + id
                        },
                        onToggleProjectExpand = { key ->
                            expandedRemoteProjects =
                                if (expandedRemoteProjects.contains(key)) expandedRemoteProjects - key
                                else expandedRemoteProjects + key
                        },
                        onWatchProjects = onWatchRemoteProjects,
                        onSwitch = onSwitch,
                        onCreateOnServer = onCreateOnServer,
                        onCreateInProject = onCreateInRemoteProject,
                        onCreateTerminal = onCreateTerminalOnServer,
                        onOpenRemoteHome = onOpenRemoteHome,
                        onRename = { id -> renameTargetId = id },
                        onDelete = { id -> deleteTargetId = id },
                        onTogglePin = onTogglePin,
                    )
                }

                if (botMatches.isNotEmpty()) {
                    BotSessionsSection(
                        botSessions = botMatches,
                        groupRefs = botSessionGroups,
                        currentSessionId = currentSessionId,
                        sessionRunStates = sessionRunStates,
                        searchActive = query.isNotBlank(),
                        onSwitch = onSwitch,
                        onRename = { id -> renameTargetId = id },
                        onDelete = { id -> deleteTargetId = id },
                        onHide = onHideBotSession,
                    )
                }

                if (query.isNotBlank()) {
                    SessionMessageHits(query = query, search = searchMessages, onSwitch = { onSwitch(it); onDismiss() })
                }
                if (query.isBlank() || partition.recentSessions.isNotEmpty()) {
                    RecentSection(
                        recentSessions = partition.recentSessions,
                        currentSessionId = currentSessionId,
                        sessionRunStates = sessionRunStates,
                        onSwitch = onSwitch,
                        onRename = { id -> renameTargetId = id },
                        onDelete = { id -> deleteTargetId = id },
                        onTogglePin = onTogglePin,
                    )
                }

                if (hiddenBotSessions.isNotEmpty()) HiddenSessionsEntry(hiddenBotSessions, onRestore = onHideBotSession)
            }
            SessionDrawerFooter(sessions.size, onSettings, navigationContent)
        }
    }

    // 重命名会话弹窗
    val renameTarget = renameTargetId?.let { id -> (sessions + botSessions).firstOrNull { it.id == id } }
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
            title = { Text(stringResource(R.string.chat_delete_session_title), fontWeight = FontWeight.Medium) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        stringResource(R.string.chat_delete_session_message),
                        style = RuntimeTheme.typography.bodyMedium,
                        color = RuntimeTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            confirmButton = {
                RuntimeTextButton(
                    onClick = {
                        deleteTargetId = null
                        onDelete(targetId)
                    },
                ) {
                    Text(stringResource(R.string.chat_confirm_delete), color = RuntimeTheme.colorScheme.onError)
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

/** 最近分区（未关联项目的会话）。 */
@Composable
private fun RecentSection(
    recentSessions: List<HarnessSessionEntity>,
    currentSessionId: String,
    sessionRunStates: Map<String, SessionRunState>,
    onSwitch: (String) -> Unit,
    onRename: (String) -> Unit,
    onDelete: (String) -> Unit,
    onTogglePin: (String, Boolean) -> Unit,
) {
    var expanded by rememberSaveable { mutableStateOf(true) }
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        SessionSectionHeader(
            title = stringResource(R.string.chat_drawer_recent),
            count = recentSessions.size,
            expanded = expanded,
            onToggle = { expanded = !expanded },
        )
        if (!expanded) return@Column

        if (recentSessions.isEmpty()) {
            Text(text = stringResource(R.string.chat_drawer_no_recent_sessions),
                style = RuntimeTheme.typography.bodySmall, color = RuntimeTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp))
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                recentSessions.forEach { session ->
                    SessionDrawerItem(session = session, isCurrent = session.id == currentSessionId,
                        runState = sessionRunStates[session.id] ?: SessionRunState.IDLE,
                        onSwitch = { onSwitch(session.id) }, onRename = { onRename(session.id) },
                        onDelete = { onDelete(session.id) }, pinned = session.pinnedAt != 0L,
                        onTogglePin = { onTogglePin(session.id, session.pinnedAt == 0L) })
                }
            }
        }
    }
}

/** 置顶分区：pinnedAt 倒序；置顶会话不再出现在项目/最近分区。 */
@Composable
private fun PinnedSessionsSection(
    pinnedSessions: List<HarnessSessionEntity>,
    currentSessionId: String,
    sessionRunStates: Map<String, SessionRunState>,
    onSwitch: (String) -> Unit,
    onTogglePin: (String, Boolean) -> Unit,
    onRename: (String) -> Unit,
    onDelete: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        SessionSectionHeader(
            title = stringResource(R.string.chat_drawer_pinned),
            count = pinnedSessions.size,
            expanded = true,
            onToggle = {},
        )
        pinnedSessions.forEach { session ->
            SessionDrawerItem(session = session, isCurrent = session.id == currentSessionId,
                runState = sessionRunStates[session.id] ?: SessionRunState.IDLE,
                onSwitch = { onSwitch(session.id) }, onRename = { onRename(session.id) },
                onDelete = { onDelete(session.id) }, pinned = true, onTogglePin = { onTogglePin(session.id, false) })
        }
    }
}
