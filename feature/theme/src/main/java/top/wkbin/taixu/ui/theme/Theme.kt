package top.wkbin.taixu.ui.theme

import top.wkbin.taixu.core.model.ThemePalette

import android.app.Activity
import android.content.ContextWrapper
import androidx.compose.foundation.isSystemInDarkTheme
import top.wkbin.taixu.ui.miuix.RuntimeDesignTheme
import top.wkbin.taixu.ui.miuix.Shapes
import top.wkbin.taixu.ui.miuix.darkColorScheme
import top.wkbin.taixu.ui.miuix.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

val XuantongLightColors = lightColorScheme()
val XuantongDarkColors = darkColorScheme()
val TaiXuShapes = Shapes()

/**
 * 太墟主题：Miuix 设计系统，支持保存的色板与壁纸动态取色（HCT 兜底）。
 * [dynamicColor]/[palette] 仍生效，不再固定为玄同默认色。
 */
@Composable
fun TaiXuTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    palette: ThemePalette = ThemePalette(),
    content: @Composable () -> Unit,
) {
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = generateSequence(view.context) { context ->
                (context as? ContextWrapper)?.baseContext?.takeIf { it !== context }
            }.filterIsInstance<Activity>().firstOrNull()?.window
            if (window != null) {
                window.isNavigationBarContrastEnforced = false
                val insetsController = WindowCompat.getInsetsController(window, view)
                insetsController.isAppearanceLightStatusBars = !darkTheme
                insetsController.isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    val staticFallback = remember(darkTheme, palette) {
        themePaletteColorScheme(palette, darkTheme)
    }
    val colorScheme = if (dynamicColor) {
        rememberWallpaperDynamicColorScheme(
            darkTheme = darkTheme,
            systemScheme = null,
            fallback = staticFallback,
            style = palette.style,
        )
    } else {
        staticFallback
    }

    RuntimeDesignTheme(
        colorScheme = colorScheme,
        dark = darkTheme,
        typography = AppTypography,
        shapes = TaiXuShapes,
    ) {
        val semantics = remember(colorScheme) { buildSemanticColors(colorScheme) }
        CompositionLocalProvider(LocalSemanticColors provides semantics, content = content)
    }
}
