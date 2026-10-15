package top.wkbin.taixu.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat

// ======================= 「玄同 · Xuantong」= 默认 M3 Expressive 配色 =======================

val XuantongLightColors = lightColorScheme(
    primary = M3ExpLightPrimary,
    onPrimary = M3ExpLightOnPrimary,
    primaryContainer = M3ExpLightPrimaryContainer,
    onPrimaryContainer = M3ExpLightOnPrimaryContainer,
    secondary = M3ExpLightSecondary,
    onSecondary = M3ExpLightOnSecondary,
    secondaryContainer = M3ExpLightSecondaryContainer,
    onSecondaryContainer = M3ExpLightOnSecondaryContainer,
    tertiary = M3ExpLightTertiary,
    onTertiary = M3ExpLightOnTertiary,
    tertiaryContainer = M3ExpLightTertiaryContainer,
    onTertiaryContainer = M3ExpLightOnTertiaryContainer,
    error = M3ExpLightError,
    onError = Color.White,
    errorContainer = M3ExpLightErrorContainer,
    onErrorContainer = Color(0xFF410002),
    background = M3ExpLightBackground,
    onBackground = M3ExpLightOnBackground,
    surface = M3ExpLightSurface,
    onSurface = M3ExpLightOnSurface,
    surfaceVariant = M3ExpLightSurfaceVariant,
    onSurfaceVariant = M3ExpLightOnSurfaceVariant,
    surfaceContainerLowest = M3ExpLightSurfaceContainerLowest,
    surfaceContainerLow = M3ExpLightSurfaceContainerLow,
    surfaceContainer = M3ExpLightSurfaceContainer,
    surfaceContainerHigh = M3ExpLightSurfaceContainerHigh,
    surfaceContainerHighest = M3ExpLightSurfaceContainerHighest,
    outline = M3ExpLightOutline,
    outlineVariant = M3ExpLightOutlineVariant,
)

val XuantongDarkColors = darkColorScheme(
    primary = M3ExpDarkPrimary,
    onPrimary = M3ExpDarkOnPrimary,
    primaryContainer = M3ExpDarkPrimaryContainer,
    onPrimaryContainer = M3ExpDarkOnPrimaryContainer,
    secondary = M3ExpDarkSecondary,
    onSecondary = M3ExpDarkOnSecondary,
    secondaryContainer = M3ExpDarkSecondaryContainer,
    onSecondaryContainer = M3ExpDarkOnSecondaryContainer,
    tertiary = M3ExpDarkTertiary,
    onTertiary = M3ExpDarkOnTertiary,
    tertiaryContainer = M3ExpDarkTertiaryContainer,
    onTertiaryContainer = M3ExpDarkOnTertiaryContainer,
    error = M3ExpDarkError,
    onError = Color(0xFF690005),
    errorContainer = M3ExpDarkErrorContainer,
    onErrorContainer = Color(0xFFFFDAD6),
    background = M3ExpDarkBackground,
    onBackground = M3ExpDarkOnBackground,
    surface = M3ExpDarkSurface,
    onSurface = M3ExpDarkOnSurface,
    surfaceVariant = M3ExpDarkSurfaceVariant,
    onSurfaceVariant = M3ExpDarkOnSurfaceVariant,
    surfaceContainerLowest = M3ExpDarkSurfaceContainerLowest,
    surfaceContainerLow = M3ExpDarkSurfaceContainerLow,
    surfaceContainer = M3ExpDarkSurfaceContainer,
    surfaceContainerHigh = M3ExpDarkSurfaceContainerHigh,
    surfaceContainerHighest = M3ExpDarkSurfaceContainerHighest,
    outline = M3ExpDarkOutline,
    outlineVariant = M3ExpDarkOutlineVariant,
)

/**
 * Material 3 形状体系 (Shape Scale)
 *
 * M3 Expressive 在标准 M3 五级基础上引入更饱满的圆角：卡片 16dp、按钮全圆角（胶囊）、
 * 强调容器 28dp。原实现把 extraSmall 压到 4dp，导致 507 处调用点各自硬编码 4/6/8/10/12dp，
 * 形状 token 形同虚设。这里重新对齐到 M3 基线（4/8/12/16/28），
 * 并显式提供 extraExtraLarge 供摘要类大容器使用。
 */
val TaiXuShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

/**
 * 太墟主题：Material You (M3)，支持 Monet 动态壁纸取色与曜石/素白静态基调。
 */
@Composable
fun TaiXuTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window
            if (window != null) {
                window.isNavigationBarContrastEnforced = false
                val insetsController = WindowCompat.getInsetsController(window, view)
                insetsController.isAppearanceLightStatusBars = !darkTheme
                insetsController.isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    val context = LocalContext.current
    val staticFallback = if (darkTheme) XuantongDarkColors else XuantongLightColors
    val colorScheme = if (dynamicColor) {
        val systemMonet = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        } else {
            staticFallback
        }
        rememberWallpaperDynamicColorScheme(
            darkTheme = darkTheme,
            fallback = systemMonet,
        )
    } else {
        staticFallback
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = AppTypography,
        shapes = TaiXuShapes,
    ) {
        val semantics = if (darkTheme) DarkSemantics else LightSemantics
        CompositionLocalProvider(LocalSemanticColors provides semantics, content = content)
    }
}
