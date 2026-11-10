package top.wkbin.taixu.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import top.wkbin.taixu.ui.miuix.Button
import top.wkbin.taixu.ui.miuix.ButtonColors
import top.wkbin.taixu.ui.miuix.ButtonDefaults
import top.wkbin.taixu.ui.miuix.FilledIconButton
import top.wkbin.taixu.ui.miuix.FilledTonalButton
import top.wkbin.taixu.ui.miuix.IconButton
import top.wkbin.taixu.ui.miuix.IconButtonDefaults
import top.wkbin.taixu.ui.miuix.OutlinedButton
import top.wkbin.taixu.ui.miuix.RuntimeTheme
import top.wkbin.taixu.ui.miuix.TextButton

// Use the native expressive shape animation while keeping existing sizing and callbacks.
/**
 * 主操作按钮：tonal 为 true 时使用 FilledTonalButton，否则使用 Button。
 */
@Composable
fun RuntimeButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    tonal: Boolean = false,
    contentPadding: PaddingValues = ButtonDefaults.ContentPadding,
    shape: Shape = ButtonDefaults.shape,
    colors: ButtonColors? = null,
    border: BorderStroke? = null,
    content: @Composable RowScope.() -> Unit,
) {
    if (tonal) {
        FilledTonalButton(
            onClick = onClick,
            modifier = modifier,
            enabled = enabled,
            shape = shape,
            colors = colors ?: ButtonDefaults.filledTonalButtonColors(),
            border = border,
            contentPadding = contentPadding,
            content = content,
        )
    } else {
        Button(
            onClick = onClick,
            modifier = modifier,
            enabled = enabled,
            shape = shape,
            colors = colors ?: ButtonDefaults.buttonColors(),
            border = border,
            contentPadding = contentPadding,
            content = content,
        )
    }
}

@Composable
fun RuntimeOutlinedButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = ButtonDefaults.shape,
    colors: ButtonColors = ButtonDefaults.outlinedButtonColors(),
    border: BorderStroke? = null,
    contentPadding: PaddingValues = ButtonDefaults.ContentPadding,
    content: @Composable RowScope.() -> Unit,
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        shape = shape,
        colors = colors,
        border = border,
        contentPadding = contentPadding,
        content = content,
    )
}

@Composable
fun RuntimeFilledTonalButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = ButtonDefaults.shape,
    colors: ButtonColors = ButtonDefaults.filledTonalButtonColors(),
    border: BorderStroke? = null,
    contentPadding: PaddingValues = ButtonDefaults.ContentPadding,
    content: @Composable RowScope.() -> Unit,
) = RuntimeButton(onClick, modifier, enabled, tonal = true, contentPadding = contentPadding, shape = shape, colors = colors, border = border, content = content)

@Composable
fun RuntimeTextButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    contentPadding: PaddingValues = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
    colors: ButtonColors = ButtonDefaults.textButtonColors(),
    shape: Shape = ButtonDefaults.textShape,
    content: @Composable RowScope.() -> Unit,
) {
    TextButton(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        shape = shape,
        colors = colors,
        contentPadding = contentPadding,
        content = content,
    )
}

@Composable
fun RuntimeIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    contentDescription: String? = null,
    content: @Composable () -> Unit,
) {
    val accessibilityLabel = contentDescription
    // 无障碍标签挂到按钮节点；有文案时再套 miuix TooltipBox（长按 / 悬停）。
    RuntimeTooltip(text = accessibilityLabel, enabled = enabled) {
        IconButton(
            onClick = onClick,
            modifier = modifier.semantics { accessibilityLabel?.let { this.contentDescription = it } },
            enabled = enabled,
            content = content,
        )
    }
}

/** Filled icon action: default miuix IconButton 40dp, disabled alpha handled by the wrapper. */
@Composable
fun RuntimeFilledIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    containerColor: androidx.compose.ui.graphics.Color = RuntimeTheme.colorScheme.primary,
    contentColor: androidx.compose.ui.graphics.Color = RuntimeTheme.colorScheme.onPrimary,
    contentDescription: String? = null,
    content: @Composable () -> Unit,
) {
    FilledIconButton(onClick = onClick, enabled = enabled,
        modifier = modifier.semantics { contentDescription?.let { this.contentDescription = it } },
        colors = IconButtonDefaults.filledIconButtonColors(containerColor = containerColor, contentColor = contentColor),
        content = content)
}

