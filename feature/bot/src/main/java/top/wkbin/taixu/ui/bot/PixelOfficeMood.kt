package top.wkbin.taixu.ui.bot

import androidx.compose.ui.graphics.ImageBitmap
import top.wkbin.taixu.core.model.bot.BotAvatarState

/** Tiny looping mark above a character: bulb, sparks, bang, hourglass, check or Zzz. */
internal object PixelOfficeMood {
    const val EDGE = 16

    private const val INK = 0xFF3A2A1E.toInt()
    private const val YELLOW = 0xFFFFD54F.toInt()
    private const val GLOW = 0xFFFFF3C4.toInt()
    private const val RED = 0xFFE53935.toInt()
    private const val GREEN = 0xFF43A047.toInt()
    private const val SAND = 0xFFE0B15A.toInt()
    private const val BLUE = 0xFF4FC3F7.toInt()

    fun cells(state: BotAvatarState, frame: Int, waiting: Boolean, yawn: Boolean): IntArray {
        val pixels = IntArray(EDGE * EDGE)
        fun dot(x: Int, y: Int, color: Int) {
            if (x in 0 until EDGE && y in 0 until EDGE) pixels[y * EDGE + x] = color
        }
        fun rect(x: Int, y: Int, w: Int, h: Int, color: Int) {
            for (row in y until y + h) for (col in x until x + w) dot(col, row, color)
        }
        when {
            waiting -> hourglass(frame, ::rect, ::dot)
            state == BotAvatarState.THINKING -> when (frame) {
                0 -> bulb(::rect, ::dot)
                1 -> gear(frame, ::dot)
                else -> question(::rect, ::dot)
            }
            state == BotAvatarState.EXECUTING -> sparks(frame, ::rect, ::dot)
            state == BotAvatarState.BLOCKED || state == BotAvatarState.FAILED -> if (frame != 1) bang(::rect)
            state == BotAvatarState.SUCCESS -> check(::dot)
            yawn && state == BotAvatarState.IDLE -> zzz(frame, ::rect)
        }
        return pixels
    }

    private val images = HashMap<String, ImageBitmap?>()

    fun image(state: BotAvatarState, frame: Int, waiting: Boolean, yawn: Boolean): ImageBitmap? {
        val index = frame.coerceIn(0, 2)
        val key = "$state|$index|$waiting|$yawn"
        if (images.containsKey(key)) return images[key]
        val painted = cells(state, index, waiting, yawn)
        val bitmap = if (painted.any { it != 0 }) painted.toPixelImage(EDGE, EDGE) else null
        images[key] = bitmap
        return bitmap
    }

    private fun bulb(rect: (Int, Int, Int, Int, Int) -> Unit, dot: (Int, Int, Int) -> Unit) {
        rect(5, 3, 6, 6, YELLOW)
        rect(6, 4, 4, 4, GLOW)
        rect(6, 9, 4, 2, INK)
        dot(7, 1, YELLOW)
        dot(4, 5, YELLOW)
        dot(11, 5, YELLOW)
    }

    private fun gear(frame: Int, dot: (Int, Int, Int) -> Unit) {
        val shift = frame % 2
        dot(7, 3 + shift, INK)
        dot(4, 6, INK)
        dot(10, 6, INK)
        dot(5, 8, INK)
        dot(9, 8, INK)
        dot(7, 11 - shift, INK)
        dot(7, 6, YELLOW)
        dot(8, 7, YELLOW)
        dot(6, 7, YELLOW)
        dot(7, 8, YELLOW)
    }

    private fun question(rect: (Int, Int, Int, Int, Int) -> Unit, dot: (Int, Int, Int) -> Unit) {
        rect(5, 2, 6, 2, BLUE)
        rect(9, 4, 2, 3, BLUE)
        rect(6, 7, 3, 2, BLUE)
        dot(7, 11, BLUE)
    }

    private fun sparks(frame: Int, rect: (Int, Int, Int, Int, Int) -> Unit, dot: (Int, Int, Int) -> Unit) {
        rect(6, 6, 2, 7, INK)
        rect(4, 5, 6, 2, INK)
        val x = 2 + frame * 4
        dot(x, 3, YELLOW)
        dot(x + 1, 4, 0xFFFFF8EE.toInt())
        dot(14 - frame * 3, 8, YELLOW)
    }

    private fun bang(rect: (Int, Int, Int, Int, Int) -> Unit) {
        rect(7, 1, 2, 9, RED)
        rect(7, 12, 2, 2, RED)
    }

    private fun check(dot: (Int, Int, Int) -> Unit) {
        dot(3, 8, GREEN)
        dot(5, 10, GREEN)
        dot(6, 11, GREEN)
        dot(8, 9, GREEN)
        dot(10, 7, GREEN)
        dot(12, 5, GREEN)
        dot(13, 4, GREEN)
    }

    private fun hourglass(frame: Int, rect: (Int, Int, Int, Int, Int) -> Unit, dot: (Int, Int, Int) -> Unit) {
        rect(3, 1, 10, 2, INK)
        rect(3, 13, 10, 2, INK)
        rect(5, 3, 6, 3, INK)
        rect(7, 6, 2, 4, INK)
        rect(5, 10, 6, 3, INK)
        if (frame == 0) rect(6, 3, 4, 2, SAND) else rect(6, 11, 4, 2, SAND)
        if (frame == 1) dot(7, 7, SAND) else dot(8, 8, SAND)
    }

    private fun zzz(frame: Int, rect: (Int, Int, Int, Int, Int) -> Unit) {
        val rise = frame * 2
        rect(8, 8 - rise, 5, 1, BLUE)
        rect(11, 6 - rise, 2, 2, BLUE)
        rect(8, 5 - rise, 5, 1, BLUE)
    }
}
