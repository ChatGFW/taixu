package top.wkbin.taixu.ui.settings

import top.wkbin.taixu.ui.miuix.RuntimeSpacing
import androidx.compose.foundation.layout.*
import top.wkbin.taixu.ui.miuix.RuntimeTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.wkbin.taixu.ui.components.*
import top.wkbin.taixu.ui.settings.LocalizedText as Text

/** Shared density and color roles for settings pages, including legacy groups. */
@Composable
internal fun SettingsGroup(content: @Composable () -> Unit) {
    RuntimeCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RuntimeTheme.shapes.medium,
        containerColor = RuntimeTheme.colorScheme.surfaceContainer,
        contentPadding = PaddingValues(0.dp),
    ) {
        Column { content() }
    }
}

@Composable
internal fun SettingsRow(
    icon: RuntimeIconName,
    title: String,
    subtitle: String,
    value: String? = null,
    onClick: (() -> Unit)? = null,
    tonalIcon: Boolean = false,
    enabled: Boolean = true,
    trailing: @Composable RowScope.() -> Unit = {},
) {
    val startAction: @Composable () -> Unit = { SettingsRowIcon(icon) }
    val endActions: @Composable RowScope.() -> Unit = {
        value?.let {
            Text(it, Modifier.widthIn(max = 128.dp), style = MiuixTheme.textStyles.body2,
                color = MiuixTheme.colorScheme.onSurfaceVariantActions, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        trailing()
    }
    if (onClick == null) {
        top.yukonga.miuix.kmp.basic.BasicComponent(
            title = resolveLegacyString(title), summary = resolveLegacyString(subtitle),
            enabled = enabled, startAction = startAction, endActions = endActions,
        )
    } else {
        top.yukonga.miuix.kmp.preference.ArrowPreference(
            title = resolveLegacyString(title), summary = resolveLegacyString(subtitle),
            enabled = enabled, onClick = onClick, startAction = startAction, endActions = endActions,
        )
    }
}

@Composable
internal fun ToggleRow(
    icon: RuntimeIconName,
    title: String,
    subtitle: String,
    checked: Boolean,
    change: (Boolean) -> Unit,
    enabled: Boolean = true,
) {
    top.yukonga.miuix.kmp.preference.SwitchPreference(
        checked = checked,
        onCheckedChange = change,
        title = resolveLegacyString(title),
        summary = resolveLegacyString(subtitle),
        startAction = { SettingsRowIcon(icon) },
        enabled = enabled,
    )
}

/** Row glyphs sit a step behind the title: softer ink and a fixed gap before the text column. */
@Composable
internal fun SettingsRowIcon(icon: RuntimeIconName) {
    RuntimeIcon(icon, Modifier.padding(end = RuntimeSpacing.rowIconEnd).size(RuntimeSpacing.rowIconSize), RuntimeTheme.colorScheme.onSurface)
}
