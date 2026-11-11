package top.wkbin.taixu.feature.a2uipoc.miuix

import androidx.a2ui.compose.runtime.A2uiComponentScope
import androidx.a2ui.compose.ui.catalog.A2uiBasicCatalogV1
import androidx.a2ui.model.protocol.A2uiException
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import top.wkbin.taixu.ui.components.RuntimeAlertDialog
import top.wkbin.taixu.ui.components.RuntimeButton
import top.wkbin.taixu.ui.components.RuntimeTextButton
import top.wkbin.taixu.ui.miuix.RuntimeTheme
import top.yukonga.miuix.kmp.basic.NumberPicker
import top.yukonga.miuix.kmp.basic.Text

internal object MiuixA2uiBasicCatalogV1DateTimeInput : A2uiBasicCatalogV1.DateTimeInput {
    @Composable
    override fun A2uiComponentScope.TypedContent(
        value: Long?,
        onValueChange: ((Long?) -> Unit)?,
        enableDate: Boolean,
        enableTime: Boolean,
        min: Long?,
        max: Long?,
        label: String?,
        accessibility: A2uiBasicCatalogV1.AccessibilityAttributes?,
        checks: List<A2uiBasicCatalogV1.CheckRule>,
        modifier: Modifier,
    ) {
        var open by rememberSaveable { mutableStateOf(false) }
        val invalidRange = min != null && max != null && min > max
        if (invalidRange) SideEffect(min, max) {
            reportError(A2uiException.A2uiRuntimeException("Min value cannot be greater than max value."))
        }
        val current = remember(value) { LocalDateTime.ofInstant(Instant.ofEpochMilli(value ?: System.currentTimeMillis()), ZoneOffset.UTC) }
        val formatter = when {
            enableDate && enableTime -> DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT)
            enableDate -> DateTimeFormatter.ofLocalizedDate(FormatStyle.SHORT)
            else -> DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)
        }.withLocale(Locale.getDefault())
        Column(modifier.a2uiAccessibility(accessibility), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            label?.let { Text(it) }
            RuntimeButton(onClick = { open = true }, enabled = onValueChange != null && !invalidRange && (enableDate || enableTime)) {
                Text(current.format(formatter))
            }
            checks.firstOrNull { !it.condition }?.message?.let { Text(it, color = RuntimeTheme.colorScheme.error) }
        }
        if (open) DateTimeSelection(current, enableDate, enableTime, min, max,
            onDismiss = { open = false }, onConfirm = { onValueChange?.invoke(it); open = false })
    }
}

/** A2UI dates use UTC milliseconds. Miuix wheels edit a draft until confirmation. */
@Composable
private fun DateTimeSelection(
    initial: LocalDateTime,
    enableDate: Boolean,
    enableTime: Boolean,
    min: Long?,
    max: Long?,
    onDismiss: () -> Unit,
    onConfirm: (Long) -> Unit,
) {
    var year by rememberSaveable { mutableIntStateOf(initial.year) }
    var month by rememberSaveable { mutableIntStateOf(initial.monthValue) }
    var day by rememberSaveable { mutableIntStateOf(initial.dayOfMonth) }
    var hour by rememberSaveable { mutableIntStateOf(initial.hour) }
    var minute by rememberSaveable { mutableIntStateOf(initial.minute) }
    val days = java.time.YearMonth.of(year, month).lengthOfMonth()
    val selectedDay = day.coerceAtMost(days)
    val candidate = LocalDateTime.of(year, month, selectedDay, hour, minute).toInstant(ZoneOffset.UTC).toEpochMilli()
    val valid = (min == null || candidate >= min) && (max == null || candidate <= max)
    RuntimeAlertDialog(onDismissRequest = onDismiss,
        confirmButton = {
            RuntimeTextButton(onClick = { onConfirm(candidate) }, enabled = valid) { Text(androidx.compose.ui.res.stringResource(android.R.string.ok)) }
        },
        dismissButton = {
            RuntimeTextButton(onClick = onDismiss) { Text(androidx.compose.ui.res.stringResource(android.R.string.cancel)) }
        },
        text = {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (enableDate) Row(Modifier.fillMaxWidth()) {
                    NumberPicker(year, { year = it }, Modifier.weight(1.4f), range = 1..9999)
                    NumberPicker(month, { month = it }, Modifier.weight(1f), range = 1..12)
                    NumberPicker(selectedDay, { day = it }, Modifier.weight(1f), range = 1..days)
                }
                if (enableTime) Row(Modifier.fillMaxWidth()) {
                    NumberPicker(hour, { hour = it }, Modifier.weight(1f), range = 0..23)
                    Text(":", modifier = Modifier.align(androidx.compose.ui.Alignment.CenterVertically))
                    NumberPicker(minute, { minute = it }, Modifier.weight(1f), range = 0..59)
                }
            }
        })
}
