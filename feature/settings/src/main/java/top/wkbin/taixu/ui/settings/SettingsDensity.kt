package top.wkbin.taixu.ui.settings

import top.wkbin.taixu.ui.miuix.RuntimeSpacing
import top.wkbin.taixu.ui.miuix.RuntimeTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp

/** Default settings density; system font scaling still applies normally. */
internal object SettingsDensity {
    /** Home-page navigation entries: tonal icon tile, still above the M3 72dp two-line target. */
    val navRowMinHeight = 88.dp
    val navTileSize = 40.dp
    val navIconSize = 20.dp
    val rowHorizontalPadding = 16.dp
    val rowVerticalPadding = 16.dp
    val iconSize = 24.dp
    val iconGap = 12.dp
    val textGap = 4.dp
    val cardPadding = RuntimeSpacing.cardContent
    val sectionGap = RuntimeSpacing.cardGap
}

@Composable
internal fun settingsTitleStyle(): TextStyle = RuntimeTheme.typography.bodyLarge

@Composable
internal fun settingsSupportingStyle(): TextStyle = RuntimeTheme.typography.bodySmall
