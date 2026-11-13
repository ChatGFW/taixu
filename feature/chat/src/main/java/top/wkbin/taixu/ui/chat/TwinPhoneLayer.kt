package top.wkbin.taixu.ui.chat

import android.graphics.Matrix
import android.graphics.Path
import android.graphics.SweepGradient as AndroidSweepGradient
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlinx.coroutines.launch
import top.wkbin.taixu.ui.miuix.RuntimeTheme
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.squircle.squircleClip
import top.yukonga.miuix.kmp.squircle.squircleSurface

/** 
private val MiniPhoneWidth = 124.dp
private val TwinExpandSpec = tween<Float>(360, easing = FastOutSlowInEasing)
private val TwinSnapSpec = tween<Float>(220, easing = FastOutSlowInEasing)
private const val FallbackPhoneAspect = 0.516f
private val MiniBorderColor = Color(0xFFD7DAE0)
private val MiniEdgeInset = 12.dp
private val MiniEdgeMin = 8.dp
private val MiniBottomSlack = 10.dp

/** 
internal enum class TwinGlowKind { Running, Need, Done, Failed }

/** 小窗 pip 状态 chip（
internal data class TwinMiniBadge(
    val label: String,
    val color: Color,
    val breath: Boolean = false,
)

/**
 * 分身画面层：小窗 ↔ 大窗；四角吸附；任务中流光。
 * 小窗可叠小型状态 chip；「我来/停止」底栏只在大窗 [expandedChrome]。
 */
