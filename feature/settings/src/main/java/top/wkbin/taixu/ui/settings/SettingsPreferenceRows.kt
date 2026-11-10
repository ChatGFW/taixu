package top.wkbin.taixu.ui.settings

import top.wkbin.taixu.ui.miuix.RuntimeSpacing
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.LayoutDirection
import top.wkbin.taixu.ui.miuix.RuntimeTheme
import top.wkbin.taixu.ui.miuix.Switch
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.basic.ArrowRight
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

/** miuix 行内文案：标题保持库字阶，描述固定单行，超出省略，避免长描述挤成两行。 */
@Composable
private fun SettingsRowTexts(title: String, subtitle: String?, enabled: Boolean) {
    Text(
        title,
        fontSize = MiuixTheme.textStyles.headline1.fontSize,
        fontWeight = FontWeight.Medium,
        color = if (enabled) MiuixTheme.colorScheme.onBackground else MiuixTheme.colorScheme.disabledOnSecondaryVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
    if (!subtitle.isNullOrEmpty()) {
        Text(
            subtitle,
            fontSize = MiuixTheme.textStyles.body2.fontSize,
            color = if (enabled) MiuixTheme.colorScheme.onSurfaceVariantSummary else MiuixTheme.colorScheme.disabledOnSecondaryVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun RowScope.SettingsRowArrow(enabled: Boolean) {
    val tint = MiuixTheme.colorScheme.onSurfaceVariantActions
    val layoutDirection = LocalLayoutDirection.current
    Image(
        modifier = Modifier
            .size(width = 10.dp, height = 16.dp)
            .graphicsLayer { scaleX = if (layoutDirection == LayoutDirection.Rtl) -1f else 1f }
            .align(Alignment.CenterVertically),
        imageVector = MiuixIcons.Basic.ArrowRight,
        contentDescription = null,
        colorFilter = ColorFilter.tint(if (enabled) tint else MiuixTheme.colorScheme.disabledOnSecondaryVariant),
    )
}

@Composable
internal fun SettingsRow(
    icon: RuntimeIconName,
    title: String,
    subtitle: String,
    onClick: (() -> Unit)? = null,
    tonalIcon: Boolean = false,
    enabled: Boolean = true,
    trailing: @Composable RowScope.() -> Unit = {},
) {
    BasicComponent(
        onClick = onClick,
        enabled = enabled,
        startAction = { SettingsRowIcon(icon) },
        endActions = {
            Row(
                modifier = Modifier
                    .padding(end = 8.dp)
                    .align(Alignment.CenterVertically)
                    .weight(1f, fill = false),
            ) { trailing() }
            if (onClick != null) SettingsRowArrow(enabled)
        },
    ) {
        SettingsRowTexts(title, subtitle, enabled)
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
    BasicComponent(
        onClick = { if (enabled) change(!checked) },
        role = Role.Switch,
        enabled = enabled,
        startAction = { SettingsRowIcon(icon) },
        endActions = {
            Switch(checked = checked, onCheckedChange = change, enabled = enabled)
        },
    ) {
        SettingsRowTexts(title, subtitle, enabled)
    }
}

/** Row glyphs sit a step behind the title: softer ink and a fixed gap before the text column. */
@Composable
internal fun SettingsRowIcon(icon: RuntimeIconName) {
    RuntimeIcon(icon, Modifier.padding(end = RuntimeSpacing.rowIconEnd).size(RuntimeSpacing.rowIconSize), RuntimeTheme.colorScheme.onSurface)
}
