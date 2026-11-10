package top.wkbin.taixu.ui.settings

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import top.wkbin.taixu.ui.miuix.RuntimeSpacing
import top.wkbin.taixu.ui.components.RuntimeSwitchPreference

/** The form card supplies the horizontal inset, and the preference supplies its row spacing. */
@Composable
internal fun EditorToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    RuntimeSwitchPreference(
        title = resolveLegacyString(title),
        summary = resolveLegacyString(subtitle),
        checked = checked,
        onCheckedChange = onCheckedChange,
        insideMargin = PaddingValues(horizontal = 0.dp, vertical = RuntimeSpacing.cardContent),
    )
}
