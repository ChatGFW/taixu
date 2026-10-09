package top.wkbin.taixu.ui.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import top.wkbin.taixu.harness.workflow.SentinelState
import top.wkbin.taixu.ui.components.RuntimeAlertDialog
import top.wkbin.taixu.ui.components.RuntimeIcon
import top.wkbin.taixu.ui.components.RuntimeIconName
import top.wkbin.taixu.ui.components.RuntimeSwitch

/** 晨报哨兵控制行：开关 + 开启后展示定时与下次巡检，可改时间。 */
@Composable
internal fun SentinelControlRow(
    state: SentinelState,
    onEnable: (hour: Int, minute: Int) -> Unit,
    onDisable: () -> Unit,
) {
    var showTimeEditor by remember { mutableStateOf(false) }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center,
                ) {
                    RuntimeIcon(name = RuntimeIconName.Alarm, modifier = Modifier.size(15.dp), tint = MaterialTheme.colorScheme.primary)
                }
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(1.dp),
                ) {
                    Text(
                        text = "晨报哨兵",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    )
                    Text(
                        text = "每天定时只读巡检工作区，AI 汇总晨间简报",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    )
                }
                RuntimeSwitch(
                    checked = state.enabled,
                    onCheckedChange = { checked -> if (checked) onEnable(state.hour, state.minute) else onDisable() },
                )
            }

            AnimatedVisibility(
                visible = state.enabled,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut(),
            ) {
                Column(modifier = Modifier.padding(start = 50.dp, end = 12.dp, bottom = 10.dp)) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                    Spacer(Modifier.size(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                text = "每天 ${formatSentinelClock(state.hour, state.minute)} 自动巡检",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            state.nextRunAt?.let { next ->
                                Text(
                                    text = "下次巡检：${formatSentinelTimestamp(next)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        TextButton(onClick = { showTimeEditor = true }) {
                            Text("改时间")
                        }
                    }
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
                onEnable(hour, minute)
            },
            onDismiss = { showTimeEditor = false },
        )
    }
}

/** 时/分手动输入弹窗（0-23 / 0-59 校验）。 */
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
        title = { Text("巡检时间") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = hourText,
                        onValueChange = { hourText = it.filter(Char::isDigit).take(2) },
                        label = { Text("时 (0–23)") },
                        isError = hour !in 0..23,
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = minuteText,
                        onValueChange = { minuteText = it.filter(Char::isDigit).take(2) },
                        label = { Text("分 (0–59)") },
                        isError = minute !in 0..59,
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(enabled = valid, onClick = { onConfirm(hour ?: 0, minute ?: 0) }) {
                Text("保存")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )
}

private fun formatSentinelClock(hour: Int, minute: Int): String =
    "%02d:%02d".format(Locale.getDefault(), hour, minute)

private fun formatSentinelTimestamp(epochMillis: Long): String =
    SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date(epochMillis))
