package top.wkbin.taixu.ui.bot

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import top.wkbin.taixu.core.model.bot.BotAvatarState
import top.wkbin.taixu.core.model.bot.PixelAvatarRecipe
import kotlin.math.roundToInt

/** Full-bleed pixel office. Characters are hit targets; speech bubbles use real type. */
@Composable
internal fun GroupPixelOffice(
    actors: List<OfficeActor>,
    recipes: Map<String, PixelAvatarRecipe>,
    bubbles: Map<String, String>,
    screens: Map<String, String> = emptyMap(),
    selectedId: String?,
    emptyHint: String?,
    onSelect: (String) -> Unit,
    objective: String? = null,
    modifier: Modifier = Modifier,
) {
    val room = remember { PixelOfficeScene.pixels().toPixelImage(PixelOfficeDetail.WIDTH, PixelOfficeScene.HEIGHT * PixelOfficeDetail.SCALE) }
    val transition = rememberInfiniteTransition(label = "pixel-office")
    val frameFloat = transition.animateFloat(
        initialValue = 0f,
        targetValue = 3f,
        animationSpec = infiniteRepeatable(tween(900, easing = LinearEasing), RepeatMode.Restart),
        label = "pixel-office-frame",
    )
    val yawnFloat = transition.animateFloat(
        initialValue = 0f,
        targetValue = 8f,
        animationSpec = infiniteRepeatable(tween(6400, easing = LinearEasing), RepeatMode.Restart),
        label = "pixel-office-yawn",
    )
    val frame by remember { derivedStateOf { frameFloat.value.toInt().coerceIn(0, 2) } }
    val yawnSlot by remember { derivedStateOf { yawnFloat.value.toInt() } }
    val panXState = remember { mutableFloatStateOf(0f) }
    val panYState = remember { mutableFloatStateOf(0f) }
    val zoomState = remember { mutableFloatStateOf(1f) }
    val metrics = remember { OfficeMetrics() }
    var aimed by remember { mutableStateOf(false) }
    BoxWithConstraints(
        modifier
            .fillMaxSize()
            .statusBarsPadding()
            .clipToBounds()
            .officeGestures(metrics)
            .testTag("pixel-office-scene"),
    ) {
        val widthPx = constraints.maxWidth
        val heightPx = constraints.maxHeight
        metrics.viewW = widthPx
        metrics.viewH = heightPx
        metrics.panX = panXState
        metrics.panY = panYState
        metrics.zoom = zoomState
        metrics.baseScale = if (widthPx > 0 && heightPx > 0) minOf(
            widthPx / PixelOfficeRoom.WIDTH.toFloat(),
            heightPx / PixelOfficeScene.HEIGHT.toFloat(),
        ) else 1f
        if (widthPx > 0 && heightPx > 0) {
            val scale = metrics.scale
            val mapW = (PixelOfficeRoom.WIDTH * scale).roundToInt()
            val mapH = (PixelOfficeScene.HEIGHT * scale).roundToInt()
            LaunchedEffect(widthPx, heightPx) {
                val (x, y) = if (!aimed) {
                    aimed = true
                    openingOfficeCamera(widthPx, heightPx, metrics.baseScale * zoomState.floatValue)
                } else {
                    clampOfficeCamera(panXState.floatValue, panYState.floatValue, metrics.baseScale * zoomState.floatValue, widthPx, heightPx)
                }
                panXState.floatValue = x
                panYState.floatValue = y
            }
            val panX = panXState.floatValue
            val panY = panYState.floatValue
            Canvas(Modifier.fillMaxSize()) {
                drawRect(Color(PixelOfficeRoom.FLOOR))
                drawImage(
                    room,
                    dstOffset = IntOffset(panX.roundToInt(), panY.roundToInt()),
                    dstSize = IntSize(mapW, mapH),
                    filterQuality = FilterQuality.None,
                )
            }
            actors.sortedBy { it.y }.forEach { actor ->
                key(actor.botId) {
                    OfficeActorSprite(
                        actor = actor.copy(y = PixelOfficeScene.y(actor.y.toFloat()).roundToInt()),
                        recipe = recipes[actor.botId] ?: PixelAvatarRecipe.fromName(actor.botId),
                        frame = frame,
                        yawnSlot = yawnSlot,
                        originX = panX.roundToInt(),
                        originY = panY.roundToInt(),
                        scale = scale,
                        sceneWidth = widthPx,
                        sceneHeight = heightPx,
                        bubble = bubbles[actor.botId],
                        screen = screens[actor.botId],
                        selected = actor.botId == selectedId,
                        onSelect = { onSelect(actor.botId) },
                    )
                }
            }
            OfficeObjectiveBoard(objective, panX, panY, scale)
            if (actors.isEmpty() && !emptyHint.isNullOrBlank()) {
                Surface(
                    modifier = Modifier.align(Alignment.Center).padding(24.dp).testTag("pixel-office-empty"),
                    shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.surface,
                    shadowElevation = 6.dp,
                ) {
                    Text(
                        text = emptyHint,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun OfficeActorSprite(
    actor: OfficeActor,
    recipe: PixelAvatarRecipe,
    frame: Int,
    yawnSlot: Int,
    originX: Int,
    originY: Int,
    scale: Float,
    sceneWidth: Int,
    sceneHeight: Int,
    bubble: String?,
    screen: String?,
    selected: Boolean,
    onSelect: () -> Unit,
) {
    val x by animateIntAsState(actor.x, tween(700), label = "office-x-${actor.botId}")
    val y by animateIntAsState(actor.y, tween(700), label = "office-y-${actor.botId}")
    val offset = officeFrameOffset(actor.botId)
    val localFrame = (frame + offset) % 3
    val yawn = actor.state == BotAvatarState.IDLE && (yawnSlot + offset) % 8 == 7
    val waiting = actor.waitSeconds != null
    val image = PixelOfficeFrames.bitmap(recipe, actor.pose, actor.state, localFrame, waiting, yawn)
    val mood = PixelOfficeMood.image(actor.state, localFrame, waiting, yawn)
    val density = LocalDensity.current
    val left = originX + ((x - PixelOfficeSprite.WIDTH / 2f) * scale).roundToInt()
    val top = originY + ((y - PixelOfficeSprite.HEIGHT) * scale).roundToInt()
    val sw = (PixelOfficeSprite.WIDTH * scale).roundToInt().coerceAtLeast(1)
    val sh = (PixelOfficeSprite.HEIGHT * scale).roundToInt().coerceAtLeast(1)
    val highlight = MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
    Canvas(
        Modifier
            .offset { IntOffset(left, top) }
            .size(with(density) { sw.toDp() }, with(density) { sh.toDp() })
            .testTag("studio-actor-${actor.botId}")
            .clickable(onClick = onSelect),
    ) {
        if (selected) {
            drawOval(
                highlight,
                topLeft = Offset(size.width * 0.05f, size.height * 0.82f),
                size = Size(size.width * 0.9f, size.height * 0.16f),
            )
        }
        drawImage(
            image,
            dstSize = IntSize(size.width.roundToInt().coerceAtLeast(1), size.height.roundToInt().coerceAtLeast(1)),
            filterQuality = FilterQuality.None,
        )
    }
    if (mood != null) {
        val moodSize = (PixelOfficeMood.EDGE * scale).roundToInt().coerceAtLeast(1)
        Canvas(
            Modifier
                .offset { IntOffset(left + (sw - moodSize) / 2, top - moodSize) }
                .size(with(density) { moodSize.toDp() }),
        ) {
            drawImage(
                mood,
                dstSize = IntSize(size.width.roundToInt().coerceAtLeast(1), size.height.roundToInt().coerceAtLeast(1)),
                filterQuality = FilterQuality.None,
            )
        }
    }
    if (actor.pose == OfficePose.TYPE && !screen.isNullOrBlank()) {
        OfficeScreenLabel(
            text = screen,
            roomX = x - 8,
            roomY = y - 32,
            originX = originX,
            originY = originY,
            scale = scale,
        )
    }
    val visible = left < sceneWidth && left + sw > 0 && top < sceneHeight && top + sh > 0
    val speaking = visible && !bubble.isNullOrBlank()
    if (!speaking && selected) {
        val nameLift = ((if (mood != null) PixelOfficeMood.EDGE else 0) + 8f) * scale
        PixelOfficeChip(
            name = actor.name,
            text = null,
            seconds = null,
            state = actor.state,
            waiting = waiting,
            bubbleTag = null,
            scale = scale,
            modifier = Modifier.offset { IntOffset(left, (top - nameLift).roundToInt()) }
                .clickable(onClick = onSelect),
        )
    }
    if (speaking) {
        val bubbleW = (42f * scale).roundToInt()
        val bubbleH = ((if (mood != null) PixelOfficeMood.EDGE else 0) + 16f) * scale
        val bubbleLeft = (left + sw / 2 - bubbleW / 2).coerceIn(0, (sceneWidth - bubbleW).coerceAtLeast(0))
        val bubbleTop = (top - bubbleH).roundToInt().coerceAtLeast(0)
        PixelOfficeChip(
            name = actor.name,
            text = bubble,
            seconds = actor.waitSeconds,
            state = actor.state,
            waiting = waiting,
            bubbleTag = "studio-bubble-${actor.botId}",
            scale = scale,
            modifier = Modifier.offset { IntOffset(bubbleLeft, bubbleTop) }
                .clickable(onClick = onSelect),
        )
    }
}

