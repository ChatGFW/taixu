package top.wkbin.taixu.runtime.virtualdisplay

import org.junit.Assert.assertEquals
import org.junit.Test

class TwinPhoneEmbedSupportTest {
    @Test
    fun mapViewToDisplay_matchesTwoyiGuestStretchScale() {
        // twoyi b8.p0: scale(vdW/viewW, vdH/viewH); clamp to VD pixel range.
        val (x, y) = TwinPhoneEmbedSupport.mapViewToDisplay(
            viewX = 216f,
            viewY = 476.5f,
            viewW = 432,
            viewH = 953,
            vdW = 1088,
            vdH = 2400,
        )
        assertEquals(544f, x, 0.01f)
        assertEquals(476.5f * 2400f / 953f, y, 0.01f)
    }

    @Test
    fun mapViewToDisplay_cornersStayInsideVd() {
        val (x0, y0) = TwinPhoneEmbedSupport.mapViewToDisplay(0f, 0f, 400, 800, 1080, 2340)
        val (x1, y1) = TwinPhoneEmbedSupport.mapViewToDisplay(400f, 800f, 400, 800, 1080, 2340)
        assertEquals(0f, x0, 0f)
        assertEquals(0f, y0, 0f)
        assertEquals(1079f, x1, 0f)
        assertEquals(2339f, y1, 0f)
    }
}
