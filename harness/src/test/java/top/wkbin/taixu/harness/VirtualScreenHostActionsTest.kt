package top.wkbin.taixu.harness

import org.junit.Assert.assertEquals
import org.junit.Test

class VirtualScreenHostActionsTest {
    @Test
    fun `png dimensions come from the IHDR header`() {
        val png = ByteArray(24)
        png[0] = 0x89.toByte()
        png[1] = 'P'.code.toByte()
        png[2] = 'N'.code.toByte()
        png[3] = 'G'.code.toByte()
        // width 1216, height 2640, big-endian at offsets 16 and 20
        png[16] = 0
        png[17] = 0
        png[18] = 4
        png[19] = 192.toByte()
        png[20] = 0
        png[21] = 0
        png[22] = 10
        png[23] = 80
        assertEquals(1216 to 2640, VirtualScreenHostActions.pngDimensions(png))
        assertEquals(0 to 0, VirtualScreenHostActions.pngDimensions(byteArrayOf(1, 2, 3)))
        assertEquals("image/png", VirtualScreenHostActions.imageMimeType(png))
    }

    @Test
    fun `jpeg dimensions come from SOF0`() {
        // FF D8 FF C0 00 11 08 hh hh ww ww …（最小 SOF0）
        val jpeg = byteArrayOf(
            0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xC0.toByte(),
            0x00, 0x11, 0x08,
            0x0A, 0x50, // height 2640
            0x04, 0xC0.toByte(), // width 1216
            0x03, 0x01, 0x22, 0x00, 0x02, 0x11, 0x01, 0x03, 0x11, 0x01,
        )
        assertEquals("image/jpeg", VirtualScreenHostActions.imageMimeType(jpeg))
        assertEquals(1216 to 2640, VirtualScreenHostActions.jpegDimensions(jpeg))
        assertEquals(1216 to 2640, VirtualScreenHostActions.imageDimensions(jpeg))
    }
}