@Composable
internal fun TwinPhoneLayer(
    bottomInsets: WindowInsets,
    expanded: Boolean,
    immersive: Boolean = false,
    miniVisible: Boolean,
    sweepActive: Boolean,
    contentAspect: Float,
    glowKind: TwinGlowKind = TwinGlowKind.Running,
    badge: TwinMiniBadge? = null,
    onExpand: () -> Unit,
    onCollapse: (() -> Unit)? = null,
    expandedChrome: (@Composable () -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val aspect = contentAspect.takeIf { it > 0.05f } ?: FallbackPhoneAspect
    val progress by animateFloatAsState(
        if (expanded) 1f else 0f, TwinExpandSpec, label = "twinExpand",
    )
    val miniAlpha by animateFloatAsState(
        if (expanded || miniVisible) 1f else 0f, TwinExpandSpec, label = "twinMiniAlpha",
    )
    // 小窗满光；大窗任务中保留弱流光（
    val glowAlpha by animateFloatAsState(
        when {
            !sweepActive -> 0f
            expanded -> 0.42f
            else -> 1f
        },
        tween(400),
        label = "guestGlow",
    )
    if (!expanded && !miniVisible && miniAlpha <= 0.01f && progress <= 0.01f) {
        Box(Modifier.size(1.dp).graphicsLayer { alpha = 0f; translationX = -10_000f }) { content() }
        return
    }
    val scope = rememberCoroutineScope()
    val glowColors = remember(glowKind) { glowPalette(glowKind) }
    val pageBg = RuntimeTheme.colorScheme.background
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val density = LocalDensity.current
        // 大窗从顶栏药丸下方开始，避免与「分身|手机」重叠（须含 statusBars）。
        val statusTop = WindowInsets.statusBars.getTop(density).toFloat()
        val topPad = if (immersive) {
            0f
        } else {
            statusTop + with(density) { (CompactTopBarClearance + 12.dp).toPx() }
        }
        val bottomPad = with(density) { if (immersive) 0.dp.toPx() else 12.dp.toPx() }
        val sidePad = with(density) { if (immersive) 0.dp.toPx() else 12.dp.toPx() }
        val chromeGap = with(density) { 10.dp.toPx() }
        val chromeH = with(density) {
            // 合并后单排底栏
            if (immersive || expandedChrome == null) 0.dp.toPx() else 56.dp.toPx()
        }
        val edgeInset = with(density) { MiniEdgeInset.toPx() }
        val edgeMin = with(density) { MiniEdgeMin.toPx() }
        val bottomSlack = with(density) { MiniBottomSlack.toPx() }
        val fullW = constraints.maxWidth.toFloat().coerceAtLeast(1f)
        val fullH = constraints.maxHeight.toFloat().coerceAtLeast(1f)
        val availW = (fullW - sidePad * 2f).coerceAtLeast(1f)
        val chromeReserve = if (immersive) 0f else (chromeH + chromeGap) * progress
        val availH = (fullH - topPad - bottomPad - chromeReserve).coerceAtLeast(1f)
        val expandedBox = availW to availH
        val expandedLeft = sidePad
        val expandedTop = topPad
        val miniW = with(density) { MiniPhoneWidth.toPx() }
        val miniH = miniW / aspect
        val maxDragX = (fullW - miniW - edgeMin).coerceAtLeast(0f)
        val maxDragY = (fullH - miniH - bottomSlack).coerceAtLeast(0f)
        var dragX by remember { mutableFloatStateOf(maxDragX) }
        var dragY by remember { mutableFloatStateOf(maxDragY) }
        val miniLeft = dragX.coerceIn(0f, maxDragX)
        val miniTop = dragY.coerceIn(0f, maxDragY)
        val left = lerp(miniLeft, expandedLeft, progress)
        val top = lerp(miniTop, expandedTop, progress)
        val widthPx = lerp(miniW, expandedBox.first, progress)
        val heightPx = lerp(miniH, expandedBox.second, progress)
        val corner = lerp(18f, 0f, progress)
        val glow = rememberInfiniteTransition(label = "guestGlowFlow")
        val rimAngle by glow.animateFloat(
            0f, 360f, infiniteRepeatable(tween(4000, easing = LinearEasing)), label = "rim",
        )
        val bandAngle by glow.animateFloat(
            360f, 0f, infiniteRepeatable(tween(6000, easing = LinearEasing)), label = "band",
        )
        val wavePhase by glow.animateFloat(
            0f, 1f, infiniteRepeatable(tween(6000, easing = LinearEasing)), label = "waves",
        )
        val showGlow = glowAlpha > 0.02f
        if (progress > 0.02f) {
            val scrim = if (immersive) Color.Black.copy(alpha = progress) else pageBg.copy(alpha = progress)
            Box(
                Modifier.fillMaxSize().background(scrim).then(
                    if (progress > 0.85f && onCollapse != null && immersive) {
                        Modifier.clickable(
                            remember { MutableInteractionSource() }, null, onClick = onCollapse,
                        )
                    } else Modifier,
                ),
            )
        }
        val cornerShape = RoundedCornerShape(corner.dp)
        Box(
            Modifier
                .offset { IntOffset(left.roundToInt(), top.roundToInt()) }
                .width(with(density) { widthPx.toDp() })
                .height(with(density) { heightPx.toDp() })
                .graphicsLayer {
                    alpha = if (expanded) 1f else miniAlpha
                    shadowElevation = 8f * progress
                    shape = cornerShape
                    clip = false
                }
                .squircleClip(cornerRadius = corner.dp)
                .then(
                    if (progress < 0.5f) {
                        Modifier.border(1.dp, MiniBorderColor.copy(alpha = 0.9f * (1f - progress)), cornerShape)
                    } else Modifier,
                )
                .then(
                    if (showGlow) {
                        Modifier.drawWithContent {
                            drawContent()
                            drawGuestGlow(
                                glowColors, rimAngle, bandAngle, wavePhase,
                                corner.dp.toPx(),
                                glowAlpha * lerp(1f, 0.45f, progress.coerceIn(0f, 1f)),
                            )
                        }
                    } else Modifier,
                )
                .background(Color.Black),
        ) {
            content()
            if (badge != null && progress < 0.35f && miniAlpha > 0.2f) {
                TwinMiniStatusChip(
                    badge = badge,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 8.dp, start = 6.dp, end = 6.dp),
                )
            }
            // 小窗拦截拖动/点击展开；大窗「停止」只在 expandedChrome。
            if (progress < 0.92f && miniAlpha > 0.05f) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .pointerInput(maxDragX, maxDragY, progress) {
                            if (progress > 0.08f) return@pointerInput
                            detectDragGestures(
                                onDragEnd = {
                                    val corners = listOf(
                                        edgeInset to edgeInset, maxDragX to edgeInset,
                                        edgeInset to maxDragY, maxDragX to maxDragY,
                                    )
                                    val nearest = corners.minBy { (cx, cy) -> hypot(cx - dragX, cy - dragY) }
                                    scope.launch {
                                        val ax = Animatable(dragX)
                                        val ay = Animatable(dragY)
                                        launch { ax.animateTo(nearest.first, TwinSnapSpec) { dragX = value } }
                                        launch { ay.animateTo(nearest.second, TwinSnapSpec) { dragY = value } }
                                    }
                                },
                            ) { change, amount ->
                                change.consume()
                                dragX = (dragX + amount.x).coerceIn(0f, maxDragX)
                                dragY = (dragY + amount.y).coerceIn(0f, maxDragY)
                            }
                        }
                        .clickable(remember { MutableInteractionSource() }, null, onClick = onExpand),
                )
            }
        }
        if (expandedChrome != null && progress > 0.55f && !immersive) {
            Box(
                Modifier
                    .offset { IntOffset(sidePad.roundToInt(), (top + heightPx + chromeGap).roundToInt()) }
                    .width(with(density) { availW.toDp() })
                    .graphicsLayer { alpha = ((progress - 0.55f) / 0.45f).coerceIn(0f, 1f) },
            ) { expandedChrome() }
        }
    }
}

