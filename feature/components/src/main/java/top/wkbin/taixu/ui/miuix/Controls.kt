package top.wkbin.taixu.ui.miuix

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

typealias SwitchColors = top.yukonga.miuix.kmp.basic.SwitchColors
typealias CheckboxColors = top.yukonga.miuix.kmp.basic.CheckboxColors
typealias RadioButtonColors = top.yukonga.miuix.kmp.basic.RadioButtonColors
typealias SliderColors = top.yukonga.miuix.kmp.basic.SliderColors

object SwitchDefaults {
    @Composable
    fun colors(
        checkedThumbColor: Color = RuntimeTheme.colorScheme.onPrimary,
        checkedTrackColor: Color = RuntimeTheme.colorScheme.primary,
    ) = top.yukonga.miuix.kmp.basic.SwitchDefaults.switchColors(
        checkedThumbColor = checkedThumbColor, checkedTrackColor = checkedTrackColor)
}

object CheckboxDefaults {
    @Composable fun colors() = top.yukonga.miuix.kmp.basic.CheckboxDefaults.checkboxColors()
}

object RadioButtonDefaults {
    @Composable fun colors() = top.yukonga.miuix.kmp.basic.RadioButtonDefaults.radioButtonColors()
}

object SliderDefaults {
    @Composable fun colors() = top.yukonga.miuix.kmp.basic.SliderDefaults.sliderColors()
}

@Composable
fun Switch(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    colors: SwitchColors = SwitchDefaults.colors(),
    thumbContent: (@Composable () -> Unit)? = null,
) = top.yukonga.miuix.kmp.basic.Switch(checked, onCheckedChange,
    if (onCheckedChange == null) modifier.clearAndSetSemantics {} else modifier, colors, enabled)

@Composable
fun Checkbox(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    colors: CheckboxColors = CheckboxDefaults.colors(),
) = top.yukonga.miuix.kmp.basic.Checkbox(
    state = ToggleableState(checked),
    onClick = onCheckedChange?.let { callback -> { callback(!checked) } },
    modifier = if (onCheckedChange == null) modifier.clearAndSetSemantics {} else modifier, colors = colors, enabled = enabled)

@Composable
fun RadioButton(
    selected: Boolean,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    colors: RadioButtonColors = RadioButtonDefaults.colors(),
) = top.yukonga.miuix.kmp.basic.RadioButton(selected, onClick,
    if (onClick == null) modifier.clearAndSetSemantics {} else modifier, colors, enabled)

@Composable
fun Slider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    steps: Int = 0,
    onValueChangeFinished: (() -> Unit)? = null,
    colors: SliderColors = SliderDefaults.colors(),
) = top.yukonga.miuix.kmp.basic.Slider(value, onValueChange, modifier, enabled,
    valueRange, steps, onValueChangeFinished, colors = colors)

@Composable
fun LinearProgressIndicator(
    progress: (() -> Float)? = null,
    modifier: Modifier = Modifier,
    color: Color = RuntimeTheme.colorScheme.primary,
    trackColor: Color = RuntimeTheme.colorScheme.surfaceContainerHighest,
) = top.yukonga.miuix.kmp.basic.LinearProgressIndicator(modifier, progress?.invoke(),
    colors = top.yukonga.miuix.kmp.basic.ProgressIndicatorDefaults.progressIndicatorColors(
        foregroundColor = color, backgroundColor = trackColor))

@Composable
fun CircularProgressIndicator(
    modifier: Modifier = Modifier,
    color: Color = top.yukonga.miuix.kmp.theme.LocalContentColor.current,
    trackColor: Color = Color.Transparent,
    strokeWidth: Dp = 4.dp,
) {
    BoxWithConstraints(modifier.size(24.dp)) {
        val indicatorSize = minOf(maxWidth, maxHeight)
        top.yukonga.miuix.kmp.basic.CircularProgressIndicator(
            modifier = Modifier.size(indicatorSize),
            size = indicatorSize,
            colors = top.yukonga.miuix.kmp.basic.ProgressIndicatorDefaults.progressIndicatorColors(
                foregroundColor = color, backgroundColor = trackColor),
            strokeWidth = strokeWidth.coerceAtMost(indicatorSize / 8),
        )
    }
}
