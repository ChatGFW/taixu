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

/**
 * 不确定加载：走 miuix [InfiniteProgressIndicator]（圆环 + 轨道点），
 * 不是弧形扫过的 [top.yukonga.miuix.kmp.basic.CircularProgressIndicator]。
 * [trackColor] 保留兼容，圆环加载不用轨道色。
 */
@Composable
fun CircularProgressIndicator(
    modifier: Modifier = Modifier,
    color: Color = RuntimeTheme.colorScheme.onSurfaceVariant,
    trackColor: Color = Color.Transparent,
    strokeWidth: Dp = 2.dp,
) {
    BoxWithConstraints(modifier.size(20.dp)) {
        val indicatorSize = minOf(maxWidth, maxHeight)
        val ring = strokeWidth.coerceIn(1.dp, (indicatorSize / 8).coerceAtLeast(1.dp))
        val dot = (indicatorSize / 10).coerceIn(1.5.dp, 3.dp)
        top.yukonga.miuix.kmp.basic.InfiniteProgressIndicator(
            modifier = Modifier.size(indicatorSize),
            color = color,
            size = indicatorSize,
            strokeWidth = ring,
            orbitingDotSize = dot,
        )
    }
}

/** 确定进度圆环（扫弧）；不确定加载请用 [CircularProgressIndicator]。 */
@Composable
fun DeterminateCircularProgressIndicator(
    progress: Float,
    modifier: Modifier = Modifier,
    color: Color = RuntimeTheme.colorScheme.primary,
    trackColor: Color = RuntimeTheme.colorScheme.secondaryContainer,
    strokeWidth: Dp = 4.dp,
) {
    BoxWithConstraints(modifier.size(30.dp)) {
        val indicatorSize = minOf(maxWidth, maxHeight)
        top.yukonga.miuix.kmp.basic.CircularProgressIndicator(
            modifier = Modifier.size(indicatorSize),
            progress = progress.coerceIn(0f, 1f),
            size = indicatorSize,
            colors = top.yukonga.miuix.kmp.basic.ProgressIndicatorDefaults.progressIndicatorColors(
                foregroundColor = color, backgroundColor = trackColor),
            strokeWidth = strokeWidth.coerceAtMost(indicatorSize / 8),
        )
    }
}
