package top.wkbin.taixu.ui.chat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import top.wkbin.taixu.core.database.HarnessSessionEntity
import top.wkbin.taixu.core.model.SessionRunState
import top.wkbin.taixu.feature.chat.R
import top.wkbin.taixu.ui.components.RuntimeTextButton

/** Bot 产生的会话（私有、群维度、独立委派）统一收拢：默认折叠，展开时活跃优先并限量渲染。 */
private const val VISIBLE_LIMIT = 8

@Composable
internal fun BotSessionsSection(
    botSessions: List<HarnessSessionEntity>,
    currentSessionId: String,
    sessionRunStates: Map<String, SessionRunState>,
    forceExpanded: Boolean,
    onSwitch: (String) -> Unit,
    onRename: (String) -> Unit,
    onDelete: (String) -> Unit,
) {
    var expanded by rememberSaveable {
        mutableStateOf(botSessions.any { it.id == currentSessionId })
    }
    var showAll by rememberSaveable { mutableStateOf(false) }
    // 搜索命中时自动展开一次，但保留用户手动折叠的能力。
    LaunchedEffect(forceExpanded) { if (forceExpanded) expanded = true }
    val isExpanded = expanded
    val ordered = remember(botSessions, sessionRunStates) {
        botSessions.sortedWith(
            compareByDescending<HarnessSessionEntity> { sessionRunStates[it.id].isBusy() }
                .thenByDescending { it.updatedAt },
        )
    }
    val busyCount = remember(ordered, sessionRunStates) { ordered.count { sessionRunStates[it.id].isBusy() } }
    val visible = if (showAll) ordered else ordered.take(VISIBLE_LIMIT)
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        SessionSectionHeader(
            title = stringResource(R.string.chat_drawer_bot_sessions),
            count = botSessions.size,
            expanded = isExpanded,
            onToggle = { expanded = !expanded },
            alert = busyCount > 0,
        )
        if (isExpanded) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(start = 12.dp, end = 4.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                visible.forEach { session ->
                    SessionDrawerItem(
                        session = session,
                        isCurrent = session.id == currentSessionId,
                        runState = sessionRunStates[session.id] ?: SessionRunState.IDLE,
                        onSwitch = { onSwitch(session.id) },
                        onRename = { onRename(session.id) },
                        onDelete = { onDelete(session.id) },
                    )
                }
                if (ordered.size > VISIBLE_LIMIT) {
                    RuntimeTextButton(onClick = { showAll = !showAll }) {
                        Text(
                            stringResource(
                                if (showAll) R.string.chat_drawer_bot_sessions_collapse
                                else R.string.chat_drawer_bot_sessions_expand,
                                ordered.size,
                            ),
                            style = MaterialTheme.typography.labelMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

private fun SessionRunState?.isBusy(): Boolean =
    this == SessionRunState.RUNNING || this == SessionRunState.WAITING_APPROVAL || this == SessionRunState.FAILED
