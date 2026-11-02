package top.wkbin.taixu.harness

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AnthropicDataImageUrlTest {
    @Test
    fun `parses standard data image urls without regex`() {
        val huge = "A".repeat(50_000)
        val parsed = parseDataImageUrl("data:image/png;base64,$huge")
        assertEquals("image/png", parsed!!.first)
        assertEquals(huge, parsed.second)
        assertEquals("image/jpeg" to "abc+", parseDataImageUrl("data:image/jpeg;base64,abc+"))
        assertEquals("image/svg+xml" to "zzz", parseDataImageUrl("data:image/svg+xml;base64,zzz"))
    }

    @Test
    fun `rejects malformed data urls`() {
        assertNull(parseDataImageUrl("https://example.com/a.png"))
        assertNull(parseDataImageUrl("data:text/plain;base64,abc"))
        assertNull(parseDataImageUrl("data:image/png;base64,"))
        assertNull(parseDataImageUrl("data:image/png base64,abc"))
        assertNull(parseDataImageUrl("data:image/png;charset=utf-8;base64,abc"))
    }
}
