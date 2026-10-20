package top.wkbin.taixu.ui.bot

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import top.wkbin.taixu.core.model.bot.BotAvatarState

class PixelOfficeAmbientTest {
    private val actor = OfficeActor(
        botId = "bot-a",
        name = "阿策",
        x = 48,
        y = 140,
        pose = OfficePose.THINK,
        state = BotAvatarState.SUCCESS,
    )

    @Test
    fun `ambient frame is deterministic and stays inside the room`() {
        val now = 120_000L
        val first = PixelOfficeAmbient.dots(now, listOf(actor))
        val second = PixelOfficeAmbient.dots(now, listOf(actor))
        assertEquals(first, second)
        assertTrue(first.isNotEmpty())
        first.forEach { dot ->
            assertTrue("x=${dot.x}", dot.x in 0f..192f)
            assertTrue("y=${dot.y}", dot.y in 0f..448f)
            assertTrue("w=${dot.w}", dot.w > 0f)
            assertTrue("h=${dot.h}", dot.h > 0f)
        }
    }

    @Test
    fun `steam window lamps and screens are always alive`() {
        val dots = PixelOfficeAmbient.dots(42_000L, emptyList())
        assertTrue(dots.any { it.y in 193f..206f && it.x in 126f..132f })
        assertTrue(dots.any { it.x in 121f..184f && it.y in 14f..32f })
        assertTrue(dots.any { it.x in 87f..162f && it.y in 90f..344f })
        assertTrue(dots.any { it.x in 36f..96f && it.y in 196f..256f })
        assertTrue(dots.any { it.x in 183f..185f && it.y in 281f..300f })
        assertTrue(dots.any { it.x in 133f..134f && it.y in 196f..198f })
    }

    @Test
    fun `successful actor gets confetti above the head`() {
        val withConfetti = PixelOfficeAmbient.dots(80_000L, listOf(actor), celebrating = setOf(actor.botId))
        val plain = PixelOfficeAmbient.dots(80_000L, listOf(actor))
        assertTrue(withConfetti.size > plain.size)
        assertTrue(withConfetti.any { dot ->
            dot.x in 40f..56f && dot.y in actor.y - 34f..actor.y - 20f
        })
    }

    @Test
    fun `petting a cat and brewing coffee add feedback particles`() {
        val now = 60_000L
        val plain = PixelOfficeAmbient.dots(now, emptyList())
        val pet = PixelOfficeAmbient.dots(now, emptyList(), lobbyPetAt = now - 400L)
        val brew = PixelOfficeAmbient.dots(now, emptyList(), brewAt = now - 400L)
        assertTrue(pet.size > plain.size)
        assertTrue(brew.size > plain.size)
        assertTrue(pet.any { it.x in 74f..90f && it.y in 32f..54f })
        assertTrue(brew.any { it.x in 118f..142f && it.y in 186f..206f })
    }
}
