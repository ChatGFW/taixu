package top.wkbin.taixu.ui.chat

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import top.wkbin.taixu.core.model.SessionRunState
import top.wkbin.taixu.feature.chat.R
import top.wkbin.taixu.runtime.SessionWorkspaceCatalogState
import top.wkbin.taixu.ui.components.RuntimeExpandChevron
import top.wkbin.taixu.ui.components.RuntimeIcon
import top.wkbin.taixu.ui.components.RuntimeIconName
import top.wkbin.taixu.ui.miuix.RuntimeTheme
import top.wkbin.taixu.ui.miuix.Surface
import top.yukonga.miuix.kmp.basic.Text

/** 侧栏远程分区入口：搜索态自动展开全部服务器。 */
@Composable
internal fun SessionsDrawerRemoteServers(
    query: String,
    remoteGroups: List<RemoteServerSessionGroup>,
    remotesExpanded: Boolean,
    searchRemotesExpanded: Boolean,
    onToggleSection: () -> Unit,
    currentSessionId: String,
    sessionRunStates: Map<String, SessionRunState>,
    expandedServers: Set<String>,
    expandedRemoteProjects: Set<String>,
    remoteProjectsByServer: Map<String, SessionWorkspaceCatalogState>,
    onToggleExpand: (String) -> Unit,
    onToggleProjectExpand: (String) -> Unit,
    onWatchProjects: (String) -> Unit,
    onSwitch: (String) -> Unit,
    onCreateOnServer: (RemoteServerSessionGroup) -> Unit,
    onCreateInProject: (RemoteServerSessionGroup, RemoteProjectBucket) -> Unit,
    onCreateTerminal: (RemoteServerSessionGroup) -> Unit,
    onOpenRemoteHome: (RemoteServerSessionGroup) -> Unit,
    onRename: (String) -> Unit,
    onDelete: (String) -> Unit,
    onTogglePin: (String, Boolean) -> Unit,
) {
    if (query.isNotBlank() && remoteGroups.isEmpty()) return
    RemoteServersSection(
        serverGroups = remoteGroups,
        sectionExpanded = if (query.isBlank()) remotesExpanded else searchRemotesExpanded,
        onToggleSection = onToggleSection,
        currentSessionId = currentSessionId,
        sessionRunStates = sessionRunStates,
        expandedServers = if (query.isBlank()) expandedServers else remoteGroups.map { it.groupKey }.toSet(),
        expandedProjects = expandedRemoteProjects,
        remoteProjectsByServer = remoteProjectsByServer,
        onToggleExpand = onToggleExpand,
        onToggleProjectExpand = onToggleProjectExpand,
        onWatchProjects = onWatchProjects,
        onSwitch = onSwitch,
        onCreateOnServer = onCreateOnServer,
        onCreateInProject = onCreateInProject,
        onCreateTerminal = onCreateTerminal,
        onOpenRemoteHome = onOpenRemoteHome,
        onRename = onRename,
        onDelete = onDelete,
        onTogglePin = onTogglePin,
    )
}

