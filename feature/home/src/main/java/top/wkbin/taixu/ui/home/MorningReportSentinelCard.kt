package top.wkbin.taixu.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import top.wkbin.taixu.feature.home.R
import top.wkbin.taixu.ui.components.RuntimeAlertDialog
import top.wkbin.taixu.ui.components.RuntimeCard
import top.wkbin.taixu.ui.components.RuntimeIcon
import top.wkbin.taixu.ui.components.RuntimeIconName
import top.wkbin.taixu.ui.components.RuntimeSwitch

/**
 * 晨报哨兵入口卡：每日定时对当前工作区做只读巡检并推送通知。
 * 开关与时间直接读写一条 DAILY 定时计划（workflowId = morning_report_sentinel）。
 */
@Composable
fun MorningReportSentinelCard(
    state: SentinelState,
    onEnable: () -> Unit,
    onDisable: () -> Unit,
    onTimeChange: (hour: Int, minute: Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    var showTimeEditor by remember { mutableStateOf(false) }

    RuntimeCard(modifier = modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            RuntimeIcon(
                name = RuntimeIconName.Alarm,
                modifier = Modifier.size(22.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.size(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.home_sentinel_title),
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = stringResource(R.string.home_sentinel_subtitle),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.size(8.dp))
            RuntimeSwitch(
                checked = state.enabled,
                onCheckedChange = { checked -> if (checked) onEnable() else onDisable() },
            )
        }
        if (state.enabled) {
            Spacer(Modifier.size(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.home_sentinel_schedule, formatSentinelClock(state.hour, state.minute)),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    state.nextRunAt?.let { next ->
                        Text(
                            text = stringResource(R.string.home_sentinel_next_run, formatSentinelTimestamp(next)),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                TextButton(onClick = { showTimeEditor = true }) {
                    Text(stringResource(R.string.home_sentinel_time_edit))
                }
            }
        }
    }

    if (showTimeEditor) {
        SentinelTimeEditorDialog(
            initialHour = state.hour,
            initialMinute = state.minute,
            onConfirm = { hour, minute ->
                showTimeEditor = false
                onTimeChange(hour, minute)
            },
            onDismiss = { showTimeEditor = false },
        )
    }
}

/** 时/分手动输入弹窗（0-23 / 0-59 校验），与工作流定时计划的 DAILY 编辑保持同一交互习惯。 */
@Composable
private fun SentinelTimeEditorDialog(
    initialHour: Int,
    initialMinute: Int,
    onConfirm: (Int, Int) -> Unit,
    onDismiss: () -> Unit,
) {
    var hourText by remember { mutableStateOf(initialHour.toString()) }
    var minuteText by remember { mutableStateOf(initialMinute.toString()) }
    val hour = hourText.toIntOrNull()
    val minute = minuteText.toIntOrNull()
    val valid = hour in 0..23 && minute in 0..59

    RuntimeAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.home_sentinel_dialog_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = hourText,
                        onValueChange = { hourText = it.filter(Char::isDigit).take(2) },
                        label = { Text(stringResource(R.string.home_sentinel_hour_label)) },
                        isError = hour !in 0..23,
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = minuteText,
                        onValueChange = { minuteText = it.filter(Char::isDigit).take(2) },
                        label = { Text(stringResource(R.string.home_sentinel_minute_label)) },
                        isError = minute !in 0..59,
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(enabled = valid, onClick = { onConfirm(hour ?: 0, minute ?: 0) }) {
                Text(stringResource(R.string.home_sentinel_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.home_sentinel_cancel)) }
        },
    )
}

private fun formatSentinelClock(hour: Int, minute: Int): String = "%02d:%02d".format(Locale.getDefault(), hour, minute)

private fun formatSentinelTimestamp(epochMillis: Long): String =
    SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date(epochMillis))
