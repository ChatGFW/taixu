package top.wkbin.taixu.ui.settings

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import top.wkbin.taixu.ui.components.RuntimeIconName
import top.wkbin.taixu.ui.components.RuntimeSectionTitle

@Composable
internal fun BaseCommandTimeoutSliderRow(
    currentValue: Int,
    onValueChange: (Int) -> Unit,
) {
    var sliderMinutes by remember(currentValue) {
        mutableFloatStateOf((currentValue / 60f).coerceIn(1f, 60f))
    }
    top.wkbin.taixu.ui.components.RuntimeSliderPreference(
        value = sliderMinutes,
        onValueChange = { sliderMinutes = it },
        modifier = Modifier.fillMaxWidth(),
        title = "base 命令默认超时",
        summary = "适用于普通前台命令；模型可为单次命令指定 1–60 分钟，常驻任务应使用 process 工具",
        valueText = "${sliderMinutes.toInt()} 分钟",
        valueRange = 1f..60f,
        steps = 58,
        onValueChangeFinished = { onValueChange(sliderMinutes.toInt() * 60) },
    )
}

@Composable
internal fun RoundsSliderRow(
    currentValue: Int,
    onValueChange: (Int) -> Unit,
) {
    var sliderVal by remember(currentValue) { mutableFloatStateOf(currentValue.toFloat()) }
    top.wkbin.taixu.ui.components.RuntimeSliderPreference(
        value = sliderVal,
        onValueChange = { sliderVal = it },
        modifier = Modifier.fillMaxWidth(),
        title = "单回合最大工具轮次上限",
        summary = "防止复杂任务中模型陷入死循环；达到轮次后进入下方的自动续跑检查点",
        valueText = "${sliderVal.toInt()} 轮",
        valueRange = 10f..300f,
        steps = 28,
        onValueChangeFinished = { onValueChange(sliderVal.toInt()) },
    )
}

@Composable
internal fun AgentSettingsGroup(content: @Composable () -> Unit) = SettingsGroup(content)

@Composable
internal fun AgentBlockTitle(title: String) {
    RuntimeSectionTitle(resolveLegacyString(title))
}

@Composable
internal fun AgentToggleRow(
    icon: RuntimeIconName, title: String, subtitle: String,
    checked: Boolean, onCheckedChange: (Boolean) -> Unit,
) = ToggleRow(icon, title, subtitle, checked, onCheckedChange)
