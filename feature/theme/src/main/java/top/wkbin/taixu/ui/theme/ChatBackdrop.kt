package top.wkbin.taixu.ui.theme

import top.wkbin.taixu.ui.miuix.RuntimeTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/** Scoped to the conversation; terminal, browser and settings keep their own surfaces. */
val LocalChatBackgroundActive = staticCompositionLocalOf { false }

@Composable
fun chatBackdropSurface(base: Color = RuntimeTheme.colorScheme.surfaceContainerLow): Color =
    if (LocalChatBackgroundActive.current) base.copy(alpha = 0.92f) else base

@Composable
fun chatBackdropChrome(): Color =
    if (LocalChatBackgroundActive.current) RuntimeTheme.colorScheme.surface.copy(alpha = 0.86f)
    else RuntimeTheme.colorScheme.surface
