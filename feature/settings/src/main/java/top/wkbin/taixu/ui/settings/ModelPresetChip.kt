package top.wkbin.taixu.ui.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.padding
import top.wkbin.taixu.ui.miuix.RuntimeTheme
import top.wkbin.taixu.ui.miuix.Surface
import top.yukonga.miuix.kmp.basic.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
internal fun PresetChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        shape = RuntimeTheme.shapes.chip,
        color = if (selected) RuntimeTheme.colorScheme.primaryContainer else RuntimeTheme.colorScheme.surfaceContainerHigh,
        border = BorderStroke(1.dp, if (selected) RuntimeTheme.colorScheme.primary else Color.Transparent),
        modifier = Modifier.clickable(onClick = onClick),
    ) {
        Text(
            text = label,
            style = RuntimeTheme.typography.labelSmall.copy(
                fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
            ),
            color = if (selected) RuntimeTheme.colorScheme.onPrimaryContainer else RuntimeTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
        )
    }
}
