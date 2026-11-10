package top.wkbin.taixu.ui.miuix

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
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
import top.wkbin.taixu.ui.components.RuntimeBlurredBar
import top.wkbin.taixu.ui.components.rememberRuntimeBackdrop
import top.wkbin.taixu.ui.components.runtimeBackdropSource
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
 * 与默认参数共用同一引用，便于识别「未提供顶栏」从而跳过零尺寸 textureBlur。
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
    content: @Composable (PaddingValues) -> Unit,
) {
    // 无顶栏时不建 backdrop：避免空 topBar 上 textureBlur 在部分 GPU 上 RenderThread SIGSEGV，
    // 也避免聊天等把胶囊画在 layerBackdrop 内容树内再采样自身。
    val useFrostedTopBar = topBar !== EmptyScaffoldTopBar
    val backdrop = if (useFrostedTopBar) rememberRuntimeBackdrop() else null
    CompositionLocalProvider(
        LocalContentColor provides contentColor,
        LocalRuntimeBackdrop provides backdrop,
    ) {
        top.yukonga.miuix.kmp.basic.Scaffold(
            modifier = modifier,
            topBar = {
                if (useFrostedTopBar) RuntimeBlurredBar(backdrop) { topBar() } else topBar()
            },
            bottomBar = bottomBar,
            floatingActionButton = floatingActionButton,
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
    // 库默认 FAB 为 60dp；TaiXu 统一压到 IconButton 的 40dp，避免 Material 浮层感。
    CompositionLocalProvider(LocalContentColor provides contentColor) {
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
