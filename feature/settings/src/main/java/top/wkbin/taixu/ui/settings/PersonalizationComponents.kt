package top.wkbin.taixu.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListScope
import top.wkbin.taixu.ui.miuix.*
import top.yukonga.miuix.kmp.basic.CardDefaults as MiuixCardDefaults
import top.yukonga.miuix.kmp.squircle.squircleSurface
import top.wkbin.taixu.ui.miuix.runtimeCardColor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import top.wkbin.taixu.ui.components.*
import top.wkbin.taixu.ui.settings.LocalizedText as Text

@Composable
internal fun PersonalizationScaffold(title: String, onBack: () -> Unit, content: LazyListScope.() -> Unit) =
    RuntimeSettingsScaffold(title, onBack, itemSpacing = SettingsDensity.sectionGap, content = content)

@Composable
internal fun PersonalizationSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column {
        RuntimeSectionTitle(resolveLegacyString(title))
        content()
    }
}

/** Consecutive segments join into one miuix card: only the group's outer corners are rounded. */
@Composable
internal fun PreferenceSurface(
    first: Boolean = true,
    last: Boolean = true,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val radius = MiuixCardDefaults.CornerRadius
    val top = if (first) radius else 0.dp
    val bottom = if (last) radius else 0.dp
    Column(
        modifier = modifier
            .fillMaxWidth()
            .squircleSurface(runtimeCardColor(), top, top, bottom, bottom)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        content = content,
    )
}

@Composable
internal fun PersonalizationToggle(
    icon: RuntimeIconName,
    title: String,
    subtitle: String,
    checked: Boolean,
    enabled: Boolean = true,
    onCheckedChange: (Boolean) -> Unit,
) {
    top.yukonga.miuix.kmp.preference.SwitchPreference(
        checked = checked,
        onCheckedChange = onCheckedChange,
        title = resolveLegacyString(title),
        summary = resolveLegacyString(subtitle),
        startAction = { SettingsRowIcon(icon) },
        enabled = enabled,
    )
}

@Composable
internal fun PreferenceText(title: String, subtitle: String, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(SettingsDensity.textGap)) {
        Text(title, style = settingsTitleStyle(), color = RuntimeTheme.colorScheme.onSurface)
        Text(subtitle, style = settingsSupportingStyle(), color = RuntimeTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
internal fun ThemeOptionRow(title: String, subtitle: String, selected: Boolean, onClick: () -> Unit) {
    top.yukonga.miuix.kmp.preference.RadioButtonPreference(
        selected = selected,
        onClick = onClick,
        title = resolveLegacyString(title),
        summary = resolveLegacyString(subtitle),
    )
}

@Composable
internal fun PreferenceSlider(
    title: String,
    valueLabel: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    steps: Int,
    onValueChange: (Float) -> Unit,
    onFinished: () -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(SettingsDensity.cardPadding), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(title, Modifier.weight(1f), style = settingsTitleStyle())
            Text(valueLabel, style = RuntimeTheme.typography.labelLarge, color = RuntimeTheme.colorScheme.primary)
        }
        RuntimeSlider(value = value, onValueChange = onValueChange, onValueChangeFinished = onFinished,
            valueRange = range, steps = steps, modifier = Modifier.fillMaxWidth().semantics { contentDescription = title })
    }
}
