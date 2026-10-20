package top.wkbin.taixu.ui.bot

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.MutableFloatState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import android.os.SystemClock
import kotlin.math.hypot

internal const val OFFICE_MIN_ZOOM = 1f
internal const val OFFICE_MAX_ZOOM = 3f

/** Screen camera for the pixel office: one finger pans, two fingers pinch around the midpoint. */
internal class OfficeMetrics {
    var viewW = 0
    var viewH = 0
    var baseScale = 1f
    lateinit var panX: MutableFloatState
    lateinit var panY: MutableFloatState
    lateinit var zoom: MutableFloatState

    val scale: Float get() = baseScale * zoom.floatValue

    fun panBy(delta: Offset) {
        if (viewW <= 0) return
        val next = clampOfficeCamera(
            panX.floatValue + delta.x,
            panY.floatValue + delta.y,
            scale,
            viewW,
            viewH,
        )
        panX.floatValue = next.first
        panY.floatValue = next.second
    }

    fun zoomAbout(factor: Float, focal: Offset) {
        if (viewW <= 0 || factor == 1f) return
        val oldScale = scale
        if (oldScale <= 0f) return
        val nextZoom = (zoom.floatValue * factor).coerceIn(OFFICE_MIN_ZOOM, OFFICE_MAX_ZOOM)
        if (nextZoom == zoom.floatValue && factor < 1f) return
        val newScale = baseScale * nextZoom
        val worldX = (focal.x - panX.floatValue) / oldScale
        val worldY = (focal.y - panY.floatValue) / oldScale
        zoom.floatValue = nextZoom
        val next = clampOfficeCamera(focal.x - worldX * newScale, focal.y - worldY * newScale, newScale, viewW, viewH)
        panX.floatValue = next.first
        panY.floatValue = next.second
    }

    fun recenter() {
        if (viewW <= 0) return
        zoom.floatValue = 1f
        val home = openingOfficeCamera(viewW, viewH, baseScale)
        panX.floatValue = home.first
        panY.floatValue = home.second
    }
}

internal fun openingOfficeCamera(viewW: Int, viewH: Int, scale: Float): Pair<Float, Float> {
    val mapW = PixelOfficeRoom.WIDTH * scale
    val mapH = PixelOfficeScene.HEIGHT * scale
    return clampOfficeCamera((viewW - mapW) / 2f, (viewH - mapH) / 2f, scale, viewW, viewH)
}

internal fun clampOfficeCamera(x: Float, y: Float, scale: Float, viewW: Int, viewH: Int): Pair<Float, Float> {
    val mapW = PixelOfficeRoom.WIDTH * scale
    val mapH = PixelOfficeScene.HEIGHT * scale
    val clampedX = if (mapW <= viewW) (viewW - mapW) / 2f else x.coerceIn(viewW - mapW, 0f)
    val clampedY = if (mapH <= viewH) (viewH - mapH) / 2f else y.coerceIn(viewH - mapH, 0f)
    return clampedX to clampedY
}

internal fun Modifier.officeGestures(metrics: OfficeMetrics): Modifier = pointerInput(metrics) {
    val slop = viewConfiguration.touchSlop
    var lastTap = 0L
    awaitEachGesture {
        val down = awaitFirstDown(pass = PointerEventPass.Initial, requireUnconsumed = false)
        var total = Offset.Zero
        var panning = false
        var pinching = false
        while (true) {
            val event = awaitPointerEvent(pass = PointerEventPass.Initial)
            val pressed = event.changes.filter { it.pressed }
            if (pressed.isEmpty()) break
            if (pressed.size >= 2) {
                pinching = true
                val a = pressed[0]
                val b = pressed[1]
                val prev = hypot(
                    (a.previousPosition.x - b.previousPosition.x).toDouble(),
                    (a.previousPosition.y - b.previousPosition.y).toDouble(),
                ).toFloat()
                val dist = hypot(
                    (a.position.x - b.position.x).toDouble(),
                    (a.position.y - b.position.y).toDouble(),
                ).toFloat()
                val focal = Offset((a.position.x + b.position.x) / 2f, (a.position.y + b.position.y) / 2f)
                if (prev.isFinite() && dist.isFinite() && prev > slop && dist > slop) {
                    metrics.zoomAbout(dist / prev, focal)
                }
                val prevMid = Offset(
                    (a.previousPosition.x + b.previousPosition.x) / 2f,
                    (a.previousPosition.y + b.previousPosition.y) / 2f,
                )
                if (prevMid.x.isFinite() && prevMid.y.isFinite()) metrics.panBy(focal - prevMid)
                pressed.forEach { it.consume() }
            } else if (!pinching) {
                val change = pressed.firstOrNull { it.id == down.id } ?: pressed.first()
                total += change.positionChange()
                if (!panning && hypot(total.x.toDouble(), total.y.toDouble()) > slop) panning = true
                if (panning) {
                    metrics.panBy(change.position - change.previousPosition)
                    change.consume()
                }
            }
        }
        if (!panning && !pinching) {
            val now = SystemClock.uptimeMillis()
            if (now - lastTap in 1..299) {
                metrics.recenter()
                lastTap = 0L
            } else {
                lastTap = now
            }
        } else {
            lastTap = 0L
        }
    }
}
