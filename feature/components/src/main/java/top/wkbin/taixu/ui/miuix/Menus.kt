package top.wkbin.taixu.ui.miuix

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.PopupProperties
import top.yukonga.miuix.kmp.theme.LocalContentColor
import top.yukonga.miuix.kmp.window.WindowListPopup

data class MenuItemColors(val textColor: Color, val disabledTextColor: Color)

object MenuDefaults {
    val DropdownMenuItemContentPadding = PaddingValues(horizontal = 20.dp, vertical = 14.dp)
    @Composable
    fun itemColors(textColor: Color = RuntimeTheme.colorScheme.onSurface) =
        MenuItemColors(textColor, textColor.copy(alpha = 0.38f))
}

@Composable
fun DropdownMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    offset: DpOffset = DpOffset.Zero,
    properties: PopupProperties = PopupProperties(focusable = true),
    shape: Shape = RuntimeTheme.shapes.large,
    content: @Composable ColumnScope.() -> Unit,
) {
    WindowListPopup(show = expanded, onDismissRequest = onDismissRequest,
        popupModifier = modifier.offset(offset.x, offset.y)) {
        top.yukonga.miuix.kmp.basic.ListPopupColumn { Column(content = content) }
    }
}

@Composable
fun DropdownMenuItem(
    text: @Composable () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    leadingIcon: (@Composable () -> Unit)? = null,
    trailingIcon: (@Composable () -> Unit)? = null,
    enabled: Boolean = true,
    colors: MenuItemColors = MenuDefaults.itemColors(),
    contentPadding: PaddingValues = MenuDefaults.DropdownMenuItemContentPadding,
    interactionSource: MutableInteractionSource? = null,
) {
    CompositionLocalProvider(LocalContentColor provides if (enabled) colors.textColor else colors.disabledTextColor) {
        Row(modifier.fillMaxWidth().widthIn(min = 168.dp).heightIn(min = 48.dp)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick).padding(contentPadding),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            leadingIcon?.invoke()
            Box(Modifier.weight(1f)) { text() }
            trailingIcon?.invoke()
        }
    }
}

