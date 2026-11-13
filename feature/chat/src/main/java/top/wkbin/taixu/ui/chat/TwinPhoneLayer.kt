package top.wkbin.taixu.ui.chat

import android.graphics.BlurMaskFilter
import android.graphics.Matrix
import android.graphics.Path
import android.graphics.SweepGradient as AndroidSweepGradient
import android.os.Build
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TileMode
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
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlinx.coroutines.launch
import top.wkbin.taixu.ui.miuix.RuntimeTheme
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.squircle.squircleSurface

private val MiniPhoneWidth = 124.dp
private val TwinExpandSpec = tween<Float>(360, easing = FastOutSlowInEasing)
private val TwinSnapSpec = tween<Float>(220, easing = FastOutSlowInEasing)
private const val FallbackPhoneAspect = 0.516f
/** 小窗近直角矩形（约 1dp），大窗收成 0。 */
private val MiniCornerDp = 1f
private val MiniBorderColor = Color(0xFFD7DAE0)
private val MiniEdgeInset = 12.dp
private val MiniEdgeMin = 8.dp
private val MiniBottomSlack = 10.dp

internal enum class TwinGlowKind { Running, Need, Done, Failed }

/** 小窗状态 chip。 */
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
    // 小窗满光；大窗任务中保留弱流光。
    val glowAlpha by animateFloatAsState(
        when {
            !sweepActive -> 0f
            expanded -> 0.42f
            else -> 1f
        },
        tween(400),
        label = "guestGlow",
    )
    // 注意：不要把 content 换到另一套 Compose 父级（1dp 离屏 Box）。
    // AndroidView/SurfaceView 换父会 surfaceDestroyed→黑屏，而 screencap 仍正常。
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
        val corner = lerp(MiniCornerDp, 0f, progress)
        val glow = rememberInfiniteTransition(label = "guestGlowFlow")
        val rimAngle by glow.animateFloat(
            0f, 360f, infiniteRepeatable(tween(4000, easing = LinearEasing)), label = "rim",
        )
        val wavePhase by glow.animateFloat(
            0f, 1f, infiniteRepeatable(tween(6000, easing = LinearEasing)), label = "waves",
        )
        val showGlow = glowAlpha > 0.02f
        val glowStrength = glowAlpha * lerp(1f, 0.45f, progress.coerceIn(0f, 1f))
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
        // 光晕层：扫色盘 + 系统模糊（API31+），画在画面外，不是描边流光。
        if (showGlow) {
            val blurPx = with(density) { (minOf(widthPx, heightPx) * 0.12f).coerceIn(12.dp.toPx(), 36.dp.toPx()) }
            Box(
                Modifier
                    .offset { IntOffset(left.roundToInt(), top.roundToInt()) }
                    .width(with(density) { widthPx.toDp() })
                    .height(with(density) { heightPx.toDp() })
                    .graphicsLayer {
                        alpha = (if (expanded) 1f else miniAlpha) * glowStrength
                        translationX = if (!expanded && !miniVisible && progress < 0.05f) -10_000f else 0f
                        if (Build.VERSION.SDK_INT >= 31) {
                            renderEffect = BlurEffect(blurPx, blurPx, TileMode.Decal)
                            clip = false
                        }
                    }
                    .drawBehind {
                        drawGuestLightBloom(
                            glowColors, rimAngle, wavePhase, corner.dp.toPx(),
                            alpha = 1f,
                            softBlur = Build.VERSION.SDK_INT < 31,
                        )
                    },
            )
        }
        Box(
            Modifier
                .offset { IntOffset(left.roundToInt(), top.roundToInt()) }
                .width(with(density) { widthPx.toDp() })
                .height(with(density) { heightPx.toDp() })
                .graphicsLayer {
                    alpha = if (expanded) 1f else miniAlpha
                    translationX = if (!expanded && !miniVisible && progress < 0.05f) -10_000f else 0f
                }
                .then(
                    if (showGlow) {
                        Modifier
                            .shadow(
                                elevation = 18.dp,
                                shape = cornerShape,
                                ambientColor = glowColors[0].copy(alpha = 0.45f * glowStrength),
                                spotColor = glowColors[2 % glowColors.size].copy(alpha = 0.35f * glowStrength),
                            )
                    } else {
                        Modifier.shadow(elevation = (8f * progress).dp, shape = cornerShape)
                    },
                )
                .clip(cornerShape)
                .then(
                    if (progress < 0.5f) {
                        Modifier.border(1.dp, MiniBorderColor.copy(alpha = 0.55f * (1f - progress)), cornerShape)
                    } else Modifier,
                )
                .then(
                    if (showGlow) {
                        Modifier.drawWithContent {
                            drawContent()
                            // 内侧淡扫光，仍是 bloom 而非硬边框。
                            drawGuestInnerWash(glowColors, rimAngle, glowStrength * 0.35f)
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

/**
 * 外侧扫光 bloom：填充色盘（由外层 RenderEffect 模糊），不是描边彩虹框。
 * [softBlur]=true 时用 BlurMaskFilter 兜底低版本。
 */
private fun DrawScope.drawGuestLightBloom(
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
            if (softBlur) {
                paint.maskFilter = BlurMaskFilter(6.dp.toPx() * ampScale, BlurMaskFilter.Blur.NORMAL)
            } else {
                paint.maskFilter = null
            }
            canvas.nativeCanvas.drawPath(
                softWaveRectPath(w, h, ampScale * 4.dp.toPx(), wavePhase),
                paint,
            )
        }
        // 贴矩形外缘再铺一层淡色光晕。
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

/** 画面内侧极淡扫光洗色，避免硬边。 */
private fun DrawScope.drawGuestInnerWash(
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
        // 三谐波，接近 breathing 光边而非锯齿描边。
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

private fun lerp(a: Float, b: Float, t: Float): Float = a + (b - a) * t
