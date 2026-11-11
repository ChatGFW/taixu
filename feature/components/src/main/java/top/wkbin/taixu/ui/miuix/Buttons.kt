package top.wkbin.taixu.ui.miuix

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.theme.LocalContentColor
import top.yukonga.miuix.kmp.basic.Button as MiuixButton
import top.yukonga.miuix.kmp.basic.IconButton as MiuixIconButton

typealias ButtonColors = top.yukonga.miuix.kmp.basic.ButtonColors

object ButtonDefaults {
    val shape @Composable get() = RuntimeTheme.shapes.medium
    val textShape @Composable get() = RuntimeTheme.shapes.medium
    val ContentPadding = top.yukonga.miuix.kmp.basic.ButtonDefaults.InsideMargin

    @Composable
    fun buttonColors(
        containerColor: Color = RuntimeTheme.colorScheme.primary,
        contentColor: Color = RuntimeTheme.colorScheme.onPrimary,
        disabledContainerColor: Color = if (containerColor == RuntimeTheme.colorScheme.primary)
            top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme.disabledPrimaryButton else containerColor.copy(alpha = 0.38f),
        disabledContentColor: Color = if (containerColor == RuntimeTheme.colorScheme.primary)
            top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme.disabledOnPrimaryButton else contentColor.copy(alpha = 0.38f),
    ) = ButtonColors(containerColor, disabledContainerColor, contentColor, disabledContentColor)

    @Composable
    fun filledTonalButtonColors(
        containerColor: Color = top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme.secondaryVariant,
        contentColor: Color = top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme.onSecondaryVariant,
    ) = buttonColors(containerColor, contentColor)

    @Composable
    fun outlinedButtonColors(
        containerColor: Color = top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme.secondaryVariant,
        contentColor: Color = top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme.onSecondaryVariant,
        disabledContentColor: Color = contentColor.copy(alpha = 0.38f),
        disabledContainerColor: Color = containerColor,
    ) = buttonColors(containerColor, contentColor, disabledContainerColor, disabledContentColor)

    /**
     * Text actions follow placement:
     * - dialog/sheet action row confirm → primary fill ([LocalDialogPrimaryAction])
     * - dialog/sheet action row dismiss, or any TextButton on a raised dialog/sheet body →
     *   grey fill ([LocalDialogAction] / [LocalRaisedSurface])
     * - elsewhere → borderless accent text (no grey pills next to page content)
     */
    @Composable
    fun textButtonColors(
        disabledContainerColor: Color = top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme.disabledSecondaryVariant,
        disabledContentColor: Color = top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme.disabledOnSecondaryVariant,
    ) = when {
        LocalDialogPrimaryAction.current -> buttonColors()
        LocalDialogAction.current || LocalRaisedSurface.current ->
            outlinedButtonColors(
                disabledContainerColor = disabledContainerColor,
                disabledContentColor = disabledContentColor,
            )
        else -> RuntimeTheme.colorScheme.primary.let { accent ->
            ButtonColors(Color.Transparent, Color.Transparent, accent, accent.copy(alpha = 0.38f))
        }
    }
}

@Composable
fun Button(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = ButtonDefaults.shape,
    colors: ButtonColors = ButtonDefaults.buttonColors(),
    border: BorderStroke? = null,
    contentPadding: PaddingValues = ButtonDefaults.ContentPadding,
    content: @Composable RowScope.() -> Unit,
) {
    val dialogChrome = LocalDialogAction.current || LocalRaisedSurface.current
    val buttonShape = if (dialogChrome) ButtonDefaults.shape else shape
    val radius = buttonShape.uniformRadius()
    MiuixButton(onClick, modifier.clip(buttonShape).cardOutline(buttonShape, radius, border), enabled,
        minHeight = RuntimeSpacing.buttonMinHeight,
        cornerRadius = radius ?: top.yukonga.miuix.kmp.basic.ButtonDefaults.CornerRadius,
        colors = colors,
        insideMargin = if (dialogChrome) ButtonDefaults.ContentPadding else contentPadding,
        content = content)
}

@Composable
fun FilledTonalButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = ButtonDefaults.shape,
    colors: ButtonColors = ButtonDefaults.filledTonalButtonColors(),
    border: BorderStroke? = null,
    contentPadding: PaddingValues = ButtonDefaults.ContentPadding,
    content: @Composable RowScope.() -> Unit,
) = Button(onClick, modifier, enabled, shape, colors, border, contentPadding, content)

@Composable
fun OutlinedButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = ButtonDefaults.shape,
    colors: ButtonColors = ButtonDefaults.outlinedButtonColors(),
    border: BorderStroke? = null,
    contentPadding: PaddingValues = ButtonDefaults.ContentPadding,
    content: @Composable RowScope.() -> Unit,
) = Button(onClick, modifier, enabled, shape, colors, border, contentPadding, content)

@Composable
fun TextButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = ButtonDefaults.textShape,
    colors: ButtonColors = ButtonDefaults.textButtonColors(),
    contentPadding: PaddingValues = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
    content: @Composable RowScope.() -> Unit,
) = Button(onClick, modifier, enabled, shape, colors, null, contentPadding, content)

data class IconButtonColors(val containerColor: Color, val contentColor: Color)

object IconButtonDefaults {
    @Composable
    fun filledIconButtonColors(containerColor: Color, contentColor: Color) = IconButtonColors(containerColor, contentColor)
}

@Composable
fun IconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable () -> Unit,
) = MiuixIconButton(onClick, modifier.clip(CircleShape).semantics {
        role = Role.Button
        if (!enabled) disabled()
    }, enabled, content = content)

@Composable
fun FilledIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    colors: IconButtonColors,
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(LocalContentColor provides colors.contentColor.copy(alpha = if (enabled) 1f else 0.38f)) {
        MiuixIconButton(onClick, modifier.clip(CircleShape).semantics {
        role = Role.Button
        if (!enabled) disabled()
    }.clip(CircleShape), enabled,
            backgroundColor = colors.containerColor.copy(alpha = if (enabled) 1f else 0.38f), content = content)
    }
}

fun Modifier.minimumInteractiveComponentSize() = sizeIn(minWidth = 48.dp, minHeight = 48.dp)
