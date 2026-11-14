package top.wkbin.taixu.runtime.virtualdisplay

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VirtualDisplayFrameCapturesTest {
    @Test
    fun registerCaptureAndUnregister() = runBlocking {
        val session = "frame-test"
        val png = byteArrayOf(1, 2, 3)
        val capture: suspend () -> ByteArray? = { png }
        VirtualDisplayFrameCaptures.register(session, capture)
        assertArrayEquals(png, VirtualDisplayFrameCaptures.capture(session))
        VirtualDisplayFrameCaptures.unregister(session, capture)
        assertNull(VirtualDisplayFrameCaptures.capture(session))
    }
}
