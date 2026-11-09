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

/** 使用 Miuix 原生明暗配色。旧配色参数仅兼容现有调用。 */
@Composable
fun TaiXuTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
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

    val colorScheme = if (darkTheme) XuantongDarkColors else XuantongLightColors

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
