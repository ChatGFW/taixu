package top.wkbin.taixu.ui.settings

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import top.wkbin.taixu.ui.miuix.RuntimeTheme
import top.wkbin.taixu.ui.components.RuntimeSlider as Slider
import top.wkbin.taixu.ui.settings.LocalizedText as Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import top.wkbin.taixu.ui.components.RuntimeIconName

@Composable
internal fun BaseCommandTimeoutSliderRow(
    currentValue: Int,
    onValueChange: (Int) -> Unit,
) {
    var sliderMinutes by remember(currentValue) {
        mutableFloatStateOf((currentValue / 60f).coerceIn(1f, 60f))
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = SettingsDensity.rowHorizontalPadding, vertical = SettingsDensity.rowVerticalPadding),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("base 命令默认超时", modifier = Modifier.weight(1f), style = RuntimeTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium))
            Text(
                "${sliderMinutes.toInt()} 分钟",
                style = RuntimeTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium, fontFamily = FontFamily.Monospace),
                color = RuntimeTheme.colorScheme.primary,
            )
        }
        Text(
            "适用于普通前台命令；模型可为单次命令指定 1–60 分钟，常驻任务应使用 process 工具",
            style = settingsSupportingStyle(),
            color = RuntimeTheme.colorScheme.onSurfaceVariant,
        )
        Slider(
            value = sliderMinutes,
            onValueChange = { sliderMinutes = it },
            onValueChangeFinished = { onValueChange(sliderMinutes.toInt() * 60) },
            valueRange = 1f..60f,
            steps = 58,
        )
    }
}

@Composable
internal fun RoundsSliderRow(
    currentValue: Int,
    onValueChange: (Int) -> Unit,
) {
    var sliderVal by remember(currentValue) { mutableFloatStateOf(currentValue.toFloat()) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("单回合最大工具轮次上限", style = RuntimeTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium))
            Text(
                "${sliderVal.toInt()} 轮",
                style = RuntimeTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium, fontFamily = FontFamily.Monospace),
                color = RuntimeTheme.colorScheme.primary,
            )
        }
        Text(
            "防止复杂任务中模型陷入死循环；达到轮次后进入下方的自动续跑检查点",
            style = RuntimeTheme.typography.bodySmall,
            color = RuntimeTheme.colorScheme.onSurfaceVariant,
        )
        Slider(
            value = sliderVal,
            onValueChange = { sliderVal = it },
            onValueChangeFinished = { onValueChange(sliderVal.toInt()) },
            valueRange = 10f..300f,
            steps = 28, // 10, 20, 30 ... 300
        )
    }
}

@Composable
internal fun AgentSettingsGroup(content: @Composable () -> Unit) = SettingsGroup(content)

@Composable
internal fun AgentBlockTitle(title: String) {
    top.wkbin.taixu.ui.components.RuntimeSectionTitle(resolveLegacyString(title))
}

@Composable
internal fun AgentToggleRow(
    icon: RuntimeIconName, title: String, subtitle: String,
    checked: Boolean, onCheckedChange: (Boolean) -> Unit,
) = ToggleRow(icon, title, subtitle, checked, onCheckedChange)
