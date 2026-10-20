package top.wkbin.taixu.ui.bot

import top.wkbin.taixu.core.model.bot.BotAvatarState
import top.wkbin.taixu.core.model.bot.PixelAvatarArt
import top.wkbin.taixu.core.model.bot.PixelAvatarRecipe

/** 16×24 chibi that walks the office. Built from the same recipe colors as the portrait. */
internal object PixelOfficeSprite {
    const val WIDTH = 16
    const val HEIGHT = 24

    private const val INK = 0xFF2A2438.toInt()
    private const val WHITE = 0xFFFFF8EE.toInt()
    private const val SHOE = 0xFF3A322C.toInt()
    private const val BLUSH = 0xFFFF8A80.toInt()

    fun cells(
        recipe: PixelAvatarRecipe,
        pose: OfficePose,
        state: BotAvatarState,
        frame: Int,
        waiting: Boolean = false,
        yawn: Boolean = false,
    ): IntArray {
        val pixels = IntArray(WIDTH * HEIGHT)
        val skin = PixelAvatarArt.skinColors[recipe.skinColor]
        val hair = PixelAvatarArt.hairColors[recipe.hairColor]
        val shirt = PixelAvatarArt.outfitColors[recipe.outfitColor]
        val pants = shade(shirt, 0.62f)
        val drawn = if (waiting) OfficePose.STAND else pose
        if (drawn == OfficePose.TYPE || drawn == OfficePose.SIT) {
            return sideCells(recipe, drawn, state, frame, yawn)
        }
        val lift = if (frame == 1) 1 else 0
        fun rect(x: Int, y: Int, w: Int, h: Int, color: Int) {
            val top = y - lift
            for (row in top.coerceAtLeast(0) until (top + h).coerceAtMost(HEIGHT)) {
                for (col in x.coerceAtLeast(0) until (x + w).coerceAtMost(WIDTH)) {
                    pixels[row * WIDTH + col] = color
                }
            }
        }

        val head = if (drawn == OfficePose.SIT) 6 else 3
        if (recipe.kind == 1) {
            rect(2, head - 3, 3, 4, hair)
            rect(11, head - 3, 3, 4, hair)
            rect(3, head - 2, 1, 2, skin)
            rect(12, head - 2, 1, 2, skin)
        } else if (recipe.kind == 2) {
            rect(7, head - 3, 2, 3, shirt)
            rect(6, head - 1, 4, 2, INK)
        }
        rect(4, head, 8, 8, skin)
        rect(4, head, 8, 3, hair)
        rect(3, head + 3, 2, 4, hair)
        rect(11, head + 3, 2, 4, hair)
        when (recipe.hair) {
            1 -> rect(4, head + 2, 8, 2, hair)
            2 -> {
                rect(2, head + 4, 2, 8, hair)
                rect(12, head + 4, 2, 8, hair)
            }
            3 -> {
                rect(4, head - 2, 2, 3, hair)
                rect(7, head - 3, 2, 4, hair)
                rect(10, head - 2, 2, 3, hair)
            }
            4 -> {
                rect(6, head - 4, 4, 4, hair)
                rect(7, head - 3, 2, 2, shirt)
            }
        }
        val eye = head + 4
        if (recipe.kind == 3) {
            rect(3, eye - 1, 10, 4, INK)
            rect(4, eye, 8, 2, 0xFF00E5FF.toInt())
            rect(5, eye, 2, 1, WHITE)
        } else if (state == BotAvatarState.FAILED) {
            rect(5, eye, 2, 1, INK)
            rect(9, eye, 2, 1, INK)
        } else if (state == BotAvatarState.THINKING && frame != 0) {
            rect(5, eye + 1, 2, 1, INK)
            rect(9, eye + 1, 2, 1, INK)
        } else if (yawn && state == BotAvatarState.IDLE) {
            rect(5, eye + 1, 2, 1, INK)
            rect(9, eye + 1, 2, 1, INK)
        } else {
            rect(5, eye, 2, 2, INK)
            rect(9, eye, 2, 2, INK)
            rect(5, eye, 1, 1, WHITE)
            rect(9, eye, 1, 1, WHITE)
        }
        if (yawn && state == BotAvatarState.IDLE) rect(6, head + 6, 4, 2, 0xFF5C3A3A.toInt())
        else if (state == BotAvatarState.ACTIVE || state == BotAvatarState.SUCCESS) rect(6, head + 7, 4, 1, 0xFFC46A6A.toInt())
        else rect(7, head + 7, 2, 1, INK)
        if (state == BotAvatarState.SUCCESS) {
            rect(3, head + 6, 2, 1, BLUSH)
            rect(11, head + 6, 2, 1, BLUSH)
        }
        if (state == BotAvatarState.BLOCKED) rect(12, head + 1, 1, 3, 0xFF4FC3F7.toInt())

        val glasses = if (state == BotAvatarState.THINKING && frame == 2) 1 else 0
        when (recipe.accessory) {
            1 -> {
                rect(4, eye - 1 + glasses, 4, 1, INK)
                rect(8, eye - 1 + glasses, 4, 1, INK)
                rect(4, eye + 2 + glasses, 4, 1, INK)
                rect(8, eye + 2 + glasses, 4, 1, INK)
            }
            2 -> {
                rect(2, head + 1, 2, 6, INK)
                rect(12, head + 1, 2, 6, INK)
                rect(3, head, 10, 2, shirt)
            }
            3 -> {
                rect(3, head - 1, 10, 3, shirt)
                rect(2, head + 2, 12, 2, shirt)
            }
            5 -> {
                rect(3, eye - 1, 10, 3, INK)
                rect(4, eye, 8, 1, 0xFF00E5FF.toInt())
            }
        }

        val body = head + 8
        rect(4, body, 8, 6, shirt)
        rect(6, body, 4, 2, shade(shirt, 1.12f))
        when {
            waiting -> {
                rect(3, body + 2, 4, 3, shirt)
                rect(9, body + 2, 4, 3, shirt)
                rect(6, body + 3, 4, 2, skin)
            }
            state == BotAvatarState.BLOCKED -> {
                rect(2, body - 3, 3, 5, shirt)
                rect(11, body - 3, 3, 5, shirt)
                rect(3, head + 3, 2, 2, skin)
                rect(11, head + 3, 2, 2, skin)
            }
            state == BotAvatarState.SUCCESS -> {
                rect(1, body + 2, 3, 4, shirt)
                rect(12, body - 2, 3, 5, shirt)
                rect(13, body - 4, 2, 2, skin)
            }
            drawn == OfficePose.TYPE -> {
                rect(1, body + 2, 3, 3, shirt)
                rect(12, body + 2, 3, 3, shirt)
                rect(1, body + 5, 5, 2, INK)
                rect(10, body + 5, 5, 2, INK)
                if (frame % 2 == 0) {
                    rect(0, body + 3, 3, 2, skin)
                    rect(13, body + 5, 3, 2, skin)
                } else {
                    rect(0, body + 5, 3, 2, skin)
                    rect(13, body + 3, 3, 2, skin)
                }
            }
            drawn == OfficePose.THINK -> {
                rect(1, body + 1, 3, 3, shirt)
                rect(12, body + 2, 3, 3, shirt)
                rect(2, body - 1, 3, 2, skin)
            }
            drawn == OfficePose.SIT -> {
                rect(1, body + 3, 3, 2, shirt)
                rect(12, body + 3, 3, 2, shirt)
            }
            else -> {
                val swing = if (frame == 0) 0 else 1
                rect(1, body + 1 + swing, 3, 4, shirt)
                rect(12, body + 2 - swing, 3, 4, shirt)
            }
        }
        if (recipe.outfit == 2) rect(7, body + 1, 2, 5, INK)
        if (recipe.accessory == 6) {
            rect(10, body + 1, 1, 3, 0xFFE53935.toInt())
            rect(9, body + 4, 3, 3, WHITE)
        }

        val legs = body + 6
        if (drawn == OfficePose.SIT) {
            rect(2, legs, 12, 3, pants)
            rect(12, legs + 2, 3, 2, SHOE)
        } else {
            val step = if (frame == 0) 0 else 1
            rect(4, legs, 3, 5, pants)
            rect(9, legs, 3, 5, pants)
            rect(4, legs + 5 - step, 3, 2, SHOE)
            rect(9, legs + 4 + step, 3, 2, SHOE)
        }
        if (state == BotAvatarState.FAILED) rect(13, 1, 2, 2, 0xFFE53935.toInt())
        if (state == BotAvatarState.SUCCESS && frame == 0) rect(13, 1, 2, 2, LAMP_YELLOW)
        return outlined(pixels)
    }