/** 远程服务器分区：项目树 + 根会话；可新建对话与远程终端。 */
@Composable
internal fun RemoteServersSection(
    serverGroups: List<RemoteServerSessionGroup>,
    sectionExpanded: Boolean,
    onToggleSection: () -> Unit,
    currentSessionId: String,
    sessionRunStates: Map<String, SessionRunState>,
    expandedServers: Set<String>,
    expandedProjects: Set<String>,
    remoteProjectsByServer: Map<String, SessionWorkspaceCatalogState>,
    onToggleExpand: (String) -> Unit,
    onToggleProjectExpand: (String) -> Unit,
    onWatchProjects: (String) -> Unit,
    onSwitch: (String) -> Unit,
    onCreateOnServer: (RemoteServerSessionGroup) -> Unit,
    onCreateInProject: (RemoteServerSessionGroup, RemoteProjectBucket) -> Unit,
    onCreateTerminal: (RemoteServerSessionGroup) -> Unit,
    onOpenRemoteHome: (RemoteServerSessionGroup) -> Unit,
    onRename: (String) -> Unit,
    onDelete: (String) -> Unit,
    onTogglePin: (String, Boolean) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        SessionSectionHeader(
            title = stringResource(R.string.chat_drawer_remote_servers),
            count = serverGroups.size,
            expanded = sectionExpanded,
            onToggle = onToggleSection,
        )
        if (!sectionExpanded) return@Column

        if (serverGroups.isEmpty()) {
            Surface(
                color = RuntimeTheme.colorScheme.surfaceContainer,
                shape = RuntimeTheme.shapes.small,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = stringResource(R.string.chat_drawer_no_remote_servers),
                    style = RuntimeTheme.typography.labelSmall,
                    color = RuntimeTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    modifier = Modifier.padding(10.dp),
                )
            }
        } else {
            serverGroups.forEach { group ->
                val isExpanded = expandedServers.contains(group.groupKey)
                LaunchedEffect(isExpanded, group.groupKey, group.profilePresent) {
                    if (isExpanded && group.profilePresent) onWatchProjects(group.serverId)
                }
                val catalog = remoteProjectsByServer[group.serverId]
                val newTaskLabel = stringResource(R.string.chat_drawer_new_task)
                val newTerminalLabel = stringResource(R.string.chat_drawer_new_remote_terminal)
                val openHomeLabel = stringResource(R.string.chat_drawer_open_remote_home)
                val actionTint = if (group.profilePresent) RuntimeTheme.colorScheme.onSurfaceVariant
                else RuntimeTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RuntimeTheme.shapes.small)
                            .clickable { onToggleExpand(group.groupKey) }
                            .padding(start = 8.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        RuntimeIcon(
                            name = RuntimeIconName.Server,
                            modifier = Modifier.size(16.dp),
                            tint = RuntimeTheme.colorScheme.onSurfaceVariant,
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = group.serverName,
                                style = RuntimeTheme.typography.labelMedium,
                                color = RuntimeTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = if (group.profilePresent) group.hostLabel
                                else stringResource(R.string.chat_drawer_remote_profile_stale, group.hostLabel),
                                style = RuntimeTheme.typography.labelSmall,
                                color = RuntimeTheme.colorScheme.outline,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        if (group.sessions.isNotEmpty()) {
                            Text(
                                text = "${group.sessions.size}",
                                style = RuntimeTheme.typography.labelSmall,
                                color = RuntimeTheme.colorScheme.outline,
                            )
                        }
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .clip(CircleShape)
                                .then(
                                    if (group.profilePresent) Modifier.clickable { onOpenRemoteHome(group) }
                                    else Modifier,
                                )
                                .semantics { contentDescription = openHomeLabel },
                            contentAlignment = Alignment.Center,
                        ) {
                            RuntimeIcon(name = RuntimeIconName.Home, modifier = Modifier.size(14.dp), tint = actionTint)
                        }
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .clip(CircleShape)
                                .then(
                                    if (group.profilePresent) Modifier.clickable { onCreateTerminal(group) }
                                    else Modifier,
                                )
                                .semantics { contentDescription = newTerminalLabel },
                            contentAlignment = Alignment.Center,
                        ) {
                            RuntimeIcon(name = RuntimeIconName.Terminal, modifier = Modifier.size(14.dp), tint = actionTint)
                        }
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .clip(CircleShape)
                                .then(
                                    if (group.profilePresent) Modifier.clickable { onCreateOnServer(group) }
                                    else Modifier,
                                )
                                .semantics { contentDescription = newTaskLabel },
                            contentAlignment = Alignment.Center,
                        ) {
                            RuntimeIcon(name = RuntimeIconName.Plus, modifier = Modifier.size(14.dp), tint = actionTint)
                        }
                        RuntimeExpandChevron(
                            expanded = isExpanded,
                            modifier = Modifier.size(14.dp),
                            tint = RuntimeTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (isExpanded) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 24.dp, end = 4.dp),
                            verticalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            if (!group.profilePresent) {
                                Text(
                                    text = stringResource(R.string.chat_drawer_remote_profile_stale_hint),
                                    style = RuntimeTheme.typography.labelSmall,
                                    color = RuntimeTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                )
                            } else when {
                                catalog == null || catalog.loading -> Text(
                                    text = stringResource(R.string.chat_drawer_remote_projects_loading),
                                    style = RuntimeTheme.typography.labelSmall,
                                    color = RuntimeTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                )
                                catalog.error != null -> Text(
                                    text = catalog.error!!,
                                    style = RuntimeTheme.typography.labelSmall,
                                    color = RuntimeTheme.colorScheme.error,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                )
                            }
                            group.projectBuckets.forEach { bucket ->
                                val projectKey = "${group.groupKey}:${bucket.linuxPath}"
                                val projectExpanded = expandedProjects.contains(projectKey)
                                RemoteProjectRow(
                                    bucket = bucket,
                                    expanded = projectExpanded,
                                    onToggle = { onToggleProjectExpand(projectKey) },
                                    onCreate = { onCreateInProject(group, bucket) },
                                    currentSessionId = currentSessionId,
                                    sessionRunStates = sessionRunStates,
                                    onSwitch = onSwitch,
                                    onRename = onRename,
                                    onDelete = onDelete,
                                    onTogglePin = onTogglePin,
                                )
                            }
                            if (group.rootSessions.isEmpty() && group.projectBuckets.all { it.sessions.isEmpty() }) {
                                Text(
                                    text = stringResource(R.string.chat_drawer_no_sessions_on_server),
                                    style = RuntimeTheme.typography.labelSmall,
                                    color = RuntimeTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                )
                            } else {
                                group.rootSessions.forEach { session ->
                                    SessionDrawerItem(
                                        session = session,
                                        isCurrent = session.id == currentSessionId,
                                        runState = sessionRunStates[session.id] ?: SessionRunState.IDLE,
                                        onSwitch = { onSwitch(session.id) },
                                        onRename = { onRename(session.id) },
                                        onDelete = { onDelete(session.id) },
                                        pinned = session.pinnedAt != 0L,
                                        onTogglePin = { onTogglePin(session.id, session.pinnedAt == 0L) },
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

@Composable
private fun RemoteProjectRow(
    bucket: RemoteProjectBucket,
    expanded: Boolean,
    onToggle: () -> Unit,
    onCreate: () -> Unit,
    currentSessionId: String,
    sessionRunStates: Map<String, SessionRunState>,
    onSwitch: (String) -> Unit,
    onRename: (String) -> Unit,
    onDelete: (String) -> Unit,
    onTogglePin: (String, Boolean) -> Unit,
) {
    val newTaskLabel = stringResource(R.string.chat_drawer_new_task)
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RuntimeTheme.shapes.small)
                .clickable(onClick = onToggle)
                .padding(start = 4.dp, end = 4.dp, top = 2.dp, bottom = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            RuntimeIcon(
                name = if (expanded) RuntimeIconName.FolderOpen else RuntimeIconName.Folder,
                modifier = Modifier.size(14.dp),
                tint = RuntimeTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = bucket.name,
                style = RuntimeTheme.typography.labelSmall,
                color = RuntimeTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (bucket.sessions.isNotEmpty()) {
                Text(
                    text = "${bucket.sessions.size}",
                    style = RuntimeTheme.typography.labelSmall,
                    color = RuntimeTheme.colorScheme.outline,
                )
            }
            Box(
                modifier = Modifier
                    .size(22.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onCreate)
                    .semantics { contentDescription = newTaskLabel },
                contentAlignment = Alignment.Center,
            ) {
                RuntimeIcon(
                    name = RuntimeIconName.Plus,
                    modifier = Modifier.size(12.dp),
                    tint = RuntimeTheme.colorScheme.onSurfaceVariant,
                )
            }
            RuntimeExpandChevron(
                expanded = expanded,
                modifier = Modifier.size(12.dp),
                tint = RuntimeTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (expanded) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                if (bucket.sessions.isEmpty()) {
                    Text(
                        text = stringResource(R.string.chat_drawer_no_sessions_in_project),
                        style = RuntimeTheme.typography.labelSmall,
                        color = RuntimeTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                    )
                } else {
                    bucket.sessions.forEach { session ->
                        SessionDrawerItem(
                            session = session,
                            isCurrent = session.id == currentSessionId,
                            runState = sessionRunStates[session.id] ?: SessionRunState.IDLE,
                            onSwitch = { onSwitch(session.id) },
                            onRename = { onRename(session.id) },
                            onDelete = { onDelete(session.id) },
                            pinned = session.pinnedAt != 0L,
                            onTogglePin = { onTogglePin(session.id, session.pinnedAt == 0L) },
                        )
                    }
                }
            }
        }
    }
}
