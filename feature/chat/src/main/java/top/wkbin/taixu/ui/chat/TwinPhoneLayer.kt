package top.wkbin.taixu.ui.chat

import android.os.Build
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlinx.coroutines.launch
import top.wkbin.taixu.runtime.virtualdisplay.TwinPhonePresence
import top.wkbin.taixu.ui.miuix.RuntimeTheme

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

/**
 * 分身画面层：小窗 ↔ 大窗；四角吸附；任务中光晕。
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
    val glowAlpha by animateFloatAsState(
        when {
            !sweepActive -> 0f
            expanded -> 0.42f
            else -> 1f
        },
        tween(400),
        label = "guestGlow",
    )
    // 不要把 content 换到另一套 Compose 父级，否则 SurfaceView 重建→黑屏。
    val scope = rememberCoroutineScope()
    val glowColors = remember(glowKind) { glowPalette(glowKind) }
    val pageBg = RuntimeTheme.colorScheme.background
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val density = LocalDensity.current
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
        // 大窗按 VD 宽高比落入可用区，避免拉满父级导致 TextureView 裁切/变形。
        val expandedH = minOf(availH, availW / aspect).coerceAtLeast(1f)
        val expandedW = (expandedH * aspect).coerceAtMost(availW)
        val expandedLeft = sidePad + (availW - expandedW) / 2f
        val expandedTop = topPad + (availH - expandedH) / 2f
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
        val widthPx = lerp(miniW, expandedW, progress)
        val heightPx = lerp(miniH, expandedH, progress)
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
        if (showGlow) {
            val blurPx = with(density) {
                (minOf(widthPx, heightPx) * 0.12f).coerceIn(12.dp.toPx(), 36.dp.toPx())
            }
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
                .onGloballyPositioned { coords ->
                    val pos = coords.positionInWindow()
                    TwinPhonePresence.updateSourceRectHint(
                        pos.x.roundToInt(),
                        pos.y.roundToInt(),
                        (pos.x + coords.size.width).roundToInt(),
                        (pos.y + coords.size.height).roundToInt(),
                        aspect,
                    )
                }
                .graphicsLayer {
                    alpha = if (expanded) 1f else miniAlpha
                    translationX = if (!expanded && !miniVisible && progress < 0.05f) -10_000f else 0f
                }
                .then(
                    if (showGlow) {
                        Modifier.shadow(
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
            // 仅小窗拦截拖拽/点击；expanded 意图下绝不盖住预览，否则接手操作点不到画面。
            if (!expanded && progress < 0.5f && miniAlpha > 0.05f) {
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

private fun lerp(a: Float, b: Float, t: Float): Float = a + (b - a) * t