    private const val LAMP_YELLOW = 0xFFFFD54F.toInt()

    /** Profile view: head and arms stay above the desk, legs fall behind the cover. */
    private fun sideCells(
        recipe: PixelAvatarRecipe,
        pose: OfficePose,
        state: BotAvatarState,
        frame: Int,
        yawn: Boolean,
    ): IntArray {
        val pixels = IntArray(WIDTH * HEIGHT)
        val skin = PixelAvatarArt.skinColors[recipe.skinColor]
        val hair = PixelAvatarArt.hairColors[recipe.hairColor]
        val shirt = PixelAvatarArt.outfitColors[recipe.outfitColor]
        val pants = shade(shirt, 0.62f)
        val lift = if (frame == 1) 1 else 0
        fun rect(x: Int, y: Int, w: Int, h: Int, color: Int) {
            val top = y - lift
            for (row in top.coerceAtLeast(0) until (top + h).coerceAtMost(HEIGHT)) {
                for (col in x.coerceAtLeast(0) until (x + w).coerceAtMost(WIDTH)) {
                    pixels[row * WIDTH + col] = color
                }
            }
        }
        if (pose == OfficePose.TYPE) {
            rect(1, 11, 2, 8, INK)
            rect(2, 12, 2, 6, 0xFF617584.toInt())
            rect(2, 18, 10, 2, INK)
            rect(5, 20, 2, 2, INK)
            rect(2, 22, 10, 1, INK)
        }
        rect(3, 3, 8, 7, skin)
        rect(2, 2, 7, 3, hair)
        rect(2, 4, 2, 6, hair)
        if (recipe.kind == 1) rect(3, 0, 3, 3, hair)
        if (recipe.kind == 2) rect(6, 0, 2, 3, shirt)
        val blink = (state == BotAvatarState.THINKING && frame != 0) || yawn
        if (recipe.kind == 3) rect(7, 5, 4, 2, 0xFF00E5FF.toInt())
        else if (blink) rect(8, 6, 2, 1, INK)
        else {
            rect(8, 5, 2, 2, INK)
            rect(8, 5, 1, 1, WHITE)
        }
        if (yawn) rect(8, 8, 2, 2, 0xFF5C3A3A.toInt()) else rect(9, 8, 2, 1, INK)
        if (state == BotAvatarState.SUCCESS) rect(5, 7, 2, 1, BLUSH)
        if (state == BotAvatarState.BLOCKED) rect(11, 4, 1, 2, 0xFF4FC3F7.toInt())
        if (recipe.accessory == 1) {
            val drop = if (state == BotAvatarState.THINKING && frame == 2) 1 else 0
            rect(7, 5 + drop, 4, 1, INK)
            rect(7, 7 + drop, 4, 1, INK)
        }
        rect(3, 10, 7, 4, shirt)
        val typing = pose == OfficePose.TYPE
        val hand = if (typing && frame % 2 == 1) 1 else 0
        if (state == BotAvatarState.SUCCESS) {
            rect(8, 6, 2, 4, shirt)
            rect(8, 5, 2, 2, skin)
        } else if (state == BotAvatarState.BLOCKED) {
            rect(6, 7, 3, 3, shirt)
            rect(7, 6, 2, 2, skin)
        } else {
            rect(8, 11, 4, 2, shirt)
            rect(11, 12 + hand, 3, 2, skin)
            if (typing) rect(10, 13, 5, 1, INK)
        }
        rect(3, 15, 6, 3, pants)
        rect(7, 17, 3, 4, pants)
        rect(7, 21, 4, 2, SHOE)
        return outlined(pixels)
    }

