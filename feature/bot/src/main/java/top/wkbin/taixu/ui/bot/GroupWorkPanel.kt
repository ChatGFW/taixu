package top.wkbin.taixu.ui.bot

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import top.wkbin.taixu.core.model.bot.BotAssignment
import top.wkbin.taixu.core.model.bot.BotAssignmentStatus
import top.wkbin.taixu.feature.bot.R
import top.wkbin.taixu.ui.components.RuntimeAlertDialog
import top.wkbin.taixu.ui.components.RuntimeIcon
import top.wkbin.taixu.ui.components.RuntimeIconButton
import top.wkbin.taixu.ui.components.RuntimeIconName
import top.wkbin.taixu.ui.components.RuntimeTextButton

/** 群聊「工作」面板：持久化委派的执行中/历史列表，支持展开详情与取消。 */
@Composable
internal fun GroupWorkDialog(
    assignments: List<BotAssignment>,
    onDismiss: () -> Unit,
    onCancel: (String) -> Unit,
) {
    val running = assignments.count { it.status == BotAssignmentStatus.RUNNING }
    val finished = assignments.size - running
    RuntimeAlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(stringResource(R.string.fbot_work_title))
                Text(
                    stringResource(R.string.fbot_work_summary, running, finished),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        text = {
            if (assignments.isEmpty()) {
                Text(
                    stringResource(R.string.fbot_work_empty),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                LazyColumn(
                    modifier = Modifier.heightIn(max = 440.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(assignments, key = { it.id }) { assignment ->
                        WorkCard(assignment = assignment, onCancel = onCancel)
                    }
                }
            }
        },
        confirmButton = {
            RuntimeTextButton(onClick = onDismiss) { Text(stringResource(R.string.fbot_close)) }
        },
    )
}

@Composable
private fun WorkCard(assignment: BotAssignment, onCancel: (String) -> Unit) {
    var expanded by rememberSaveable(assignment.id) { mutableStateOf(false) }
    val statusColor = when (assignment.status) {
        BotAssignmentStatus.RUNNING -> MaterialTheme.colorScheme.primary
        BotAssignmentStatus.COMPLETED -> MaterialTheme.colorScheme.tertiary
        BotAssignmentStatus.FAILED -> MaterialTheme.colorScheme.error
        BotAssignmentStatus.CANCELLED -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val detail = assignment.summary?.takeIf { it.isNotBlank() } ?: assignment.error
    val timing = if (assignment.status == BotAssignmentStatus.RUNNING) {
        stringResource(
            R.string.fbot_work_running_for,
            formatDurationClock(System.currentTimeMillis() - assignment.startedAt),
        )
    } else {
        assignment.finishedAt
            ?.let { stringResource(R.string.fbot_work_took, formatDurationClock(it - assignment.startedAt)) }
            .orEmpty()
    }
    Surface(
        onClick = { expanded = !expanded },
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    assignment.botName,
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Surface(
                    color = statusColor.copy(alpha = 0.12f),
                    contentColor = statusColor,
                    shape = RoundedCornerShape(50),
                ) {
                    Text(
                        statusLabel(assignment.status),
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                    )
                }
                if (assignment.status == BotAssignmentStatus.RUNNING) {
                    RuntimeIconButton(
                        onClick = { onCancel(assignment.id) },
                        modifier = Modifier.size(32.dp),
                        contentDescription = stringResource(R.string.fbot_work_cancel),
                    ) {
                        RuntimeIcon(RuntimeIconName.Stop, Modifier.size(16.dp), tint = statusColor)
                    }
                }
            }
            Text(
                assignment.title,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = if (expanded) Int.MAX_VALUE else 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (timing.isNotBlank()) {
                Text(
                    timing,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (!detail.isNullOrBlank()) {
                Text(
                    detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = if (expanded) Int.MAX_VALUE else 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (!expanded) {
                Text(
                    stringResource(R.string.fbot_work_tap_detail),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

@Composable
private fun statusLabel(status: BotAssignmentStatus): String = when (status) {
    BotAssignmentStatus.RUNNING -> stringResource(R.string.fbot_work_status_running)
    BotAssignmentStatus.COMPLETED -> stringResource(R.string.fbot_work_status_completed)
    BotAssignmentStatus.FAILED -> stringResource(R.string.fbot_work_status_failed)
    BotAssignmentStatus.CANCELLED -> stringResource(R.string.fbot_work_status_cancelled)
}
