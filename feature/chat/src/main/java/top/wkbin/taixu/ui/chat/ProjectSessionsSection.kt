package top.wkbin.taixu.ui.chat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import top.wkbin.taixu.ui.miuix.RuntimeTheme
import top.wkbin.taixu.ui.miuix.Surface
import top.yukonga.miuix.kmp.basic.Text
import androidx.compose.runtime.Composable
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
import top.wkbin.taixu.ui.components.RuntimeExpandChevron
import top.wkbin.taixu.ui.components.RuntimeIcon
import top.wkbin.taixu.ui.components.RuntimeIconName

/** 项目分区。 */
@Composable
internal fun ProjectSection(
    projectGroups: List<ProjectSessionGroup>,
    sectionExpanded: Boolean,
    onToggleSection: () -> Unit,
    currentSessionId: String,
    sessionRunStates: Map<String, SessionRunState>,
    expandedProjects: Set<String>,
    onToggleExpand: (String) -> Unit,
    onSwitch: (String) -> Unit,
    onCreateInProject: (ProjectSessionGroup) -> Unit,
    onRename: (String) -> Unit,
    onDelete: (String) -> Unit,
    onTogglePin: (String, Boolean) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        SessionSectionHeader(
            title = stringResource(R.string.chat_drawer_projects),
            count = projectGroups.size,
            expanded = sectionExpanded,
            onToggle = onToggleSection,
        )
        if (!sectionExpanded) return@Column

        if (projectGroups.isEmpty()) {
            Surface(
                color = RuntimeTheme.colorScheme.surfaceContainer,
                shape = RuntimeTheme.shapes.small,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = stringResource(R.string.chat_drawer_no_projects),
                    style = RuntimeTheme.typography.labelSmall,
                    color = RuntimeTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    modifier = Modifier.padding(10.dp),
                )
            }
        } else {
            projectGroups.forEach { group ->
                val isExpanded = expandedProjects.contains(group.workspacePath)
                val newTaskLabel = stringResource(R.string.chat_drawer_new_task)
                Column(
                    modifier = Modifier
                        .fillMaxWidth(),
                ) {
                    // 项目文件夹行：字号/图标与分区头（「最近」）对齐，避免视觉比分区头更抢
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RuntimeTheme.shapes.small)
                            .clickable { onToggleExpand(group.workspacePath) }
                            .padding(start = 8.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        RuntimeIcon(
                            name = if (isExpanded) RuntimeIconName.FolderOpen
                            else RuntimeIconName.Folder,
                            modifier = Modifier.size(16.dp),
                            tint = RuntimeTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = group.projectName,
                            style = RuntimeTheme.typography.labelMedium,
                            color = RuntimeTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        if (group.sessions.isNotEmpty()) {
                            Text(
                                text = "${group.sessions.size}",
                                style = RuntimeTheme.typography.labelSmall,
                                color = RuntimeTheme.colorScheme.outline,
                            )
                        }

                        // 单项目快捷新建会话入口
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .clip(CircleShape)
                                .clickable { onCreateInProject(group) }
                                .semantics { contentDescription = newTaskLabel },
                            contentAlignment = Alignment.Center,
                        ) {
                            RuntimeIcon(
                                name = RuntimeIconName.Plus,
                                modifier = Modifier.size(14.dp),
                                tint = RuntimeTheme.colorScheme.onSurfaceVariant,
                            )
                        }

                        RuntimeExpandChevron(
                            expanded = isExpanded,
                            modifier = Modifier.size(14.dp),
                            tint = RuntimeTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    // 展开的会话子项
                    if (isExpanded) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 24.dp, end = 4.dp),
                            verticalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            if (group.sessions.isEmpty()) {
                                Text(
                                    text = stringResource(R.string.chat_drawer_no_sessions_in_project),
                                    style = RuntimeTheme.typography.labelSmall,
                                    color = RuntimeTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
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
