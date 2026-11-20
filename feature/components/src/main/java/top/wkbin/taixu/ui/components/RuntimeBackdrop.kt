package top.wkbin.taixu.ui.components

import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import top.yukonga.miuix.kmp.blur.LayerBackdrop
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import top.yukonga.miuix.kmp.material.isRuntimeShaderSupported
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** A source layer owned by the nearest Scaffold, used by its overlay popups. */
internal val LocalRuntimeBackdrop = staticCompositionLocalOf<LayerBackdrop?> { null }
internal val LocalRuntimeBackdropView = staticCompositionLocalOf<View?> { null }

@Composable
internal fun runtimeOverlayBackdrop(): LayerBackdrop? =
    LocalRuntimeBackdrop.current.takeIf { LocalRuntimeBackdropView.current === LocalView.current }

/** Scene recorded separately from the top bar or floating tools that sample it. */
val LocalRuntimeChromeBackdrop = staticCompositionLocalOf<LayerBackdrop?> { null }

/** Page backdrop for frosted bars; null where runtime shaders are unavailable (below Android 13). */
@Composable
fun rememberRuntimeBackdrop(): LayerBackdrop? {
    if (!isRuntimeShaderSupported()) return null
    val surface = MiuixTheme.colorScheme.surface
    return rememberLayerBackdrop {
        drawRect(surface)
        drawContent()
    }
}

/** Records the scrolling content so bars layered above it can blur it. */
fun Modifier.runtimeBackdropSource(backdrop: LayerBackdrop?): Modifier =
    if (backdrop != null) layerBackdrop(backdrop) else this
