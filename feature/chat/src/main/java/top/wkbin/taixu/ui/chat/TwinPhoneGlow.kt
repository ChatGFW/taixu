package top.wkbin.taixu.ui.chat

import android.graphics.BlurMaskFilter
import android.graphics.Matrix
import android.graphics.Path
import android.graphics.SweepGradient as AndroidSweepGradient
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sin
import top.wkbin.taixu.ui.miuix.RuntimeTheme
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.squircle.squircleSurface

internal enum class TwinGlowKind { Running, Need, Done, Failed }

/** 小窗状态 chip。 */
internal data class TwinMiniBadge(
    val label: String,
    val color: Color,
    val breath: Boolean = false,
)

@Composable
internal fun TwinMiniStatusChip(badge: TwinMiniBadge, modifier: Modifier = Modifier) {
    val breathAlpha by rememberInfiniteTransition(label = "guestBreath").animateFloat(
        1f,
        if (badge.breath) 0.35f else 1f,
        infiniteRepeatable(tween(700, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "guestBreathAlpha",
    )
    Text(
        text = badge.label,
        modifier = modifier
            .graphicsLayer { alpha = breathAlpha }
            .squircleSurface(color = badge.color, cornerRadius = 9.dp)
            .padding(horizontal = 9.dp, vertical = 4.dp)
            .widthIn(max = 100.dp),
        color = Color.White,
        style = RuntimeTheme.typography.labelSmall,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

internal fun glowPalette(kind: TwinGlowKind): List<Color> = when (kind) {
    TwinGlowKind.Running -> listOf(
        Color(0xFF4F7CFF), Color(0xFF8E6BFF), Color(0xFFE56BD6),
        Color(0xFFFF8A6B), Color(0xFFFFC56B), Color(0xFF45C4F0), Color(0xFF4F7CFF),
    )
    TwinGlowKind.Need -> listOf(
        Color(0xFFFFB23D), Color(0xFFFFD58A), Color(0xFFFF8C3A),
        Color(0xFFFFC25C), Color(0xFFFF9F45), Color(0xFFFFE2A0), Color(0xFFFFB23D),
    )
    TwinGlowKind.Done -> listOf(
        Color(0xFF3DD68C), Color(0xFF9BEFC0), Color(0xFF1FBF73),
        Color(0xFF6FE3A6), Color(0xFF2CCB80), Color(0xFFB5F5D2), Color(0xFF3DD68C),
    )
    TwinGlowKind.Failed -> listOf(
        Color(0xFFFF5C5C), Color(0xFFFF9C8F), Color(0xFFFF3D5A),
        Color(0xFFFF7A6B), Color(0xFFFF4F4F), Color(0xFFFFB3A8), Color(0xFFFF5C5C),
    )
}

/** 外侧扫光 bloom；[softBlur] 时用 BlurMaskFilter 兜底低版本。 */
internal fun DrawScope.drawGuestLightBloom(
    colors: List<Color>,
    rimAngle: Float,
    wavePhase: Float,
    cornerPx: Float,
    alpha: Float,
    softBlur: Boolean,
) {
    if (alpha <= 0.01f || colors.isEmpty()) return
    val w = size.width
    val h = size.height
    val cx = w / 2f
    val cy = h / 2f
    val radius = max(w, h) * 0.72f
    val argb = IntArray(colors.size) { colors[it].toArgb() }
    drawIntoCanvas { canvas ->
        val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
        paint.style = android.graphics.Paint.Style.FILL
        paint.alpha = (200f * alpha).roundToInt().coerceIn(0, 255)
        if (softBlur) {
            paint.maskFilter = BlurMaskFilter(radius * 0.22f, BlurMaskFilter.Blur.NORMAL)
        }
        paint.shader = AndroidSweepGradient(cx, cy, argb, null).also { shader ->
            val matrix = Matrix()
            matrix.setRotate(rimAngle, cx, cy)
            shader.setLocalMatrix(matrix)
        }
        canvas.nativeCanvas.drawCircle(cx, cy, radius, paint)

        paint.shader = null
        paint.color = android.graphics.Color.BLACK
        for ((ampScale, a) in listOf(1.0f to 0.22f, 0.6f to 0.12f, 0.3f to 0.06f)) {
            paint.alpha = (a * 255f * alpha).roundToInt().coerceIn(0, 255)
            paint.maskFilter = if (softBlur) {
                BlurMaskFilter(6.dp.toPx() * ampScale, BlurMaskFilter.Blur.NORMAL)
            } else {
                null
            }
            canvas.nativeCanvas.drawPath(
                softWaveRectPath(w, h, ampScale * 4.dp.toPx(), wavePhase),
                paint,
            )
        }
        paint.shader = AndroidSweepGradient(cx, cy, argb, null).also { shader ->
            val matrix = Matrix()
            matrix.setRotate(rimAngle + 180f, cx, cy)
            shader.setLocalMatrix(matrix)
        }
        paint.alpha = (90f * alpha).roundToInt().coerceIn(0, 255)
        if (softBlur) {
            paint.maskFilter = BlurMaskFilter(14.dp.toPx(), BlurMaskFilter.Blur.NORMAL)
        }
        val pad = 10.dp.toPx()
        canvas.nativeCanvas.drawRoundRect(
            -pad, -pad, w + pad, h + pad,
            cornerPx + pad * 0.5f, cornerPx + pad * 0.5f, paint,
        )
    }
}

/** 画面内侧极淡扫光洗色。 */
internal fun DrawScope.drawGuestInnerWash(
    colors: List<Color>,
    rimAngle: Float,
    alpha: Float,
) {
    if (alpha <= 0.01f || colors.isEmpty()) return
    val cx = size.width / 2f
    val cy = size.height / 2f
    val argb = IntArray(colors.size) { colors[it].toArgb() }
    drawIntoCanvas { canvas ->
        val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            style = android.graphics.Paint.Style.FILL
            this.alpha = (70f * alpha).roundToInt().coerceIn(0, 255)
            maskFilter = BlurMaskFilter(18.dp.toPx(), BlurMaskFilter.Blur.NORMAL)
            shader = AndroidSweepGradient(cx, cy, argb, null).also { shader ->
                val matrix = Matrix()
                matrix.setRotate(rimAngle, cx, cy)
                shader.setLocalMatrix(matrix)
            }
        }
        canvas.nativeCanvas.drawCircle(cx, cy, max(size.width, size.height) * 0.55f, paint)
    }
}

private fun softWaveRectPath(width: Float, height: Float, amplitude: Float, phase: Float): Path {
    val path = Path()
    val peri = 2f * (width + height)
    val steps = 96
    fun insetAt(dist: Float): Float {
        val t = ((dist / peri) + phase) % 1f
        var v = 0.28f
        v += 0.09f * sin((3f * t + 0f) * Math.PI.toFloat() * 2f)
        v += 0.06f * sin((-2f * t + 1.3f) * Math.PI.toFloat() * 2f)
        v += 0.05f * sin((1f * t + 2.1f) * Math.PI.toFloat() * 2f)
        return max(0.02f, v) * amplitude
    }
    fun edgePoint(d: Float): Pair<Float, Float> {
        val inset = insetAt(d)
        return when {
            d < width -> d to inset
            d < width + height -> (width - inset) to (d - width)
            d < 2f * width + height -> (width - (d - width - height)) to (height - inset)
            else -> inset to (height - (d - 2f * width - height))
        }
    }
    val first = edgePoint(0f)
    path.moveTo(first.first, first.second)
    for (i in 1..steps) {
        val p = edgePoint(peri * i / steps)
        path.lineTo(p.first, p.second)
    }
    path.close()
    return path
}
