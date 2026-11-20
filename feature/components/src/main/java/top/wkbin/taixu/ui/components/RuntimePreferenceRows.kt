package top.wkbin.taixu.ui.components

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import top.wkbin.taixu.ui.miuix.Checkbox
import top.wkbin.taixu.ui.miuix.RadioButton
import top.wkbin.taixu.ui.miuix.Slider
import top.wkbin.taixu.ui.miuix.Switch
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.BasicComponentDefaults
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * miuix preference 的字符串重载不限制标题/副标题行数，长文案会换行撑高行。
 * 这里的包装统一改为单行省略，其余布局与交互语义保持与 miuix 原组件一致。
 */
@Composable
internal fun PreferenceTexts(title: String, summary: String?, enabled: Boolean) {
    Text(
        title,
        fontSize = MiuixTheme.textStyles.headline1.fontSize,
        fontWeight = FontWeight.Medium,
        color = if (enabled) MiuixTheme.colorScheme.onBackground else MiuixTheme.colorScheme.disabledOnSecondaryVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
    if (!summary.isNullOrEmpty()) {
        Text(
            summary,
            fontSize = MiuixTheme.textStyles.body2.fontSize,
            color = if (enabled) MiuixTheme.colorScheme.onSurfaceVariantSummary else MiuixTheme.colorScheme.disabledOnSecondaryVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
fun RuntimeCheckboxPreference(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    startAction: @Composable (() -> Unit)? = null,
    insideMargin: PaddingValues = BasicComponentDefaults.InsideMargin,
) {
    val currentOnCheckedChange by rememberUpdatedState(onCheckedChange)
    BasicComponent(
        modifier = modifier,
        insideMargin = insideMargin,
        startAction = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(
                    modifier = Modifier.padding(end = 5.dp),
                    checked = checked,
                    onCheckedChange = currentOnCheckedChange,
                    enabled = enabled,
                )
                startAction?.invoke()
            }
        },
        onClick = { if (enabled) currentOnCheckedChange(!checked) },
        role = Role.Checkbox,
        enabled = enabled,
    ) {
        PreferenceTexts(title, null, enabled)
    }
}

@Composable
fun RuntimeArrowPreference(
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    summary: String? = null,
    enabled: Boolean = true,
    startAction: @Composable (() -> Unit)? = null,
    endActions: @Composable RowScope.() -> Unit = {},
    insideMargin: PaddingValues = BasicComponentDefaults.InsideMargin,
) {
    BasicComponent(
        modifier = modifier,
        insideMargin = insideMargin,
        startAction = startAction,
        endActions = {
            Row(
                modifier = Modifier
                    .padding(end = 8.dp)
                    .align(Alignment.CenterVertically)
                    .weight(1f, fill = false),
                content = endActions,
            )
            val tint = if (enabled) MiuixTheme.colorScheme.onSurfaceVariantActions else MiuixTheme.colorScheme.disabledOnSecondaryVariant
            Image(
                modifier = Modifier
                    .size(width = 10.dp, height = 16.dp)
                    .align(Alignment.CenterVertically),
                imageVector = runtimeIconImageVector(RuntimeIconName.ChevronRight),
                contentDescription = null,
                colorFilter = ColorFilter.tint(tint),
            )
        },
        onClick = onClick,
        enabled = enabled,
    ) {
        PreferenceTexts(title, summary, enabled)
    }
}
@Composable
fun RuntimeSwitchPreference(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    summary: String? = null,
    enabled: Boolean = true,
    startAction: @Composable (() -> Unit)? = null,
    insideMargin: PaddingValues = BasicComponentDefaults.InsideMargin,
) {
    val currentOnCheckedChange by rememberUpdatedState(onCheckedChange)
    BasicComponent(
        modifier = modifier,
        insideMargin = insideMargin,
        startAction = startAction,
        endActions = {
            Switch(checked = checked, onCheckedChange = currentOnCheckedChange, enabled = enabled)
        },
        onClick = { if (enabled) currentOnCheckedChange(!checked) },
        role = Role.Switch,
        enabled = enabled,
    ) {
        PreferenceTexts(title, summary, enabled)
    }
}
@Composable
fun RuntimeRadioPreference(
    title: String,
    selected: Boolean,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    summary: String? = null,
    enabled: Boolean = true,
    startAction: @Composable (() -> Unit)? = null,
    insideMargin: PaddingValues = BasicComponentDefaults.InsideMargin,
) {
    val currentOnClick by rememberUpdatedState(onClick)
    BasicComponent(
        modifier = modifier,
        insideMargin = insideMargin,
        startAction = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                RadioButton(
                    modifier = Modifier.padding(end = 5.dp),
                    selected = selected,
                    onClick = currentOnClick?.let { { it() } },
                    enabled = enabled,
                )
                startAction?.invoke()
            }
        },
        onClick = { if (enabled) currentOnClick?.invoke() },
        role = Role.RadioButton,
        enabled = enabled,
    ) {
        PreferenceTexts(title, summary, enabled)
    }
}

@Composable
fun RuntimeSliderPreference(
    value: Float,
    onValueChange: (Float) -> Unit,
    title: String,
    modifier: Modifier = Modifier,
    summary: String? = null,
    valueText: String? = null,
    enabled: Boolean = true,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    steps: Int = 0,
    onValueChangeFinished: (() -> Unit)? = null,
    startAction: @Composable (() -> Unit)? = null,
    insideMargin: PaddingValues = BasicComponentDefaults.InsideMargin,
) {
    val currentOnValueChange by rememberUpdatedState(onValueChange)
    val currentOnValueChangeFinished by rememberUpdatedState(onValueChangeFinished)
    BasicComponent(
        modifier = modifier,
        insideMargin = insideMargin,
        startAction = startAction,
        endActions = if (valueText != null) {
            {
                Text(
                    valueText,
                    modifier = Modifier
                        .padding(end = 8.dp)
                        .align(Alignment.CenterVertically)
                        .weight(1f, fill = false),
                    fontSize = MiuixTheme.textStyles.body2.fontSize,
                    color = if (enabled) MiuixTheme.colorScheme.onSurfaceVariantActions else MiuixTheme.colorScheme.disabledOnSecondaryVariant,
                    textAlign = TextAlign.End,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        } else {
            null
        },
        bottomAction = {
            Slider(
                value = value,
                onValueChange = { currentOnValueChange(it) },
                modifier = Modifier.fillMaxWidth(),
                enabled = enabled,
                valueRange = valueRange,
                steps = steps,
                onValueChangeFinished = currentOnValueChangeFinished,
            )
        },
        enabled = enabled,
    ) {
        PreferenceTexts(title, summary, enabled)
    }
}
