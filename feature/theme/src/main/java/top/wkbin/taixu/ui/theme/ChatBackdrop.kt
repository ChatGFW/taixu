package top.wkbin.taixu.ui.theme

import top.wkbin.taixu.ui.miuix.RuntimeTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp

/** Scoped to the conversation; terminal, browser and settings keep their own surfaces. */
val LocalChatBackgroundActive = staticCompositionLocalOf { false }

@Composable
fun chatBackdropSurface(base: Color = RuntimeTheme.colorScheme.surfaceContainerLow): Color =
    if (LocalChatBackgroundActive.current) base.copy(alpha = 0.92f) else base

@Composable
fun chatBackdropChrome(): Color =
    if (LocalChatBackgroundActive.current) RuntimeTheme.colorScheme.surface.copy(alpha = 0.86f)
    else RuntimeTheme.colorScheme.surface

/** 大半径、低透明度的柔和投影，比 elevation 阴影更散、更轻。 */
fun Modifier.chatSoftDropShadow(shape: Shape): Modifier = dropShadow(
    shape = shape,
    shadow = Shadow(
        radius = 28.dp,
        spread = 4.dp,
        color = Color.Black,
        alpha = 0.10f,
        offset = DpOffset(0.dp, 6.dp),
    ),
)
