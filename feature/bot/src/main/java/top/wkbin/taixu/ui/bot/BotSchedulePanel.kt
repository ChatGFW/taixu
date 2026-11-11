package top.wkbin.taixu.ui.bot

import top.wkbin.taixu.ui.miuix.RuntimeSpacing
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import top.wkbin.taixu.ui.miuix.RuntimeTheme
import top.wkbin.taixu.ui.components.RuntimeFilledTextField
import top.yukonga.miuix.kmp.basic.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import top.wkbin.taixu.core.model.bot.BotScheduleDelivery
import top.wkbin.taixu.core.model.bot.BotSchedule
import top.wkbin.taixu.core.model.bot.GroupChat
import top.wkbin.taixu.feature.bot.R
import top.wkbin.taixu.harness.bot.schedule.CronSchedule
import top.wkbin.taixu.ui.components.RuntimeAlertDialog
import top.wkbin.taixu.ui.components.RuntimeButton
import top.wkbin.taixu.ui.components.RuntimeCard
import top.wkbin.taixu.ui.components.RuntimeFilterChip
import top.wkbin.taixu.ui.components.RuntimeIcon
import top.wkbin.taixu.ui.components.RuntimeIconButton
import top.wkbin.taixu.ui.components.RuntimeIconName
import top.wkbin.taixu.ui.components.RuntimeSwitch
import top.wkbin.taixu.ui.components.RuntimeTextButton
import top.wkbin.taixu.ui.components.SectionHeader
import top.wkbin.taixu.ui.components.runtimeContentPadding

@Composable
internal fun BotSchedulePanel(
    schedules: List<BotSchedule>,
    deliveries: List<BotScheduleDelivery>,
    groups: List<GroupChat>,
    busy: Boolean,
    onAdd: (cron: String, instruction: String, groupId: String?) -> Unit,
    onToggle: (BotSchedule, Boolean) -> Unit,
    onDelete: (String) -> Unit,
) {
    var showAdd by rememberSaveable { mutableStateOf(false) }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = runtimeContentPadding(top = RuntimeSpacing.pageTop, bottom = RuntimeSpacing.pageBottom),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            SectionHeader(
                title = stringResource(R.string.fbot_tab_schedule),
                subtitle = "${schedules.size}",
                trailing = {
                    RuntimeButton(onClick = { showAdd = true }, enabled = !busy) {
                        Text(stringResource(R.string.fbot_schedule_add))
                    }
                },
            )
        }
        if (schedules.isEmpty()) {
            item {
                Text(
                    stringResource(R.string.fbot_schedule_empty),
                    style = RuntimeTheme.typography.bodySmall,
                    color = RuntimeTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        items(schedules, key = { it.id }) { schedule ->
            val groupName = groups.firstOrNull { it.id == schedule.groupId }?.name
            RuntimeCard(contentPadding = PaddingValues(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        schedule.cron,
                        style = RuntimeTheme.typography.labelMedium,
                        fontFamily = FontFamily.Monospace,
                        color = RuntimeTheme.colorScheme.primary,
                    )
                    Text(
                        stringResource(
                            R.string.fbot_schedule_next,
                            if (schedule.nextRunAt == Long.MAX_VALUE) "—" else formatEpoch(schedule.nextRunAt),
                        ),
                        style = RuntimeTheme.typography.labelSmall,
                        color = RuntimeTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f).padding(start = 10.dp),
                        maxLines = 1,
                    )
                    RuntimeSwitch(
                        checked = schedule.enabled,
                        onCheckedChange = { onToggle(schedule, it) },
                        enabled = !busy,
                    )
                    RuntimeIconButton(
                        onClick = { onDelete(schedule.id) },
                        contentDescription = stringResource(R.string.fbot_schedule_delete),
                        enabled = !busy,
                    ) {
                        RuntimeIcon(
                            RuntimeIconName.Trash,
                            Modifier.size(18.dp),
                            tint = RuntimeTheme.colorScheme.error,
                        )
                    }
                }
                Text(
                    schedule.instruction,
                    style = RuntimeTheme.typography.bodyMedium,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 6.dp),
                )
                deliveries.firstOrNull { it.scheduleId == schedule.id }?.let { delivery ->
                    val label = when (delivery.status) {
                        BotScheduleDelivery.ADMITTED -> R.string.fbot_delivery_admitted
                        BotScheduleDelivery.REJECTED -> R.string.fbot_delivery_rejected
                        BotScheduleDelivery.UNKNOWN -> R.string.fbot_delivery_unknown
                        else -> R.string.fbot_delivery_pending
                    }
                    Text(
                        stringResource(label, formatEpoch(delivery.scheduledAt)),
                        style = RuntimeTheme.typography.labelSmall,
                        color = if (delivery.status == BotScheduleDelivery.UNKNOWN) RuntimeTheme.colorScheme.error
                            else RuntimeTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (groupName != null) {
                    Text(
                        groupName,
                        style = RuntimeTheme.typography.labelSmall,
                        color = RuntimeTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }

    if (showAdd) {
        AddScheduleDialog(
            groups = groups,
            busy = busy,
            onDismiss = { showAdd = false },
            onAdd = { cron, instruction, groupId ->
                onAdd(cron, instruction, groupId)
                showAdd = false
            },
        )
    }
}

@Composable
private fun AddScheduleDialog(
    groups: List<GroupChat>,
    busy: Boolean,
    onDismiss: () -> Unit,
    onAdd: (cron: String, instruction: String, groupId: String?) -> Unit,
) {
    var cron by rememberSaveable { mutableStateOf("0 9 * * *") }
    var instruction by rememberSaveable { mutableStateOf("") }
    var groupId by rememberSaveable { mutableStateOf<String?>(null) }
    val cronError = remember(cron) { CronSchedule.validate(cron) }
    RuntimeAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.fbot_schedule_add)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                RuntimeFilledTextField(
                    value = cron,
                    onValueChange = { cron = it },
                    labelText = stringResource(R.string.fbot_schedule_cron),
                    placeholder = { Text(stringResource(R.string.fbot_schedule_cron_hint)) },
                    singleLine = true,
                    isError = cronError != null,
                    supportingText = cronError?.let { { Text(it, color = RuntimeTheme.colorScheme.error) } },
                    modifier = Modifier.fillMaxWidth(),
                )
                RuntimeFilledTextField(
                    value = instruction,
                    onValueChange = { instruction = it },
                    labelText = stringResource(R.string.fbot_schedule_instruction),
                    minLines = 3,
                    maxLines = 6,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (groups.isNotEmpty()) {
                    Text(
                        stringResource(R.string.fbot_schedule_group),
                        style = RuntimeTheme.typography.labelMedium,
                        color = RuntimeTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        RuntimeFilterChip(
                            selected = groupId == null,
                            onClick = { groupId = null },
                            label = { Text(stringResource(R.string.fbot_none)) },
                        )
                        groups.forEach { group ->
                            RuntimeFilterChip(
                                selected = groupId == group.id,
                                onClick = { groupId = group.id },
                                label = { Text(group.name) },
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            RuntimeTextButton(
                onClick = { onAdd(cron.trim(), instruction, groupId) },
                enabled = !busy && instruction.isNotBlank() && cronError == null,
            ) { Text(stringResource(R.string.fbot_create)) }
        },
        dismissButton = {
            RuntimeTextButton(onClick = onDismiss) { Text(stringResource(R.string.fbot_cancel)) }
        },
    )
}
