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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import top.wkbin.taixu.core.model.bot.Bot
import top.wkbin.taixu.core.model.bot.BotAssignment
import top.wkbin.taixu.core.model.bot.BotAssignmentStatus
import top.wkbin.taixu.core.model.bot.GroupMessage
import top.wkbin.taixu.feature.bot.R
import top.wkbin.taixu.ui.components.RuntimeAlertDialog
import top.wkbin.taixu.ui.components.RuntimeCircularProgressIndicator
import top.wkbin.taixu.ui.components.RuntimeIcon
import top.wkbin.taixu.ui.components.RuntimeIconButton
import top.wkbin.taixu.ui.components.RuntimeIconName
import top.wkbin.taixu.ui.components.RuntimeTextButton

/** 群聊「工作」面板：成员实时状态 + 持久化委派进度，支持展开详情与取消。 */
@Composable
internal fun GroupWorkDialog(
    assignments: List<BotAssignment>,
    members: List<Bot>,
    messages: List<GroupMessage>,
    onDismiss: () -> Unit,
    onCancel: (String) -> Unit,
) {
    val running = assignments.count { it.status == BotAssignmentStatus.RUNNING }
    val finished = assignments.size - running
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1_000)
            now = System.currentTimeMillis()
        }
    }
    val runningByBot = remember(assignments) {
        assignments.filter { it.status == BotAssignmentStatus.RUNNING }.associateBy { it.botId }
    }
    val streamingByBot = remember(messages) {
        messages.filter { it.isStreaming }.associateBy { it.senderId }
    }
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
            LazyColumn(
                modifier = Modifier.heightIn(max = 480.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item(key = "members-title") {
                    SectionTitle(stringResource(R.string.fbot_work_members_live))
                }
                if (members.isEmpty()) {
                    item(key = "members-empty") {
                        Text(
                            stringResource(R.string.fbot_work_empty),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else {
                    items(members, key = { "member:${it.id}" }) { bot ->
                        MemberStatusRow(
                            bot = bot,
                            assignment = runningByBot[bot.id],
                            streaming = streamingByBot[bot.id],
                        )
                    }
                }
                item(key = "assignments-title") {
                    SectionTitle(stringResource(R.string.fbot_work_assignments))
                }
                if (assignments.isEmpty()) {
                    item(key = "assignments-empty") {
                        Text(
                            stringResource(R.string.fbot_work_empty),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else {
                    items(assignments, key = { "assignment:${it.id}" }) { assignment ->
                        WorkCard(
                            assignment = assignment,
                            liveText = if (assignment.status == BotAssignmentStatus.RUNNING) {
                                liveStatusText(streamingByBot[assignment.botId], assignment)
                            } else {
                                null
                            },
                            now = now,
                            onCancel = onCancel,
                        )
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
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 2.dp),
    )
}

@Composable
private fun MemberStatusRow(
    bot: Bot,
    assignment: BotAssignment?,
    streaming: GroupMessage?,
) {
    val active = assignment != null || streaming != null
    val status = liveStatusText(streaming, assignment)
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BotAvatar(bot, 30.dp, active = active)
        Column(Modifier.weight(1f).padding(start = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = bot.name,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (bot.isMainBot) {
                    Text(
                        text = " · ${stringResource(R.string.fbot_main_bot)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            if (status != null) {
                Text(
                    text = status,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            assignment?.let {
                Text(
                    text = it.title,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (active) {
            RuntimeCircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp)
        } else {
            Text(
                text = stringResource(R.string.fbot_member_idle),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline,
            )
        }
    }
}

@Composable
private fun WorkCard(
    assignment: BotAssignment,
    liveText: String?,
    now: Long,
    onCancel: (String) -> Unit,
) {
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
            formatDurationClock(now - assignment.startedAt),
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
            if (!liveText.isNullOrBlank()) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    RuntimeCircularProgressIndicator(Modifier.size(11.dp), strokeWidth = 1.5.dp)
                    Text(
                        text = liveText,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
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
private fun liveStatusText(message: GroupMessage?, assignment: BotAssignment?): String? {
    if (message == null) {
        return assignment?.let { stringResource(R.string.fbot_member_waiting_start) }
    }
    val step = message.steps.lastOrNull()
    return when {
        step != null && step.success == null -> stringResource(R.string.fbot_member_step, step.name)
        message.content.isNotBlank() -> stringResource(R.string.fbot_member_outputting)
        message.reasoning.isNotBlank() -> stringResource(R.string.fbot_member_thinking)
        else -> stringResource(R.string.fbot_member_working)
    }
}

@Composable
private fun statusLabel(status: BotAssignmentStatus): String = when (status) {
    BotAssignmentStatus.RUNNING -> stringResource(R.string.fbot_work_status_running)
    BotAssignmentStatus.COMPLETED -> stringResource(R.string.fbot_work_status_completed)
    BotAssignmentStatus.FAILED -> stringResource(R.string.fbot_work_status_failed)
    BotAssignmentStatus.CANCELLED -> stringResource(R.string.fbot_work_status_cancelled)
}
