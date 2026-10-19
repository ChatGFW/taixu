package top.wkbin.taixu.ui.chat

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import top.wkbin.taixu.core.database.HarnessSessionEntity
import top.wkbin.taixu.core.model.SessionRunState
import top.wkbin.taixu.feature.chat.R
import top.wkbin.taixu.ui.components.RuntimeIcon
import top.wkbin.taixu.ui.components.RuntimeIconName

/** Bot 专属会话独立收拢，默认折叠，避免挤占普通会话列表。 */
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
    // 搜索命中时自动展开一次，但保留用户手动折叠的能力。
    LaunchedEffect(forceExpanded) { if (forceExpanded) expanded = true }
    val isExpanded = expanded
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.large)
                .clickable { expanded = !expanded }
                .padding(start = 8.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            RuntimeIcon(RuntimeIconName.Bot, Modifier.size(24.dp), MaterialTheme.colorScheme.primary)
            Text(
                text = stringResource(R.string.chat_drawer_bot_sessions),
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = "${botSessions.size}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline,
            )
            RuntimeIcon(
                name = if (isExpanded) RuntimeIconName.ChevronDown else RuntimeIconName.ChevronRight,
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (isExpanded) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(start = 12.dp, end = 4.dp, bottom = 4.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                botSessions.forEach { session ->
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
