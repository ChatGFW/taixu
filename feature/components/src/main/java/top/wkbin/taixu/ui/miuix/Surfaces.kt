package top.wkbin.taixu.ui.miuix

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.draw.clip
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import top.wkbin.taixu.ui.components.LocalRuntimeBackdrop
import top.wkbin.taixu.ui.components.LocalRuntimeBackdropView
import top.wkbin.taixu.ui.components.LocalRuntimeChromeBackdrop
import androidx.compose.ui.platform.LocalView
import top.wkbin.taixu.ui.components.rememberRuntimeBackdrop
import top.wkbin.taixu.ui.components.runtimeBackdropSource
import top.yukonga.miuix.kmp.glass.GlassFloatingActionButton
import top.yukonga.miuix.kmp.material.GlassShape
import top.yukonga.miuix.kmp.material.GlassShadows
import top.yukonga.miuix.kmp.theme.LocalContentColor
import top.yukonga.miuix.kmp.basic.Surface as MiuixSurface

@Composable
fun Surface(
    modifier: Modifier = Modifier,
    shape: Shape = RectangleShape,
    color: Color = RuntimeTheme.colorScheme.surface,
    contentColor: Color = RuntimeTheme.colorScheme.onSurface,
    tonalElevation: Dp = 0.dp,
    shadowElevation: Dp = 0.dp,
    border: BorderStroke? = null,
    content: @Composable () -> Unit,
) = MiuixSurface(modifier, shape, color, contentColor, border.stateOnly(), shadowElevation, content)

@Composable
fun Surface(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = RectangleShape,
    color: Color = RuntimeTheme.colorScheme.surface,
    contentColor: Color = RuntimeTheme.colorScheme.onSurface,
    tonalElevation: Dp = 0.dp,
    shadowElevation: Dp = 0.dp,
    border: BorderStroke? = null,
    content: @Composable () -> Unit,
) = MiuixSurface(onClick, modifier.clip(shape), enabled, shape, color, contentColor, border.stateOnly(), shadowElevation, content = content)

typealias CardColors = top.yukonga.miuix.kmp.basic.CardColors

object CardDefaults {
    @Composable
    fun cardColors(containerColor: Color = RuntimeTheme.colorScheme.surfaceContainer) =
        top.yukonga.miuix.kmp.basic.CardDefaults.defaultColors(containerColor)
}

@Composable
fun Card(
    modifier: Modifier = Modifier,
    shape: Shape = RuntimeTheme.shapes.medium,
    colors: CardColors = CardDefaults.cardColors(),
    border: BorderStroke? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val radius = shape.uniformRadius()
    top.yukonga.miuix.kmp.basic.Card(modifier = modifier.cardOutline(shape, radius, border), cornerRadius = radius ?: 0.dp, insideMargin = PaddingValues(0.dp), colors = colors.onRaised()) {
        content()
    }
}

@Composable
fun Card(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    shape: Shape = RuntimeTheme.shapes.medium,
    colors: CardColors = CardDefaults.cardColors(),
    border: BorderStroke? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val radius = shape.uniformRadius()
    top.yukonga.miuix.kmp.basic.Card(modifier = modifier.cardOutline(shape, radius, border), cornerRadius = radius ?: 0.dp, insideMargin = PaddingValues(0.dp), colors = colors.onRaised(),
        onClick = onClick, showIndication = true, content = content)
}

@Composable
fun HorizontalDivider(
    modifier: Modifier = Modifier,
    thickness: Dp = top.yukonga.miuix.kmp.basic.DividerDefaults.Thickness,
    color: Color = RuntimeTheme.colorScheme.outlineVariant,
) = top.yukonga.miuix.kmp.basic.HorizontalDivider(modifier = modifier, thickness = thickness, color = color)

@Composable
fun VerticalDivider(
    modifier: Modifier = Modifier,
    thickness: Dp = top.yukonga.miuix.kmp.basic.DividerDefaults.Thickness,
    color: Color = RuntimeTheme.colorScheme.outlineVariant,
) = top.yukonga.miuix.kmp.basic.VerticalDivider(modifier = modifier, thickness = thickness, color = color)

