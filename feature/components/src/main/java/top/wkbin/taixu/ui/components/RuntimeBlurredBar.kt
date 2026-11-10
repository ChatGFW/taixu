package top.wkbin.taixu.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import top.yukonga.miuix.kmp.blur.BlendColorEntry
import top.yukonga.miuix.kmp.blur.BlurDefaults
import top.yukonga.miuix.kmp.blur.LayerBackdrop
import top.yukonga.miuix.kmp.blur.isRuntimeShaderSupported
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import top.yukonga.miuix.kmp.blur.textureBlur
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** Backdrop owned by the nearest [top.wkbin.taixu.ui.miuix.Scaffold] / blur host; null below API 33. */
val LocalRuntimeBackdrop = staticCompositionLocalOf<LayerBackdrop?> { null }

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

/** Bar fill: transparent over a live backdrop, opaque page colour otherwise. */
@Composable
fun runtimeBarColor(backdrop: LayerBackdrop?): Color =
    if (backdrop != null) Color.Transparent else MiuixTheme.colorScheme.surface

/** HyperOS frosted bar: 25dp texture blur tinted with 80% page colour. */
@Composable
fun RuntimeBlurredBar(
    backdrop: LayerBackdrop?,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val tint = MiuixTheme.colorScheme.surface.copy(alpha = 0.8f)
    Box(
        modifier = if (backdrop != null) {
            modifier.textureBlur(
                backdrop = backdrop,
                shape = RectangleShape,
                blurRadius = 25f,
                colors = BlurDefaults.blurColors(blendColors = listOf(BlendColorEntry(color = tint))),
            )
        } else modifier,
    ) { content() }
}
