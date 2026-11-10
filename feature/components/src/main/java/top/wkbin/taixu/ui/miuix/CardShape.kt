package top.wkbin.taixu.ui.miuix

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import top.yukonga.miuix.kmp.squircle.squircleBorder

private val ProbeSize = Size(1000f, 1000f)

/** Uniform radii use the library's squircle path. Grouped rows retain their individual corners. */
@Composable
internal fun Shape.uniformRadius(): Dp? {
    val corners = this as? CornerBasedShape ?: return null
    val density = LocalDensity.current
    // Percent corners (CircleShape) depend on the laid-out size and need the shape clip instead.
    if (corners.topStart.toPx(Size.Zero, density) != corners.topStart.toPx(ProbeSize, density)) return null
    val topStart = corners.topStart.toPx(Size.Zero, density)
    val topEnd = corners.topEnd.toPx(Size.Zero, density)
    val bottomStart = corners.bottomStart.toPx(Size.Zero, density)
    val bottomEnd = corners.bottomEnd.toPx(Size.Zero, density)
    return if (topStart == topEnd && topStart == bottomStart && topStart == bottomEnd) {
        with(density) { topStart.toDp() }
    } else null
}

/** Card fill for custom-drawn groups: the card colour on pages, the inset fill on dialogs and sheets. */
@Composable
fun runtimeCardColor(): androidx.compose.ui.graphics.Color =
    if (LocalRaisedSurface.current) RuntimeTheme.colorScheme.surfaceContainerHigh else RuntimeTheme.colorScheme.surfaceContainer

/** On a dialog or sheet body a card-coloured card would vanish; it takes the inset fill instead. */
@Composable
internal fun top.yukonga.miuix.kmp.basic.CardColors.onRaised(): top.yukonga.miuix.kmp.basic.CardColors =
    if (LocalRaisedSurface.current && color == RuntimeTheme.colorScheme.surfaceContainer) {
        copy(color = RuntimeTheme.colorScheme.surfaceContainerHigh)
    } else this

/** miuix cards never exceed the 16dp squircle; larger M3-scale radii read as Material. */
@Composable
internal fun Shape.atMostCardRadius(): Shape {
    val radius = uniformRadius() ?: return this
    return if (radius > top.yukonga.miuix.kmp.basic.CardDefaults.CornerRadius) RuntimeTheme.shapes.medium else this
}

/**
 * miuix separates layers by fill (grey page, white card), never by outline. Grey hairlines and
 * translucent tinted rims are decoration in the M3 outlined-card idiom and are dropped; only an
 * opaque tinted border carries state (selection, error, running) and stays.
 */
internal fun BorderStroke?.stateOnly(): BorderStroke? {
    val color = (this?.brush as? SolidColor)?.value ?: return this
    val chroma = maxOf(color.red, color.green, color.blue) - minOf(color.red, color.green, color.blue)
    return if (chroma < 0.12f || color.alpha < 0.6f) null else this
}

@Composable
internal fun Modifier.cardOutline(shape: Shape, radius: Dp?, border: BorderStroke?): Modifier {
    val clipped = if (radius == null) clip(shape) else this
    val border = border.stateOnly()
    if (border == null) return clipped
    val brush = border.brush
    return if (radius != null && brush is SolidColor) clipped.squircleBorder(border.width, brush.value, radius)
    else clipped.border(border, shape)
}