@Composable
private fun TwinMiniStatusChip(badge: TwinMiniBadge, modifier: Modifier = Modifier) {
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

private fun glowPalette(kind: TwinGlowKind): List<Color> = when (kind) {
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

private fun DrawScope.drawGuestGlow(
    colors: List<Color>,
    rimAngle: Float,
    bandAngle: Float,
    wavePhase: Float,
    cornerPx: Float,
    alpha: Float,
) {
    val cx = size.width / 2f
    val cy = size.height / 2f
    val argb = IntArray(colors.size) { colors[it].toArgb() }
    drawIntoCanvas { canvas ->
        val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            style = android.graphics.Paint.Style.STROKE
        }
        fun strokeSweep(angle: Float, stroke: Float, a: Int) {
            paint.strokeWidth = stroke
            paint.alpha = (a * alpha).roundToInt().coerceIn(0, 255)
            paint.shader = AndroidSweepGradient(cx, cy, argb, null).also { shader ->
                val matrix = Matrix()
                matrix.setRotate(angle, cx, cy)
                shader.setLocalMatrix(matrix)
            }
            val inset = paint.strokeWidth / 2f
            canvas.nativeCanvas.drawRoundRect(
                inset, inset, size.width - inset, size.height - inset, cornerPx, cornerPx, paint,
            )
        }
        strokeSweep(rimAngle, 2.5.dp.toPx(), 220)
        strokeSweep(bandAngle, 4.dp.toPx(), 90)
        paint.shader = null
        paint.color = android.graphics.Color.BLACK
        for ((strength, a) in listOf(1.0f to 0.32f, 0.6f to 0.16f, 0.3f to 0.08f)) {
            paint.alpha = (a * 255f * alpha).roundToInt().coerceIn(0, 255)
            paint.strokeWidth = (1.2f + strength).dp.toPx()
            canvas.nativeCanvas.drawPath(
                wavyRoundRectPath(size.width, size.height, strength * 3.dp.toPx(), wavePhase),
                paint,
            )
        }
    }
}

private fun wavyRoundRectPath(width: Float, height: Float, amplitude: Float, phase: Float): Path {
    val path = Path()
    val steps = 96
    fun point(t: Float): Pair<Float, Float> {
        val peri = 2f * (width + height)
        val d = ((t + phase) % 1f) * peri
        val base = when {
            d < width -> d to 0f
            d < width + height -> width to (d - width)
            d < 2f * width + height -> (width - (d - width - height)) to height
            else -> 0f to (height - (d - 2f * width - height))
        }
        val ang = t * Math.PI.toFloat() * 6f
        return (base.first + cos(ang.toDouble()).toFloat() * amplitude) to
            (base.second + sin(ang.toDouble()).toFloat() * amplitude)
    }
    val first = point(0f)
    path.moveTo(first.first.coerceIn(0f, width), first.second.coerceIn(0f, height))
    for (i in 1..steps) {
        val p = point(i / steps.toFloat())
        path.lineTo(
            p.first.coerceIn(-amplitude, width + amplitude),
            p.second.coerceIn(-amplitude, height + amplitude),
        )
    }
    path.close()
    return path
}

private fun lerp(a: Float, b: Float, t: Float): Float = a + (b - a) * t
