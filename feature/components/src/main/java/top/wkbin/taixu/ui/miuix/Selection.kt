package top.wkbin.taixu.ui.miuix

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp

data class ChipColors(val containerColor: Color, val labelColor: Color,
    val selectedContainerColor: Color, val selectedLabelColor: Color)

object FilterChipDefaults {
    @Composable
    fun filterChipColors(
        containerColor: Color = RuntimeTheme.colorScheme.surfaceContainerHigh,
        labelColor: Color = RuntimeTheme.colorScheme.onSurface,
        selectedContainerColor: Color = RuntimeTheme.colorScheme.tertiaryContainer,
        selectedLabelColor: Color = RuntimeTheme.colorScheme.onTertiaryContainer,
        selectedLeadingIconColor: Color = selectedLabelColor,
    ) = ChipColors(containerColor, labelColor, selectedContainerColor, selectedLabelColor)
}

@Composable
fun FilterChip(
    selected: Boolean,
    onClick: () -> Unit,
    label: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    leadingIcon: (@Composable () -> Unit)? = null,
    trailingIcon: (@Composable () -> Unit)? = null,
    shape: Shape = RuntimeTheme.shapes.small,
    colors: ChipColors = FilterChipDefaults.filterChipColors(),
) {
    Surface(modifier = modifier.selectable(selected, enabled = enabled, role = Role.Checkbox, onClick = onClick),
        shape = shape, color = if (selected) colors.selectedContainerColor else colors.containerColor,
        contentColor = if (selected) colors.selectedLabelColor else colors.labelColor) {
        Row(Modifier.heightIn(min = 36.dp).padding(horizontal = 14.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            leadingIcon?.invoke()
            label()
            trailingIcon?.invoke()
        }
    }
}

object SuggestionChipDefaults { val Height = 40.dp }

@Composable
fun SuggestionChip(
    onClick: () -> Unit,
    label: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: (@Composable () -> Unit)? = null,
    shape: Shape = RuntimeTheme.shapes.small,
) = FilterChip(false, onClick, label, modifier, enabled, leadingIcon = icon, shape = shape)

@Composable
fun AssistChip(
    onClick: () -> Unit,
    label: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    leadingIcon: (@Composable () -> Unit)? = null,
) = FilterChip(false, onClick, label, modifier, enabled, leadingIcon)

@Composable
fun Badge(
    modifier: Modifier = Modifier,
    containerColor: Color = RuntimeTheme.colorScheme.error,
    contentColor: Color = RuntimeTheme.colorScheme.onError,
    content: (@Composable RowScope.() -> Unit)? = null,
) {
    top.yukonga.miuix.kmp.basic.Badge(modifier, containerColor, contentColor, content)
}

@Composable
fun Tab(
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    text: (@Composable () -> Unit)? = null,
    icon: (@Composable () -> Unit)? = null,
    selectedContentColor: Color = RuntimeTheme.colorScheme.onPrimaryContainer,
    unselectedContentColor: Color = RuntimeTheme.colorScheme.onSurfaceVariant,
) {
    Surface(modifier = modifier.selectable(selected, enabled = enabled, role = Role.Tab, onClick = onClick),
        shape = RuntimeTheme.shapes.small,
        color = if (selected) RuntimeTheme.colorScheme.primaryContainer else Color.Transparent,
        contentColor = if (selected) selectedContentColor else unselectedContentColor) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            icon?.invoke()
            text?.invoke()
        }
    }
}
