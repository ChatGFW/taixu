package top.wkbin.taixu.runtime.virtualdisplay

import android.graphics.Bitmap
import android.view.MotionEvent
import kotlin.math.max
import kotlin.math.min

/** [TwinPhoneEmbedView] 触点映射与黑帧判定。 */
internal object TwinPhoneEmbedSupport {
    /** 铺满映射：View 坐标线性落到 VD（与 identity transform 一致）。 */
    fun mapViewToDisplay(
        viewX: Float,
        viewY: Float,
        viewW: Int,
        viewH: Int,
        vdW: Int,
        vdH: Int,
    ): Pair<Float, Float> {
        if (viewW <= 0 || viewH <= 0 || vdW <= 1 || vdH <= 1) return 0f to 0f
        val nx = (viewX / viewW).coerceIn(0f, 1f)
        val ny = (viewY / viewH).coerceIn(0f, 1f)
        return nx * (vdW - 1) to ny * (vdH - 1)
    }

    fun mapMotionEvent(
        event: MotionEvent,
        viewW: Int,
        viewH: Int,
        vdW: Int,
        vdH: Int,
    ): MotionEvent? {
        val count = event.pointerCount
        if (count <= 0) return null
        val props = Array(count) { MotionEvent.PointerProperties() }
        val coords = Array(count) { MotionEvent.PointerCoords() }
        for (i in 0 until count) {
            event.getPointerProperties(i, props[i])
            event.getPointerCoords(i, coords[i])
            val (dx, dy) = mapViewToDisplay(coords[i].x, coords[i].y, viewW, viewH, vdW, vdH)
            coords[i].x = dx
            coords[i].y = dy
        }
        return MotionEvent.obtain(
            event.downTime,
            event.eventTime,
            event.action,
            count,
            props,
            coords,
            event.metaState,
            event.buttonState,
            event.xPrecision,
            event.yPrecision,
            event.deviceId,
            event.edgeFlags,
            event.source,
            event.flags,
        )
    }

    fun isMostlyBlack(bitmap: Bitmap): Boolean {
        val w = bitmap.width
        val h = bitmap.height
        if (w <= 0 || h <= 0) return true
        val step = max(1, min(w, h) / 32)
        var dark = 0
        var total = 0
        var y = 0
        while (y < h) {
            var x = 0
            while (x < w) {
                val c = bitmap.getPixel(x, y)
                val lum = ((c shr 16) and 0xff) + ((c shr 8) and 0xff) + (c and 0xff)
                if (lum < 30) dark++
                total++
                x += step
            }
            y += step
        }
        return total > 0 && dark * 100 / total >= 96
    }
}
