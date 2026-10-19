package top.wkbin.taixu.ui.chat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.clickable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import top.wkbin.taixu.core.model.SessionRunState
import top.wkbin.taixu.feature.chat.R
import top.wkbin.taixu.ui.components.RuntimeIcon
import top.wkbin.taixu.ui.components.RuntimeIconButton
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
                modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "${projectGroups.size}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline,
            )
            val stateLabel = stringResource(if (sectionExpanded) R.string.chat_drawer_projects_expanded else R.string.chat_drawer_projects_collapsed)
            RuntimeIconButton(
                onClick = onToggleSection,
                contentDescription = stringResource(if (sectionExpanded) R.string.chat_drawer_collapse_projects else R.string.chat_drawer_expand_projects),
                modifier = Modifier.semantics { stateDescription = stateLabel },
            ) {
                RuntimeIcon(if (sectionExpanded) RuntimeIconName.ChevronDown else RuntimeIconName.ChevronRight,
                    Modifier.size(20.dp), MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (!sectionExpanded) return@Column

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