/**
 * 与默认参数共用同一引用，便于未提供顶栏时跳过页面 backdrop 录制。
 * STUDIO 等全屏模式必须传此引用（或省略 topBar），不能传「内部为空」的新 lambda。
 */
val EmptyScaffoldTopBar: @Composable () -> Unit = {}

@Composable
fun Scaffold(
    modifier: Modifier = Modifier,
    topBar: @Composable () -> Unit = EmptyScaffoldTopBar,
    bottomBar: @Composable () -> Unit = {},
    floatingActionButton: @Composable () -> Unit = {},
    snackbarHost: @Composable () -> Unit = {},
    containerColor: Color = RuntimeTheme.colorScheme.background,
    contentColor: Color = RuntimeTheme.colorScheme.onBackground,
    contentWindowInsets: WindowInsets = ScaffoldDefaults.contentWindowInsets,
    captureBackdrop: Boolean = topBar !== EmptyScaffoldTopBar,
    content: @Composable (PaddingValues) -> Unit,
) {
    val backdrop = if (captureBackdrop) rememberRuntimeBackdrop() else null
    CompositionLocalProvider(
        LocalContentColor provides contentColor,
        LocalRuntimeBackdrop provides backdrop,
        LocalRuntimeBackdropView provides LocalView.current,
        LocalRuntimeChromeBackdrop provides null,
    ) {
        top.yukonga.miuix.kmp.basic.Scaffold(
            modifier = modifier,
            topBar = {
                CompositionLocalProvider(LocalRuntimeChromeBackdrop provides backdrop, content = topBar)
            },
            bottomBar = {
                CompositionLocalProvider(LocalRuntimeChromeBackdrop provides backdrop, content = bottomBar)
            },
            floatingActionButton = {
                CompositionLocalProvider(LocalRuntimeChromeBackdrop provides backdrop, content = floatingActionButton)
            },
            snackbarHost = snackbarHost,
            containerColor = containerColor,
            contentWindowInsets = contentWindowInsets,
        ) { padding ->
            Box(Modifier.fillMaxSize().runtimeBackdropSource(backdrop)) {
                content(padding)
            }
        }
    }
}

object ScaffoldDefaults {
    val contentWindowInsets @Composable get() = WindowInsets.systemBars.union(WindowInsets.displayCutout)
}

data class FloatingActionButtonElevation(val defaultElevation: Dp, val pressedElevation: Dp)
object FloatingActionButtonDefaults {
    fun elevation(defaultElevation: Dp = 4.dp, pressedElevation: Dp = 4.dp) =
        FloatingActionButtonElevation(defaultElevation, pressedElevation)
}

@Composable
fun FloatingActionButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    shape: Shape = androidx.compose.foundation.shape.CircleShape,
    containerColor: Color = RuntimeTheme.colorScheme.primary,
    contentColor: Color = RuntimeTheme.colorScheme.onPrimary,
    elevation: FloatingActionButtonElevation = FloatingActionButtonDefaults.elevation(),
    content: @Composable () -> Unit,
) {
    // Keep 40dp controls in floating surfaces and top bars.
    CompositionLocalProvider(LocalContentColor provides contentColor) {
        if (shape is CornerBasedShape) {
            GlassFloatingActionButton(
                onClick = onClick,
                modifier = modifier,
                backdrop = LocalRuntimeChromeBackdrop.current,
                size = 40.dp,
                shape = GlassShape(shape.topStart, shape.topEnd, shape.bottomEnd, shape.bottomStart),
                fill = containerColor,
                shadow = GlassShadows.Float.takeIf { elevation.defaultElevation > 0.dp },
                content = content,
            )
            return@CompositionLocalProvider
        }
        top.yukonga.miuix.kmp.basic.FloatingActionButton(
            onClick = onClick,
            modifier = modifier.clip(shape),
            shape = shape,
            containerColor = containerColor,
            shadowElevation = elevation.defaultElevation,
            minWidth = 40.dp,
            minHeight = 40.dp,
            content = content,
        )
    }
}