    /** One-pixel silhouette separates clothing and hair from the busy room behind it. */
    private fun outlined(pixels: IntArray): IntArray {
        val result = pixels.copyOf()
        for (y in 1 until HEIGHT - 1) for (x in 1 until WIDTH - 1) {
            val i = y * WIDTH + x
            if (pixels[i] == 0 && (pixels[i - 1] != 0 || pixels[i + 1] != 0 ||
                    pixels[i - WIDTH] != 0 || pixels[i + WIDTH] != 0)) {
                result[i] = INK
            }
        }
        return result
    }

    private fun shade(color: Int, factor: Float): Int {
        fun channel(shift: Int) = (((color ushr shift) and 255) * factor).toInt().coerceIn(0, 255)
        return (255 shl 24) or (channel(16) shl 16) or (channel(8) shl 8) or channel(0)
    }
}

/** Three cached frames per pose so the office does not allocate a bitmap on every tick. */
internal object PixelOfficeFrames {
    private val cache = HashMap<String, androidx.compose.ui.graphics.ImageBitmap>()

    fun bitmap(
        recipe: PixelAvatarRecipe,
        pose: OfficePose,
        state: BotAvatarState,
        frame: Int,
        waiting: Boolean,
        yawn: Boolean,
    ): androidx.compose.ui.graphics.ImageBitmap {
        val index = frame.coerceIn(0, 2)
        val key = "${recipe.hashCode()}|$pose|$state|$waiting|$yawn|$index"
        return cache.getOrPut(key) {
            PixelOfficeSprite.cells(recipe, pose, state, index, waiting, yawn)
                .toPixelImage(PixelOfficeSprite.WIDTH, PixelOfficeSprite.HEIGHT)
        }
    }
}
