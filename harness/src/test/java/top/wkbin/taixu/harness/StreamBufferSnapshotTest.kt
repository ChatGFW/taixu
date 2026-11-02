package top.wkbin.taixu.harness

import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test
import top.wkbin.taixu.core.model.ChunkedText

class StreamBufferSnapshotTest {
    @Test fun `publishing shares blocks and final flush retains unpublished tail`() {
        val buffer = StreamBuffer()
        buffer.append("a".repeat(4_100))
        val first = buffer.publishSnapshotIfDue(100, 16)!!
        buffer.append("😀")
        assertNull(buffer.publishSnapshotIfDue(101, 16))
        val final = buffer.snapshot()
        assertTrue(final.isExtensionOf(first))
        assertEquals(4_100, first.length)
        assertEquals("a".repeat(4_100) + "😀", final.toString())
        buffer.clear()
        buffer.append("retry")
        val retried = buffer.publishSnapshotIfDue(102, 16)!!
        assertFalse(retried.isExtensionOf(final))
        assertEquals("retry", retried.toString())
    }

    @Test fun `reasoning character cap and legacy publication are unchanged`() {
        val buffer = StreamBuffer(maxChars = 5)
        buffer.append("abcdef")
        assertEquals("abcde", buffer.publishIfDue(100, 16))
        buffer.append("more")
        assertNull(buffer.publishIfDue(200, 16))
        assertEquals("abcde", buffer.toString())
    }

    @Test fun `wall-clock rollback does not publish until interval elapses on monotonic now`() {
        val buffer = StreamBuffer()
        buffer.append("one")
        assertEquals("one", buffer.publishIfDue(1_000, 50))
        buffer.append("two")
        // 墙钟回拨：若误用 currentTimeMillis，可能永远发不出或乱序；单调 now 下应继续节流。
        assertNull(buffer.publishIfDue(900, 50))
        assertNull(buffer.publishIfDue(1_040, 50))
        assertEquals("onetwo", buffer.publishIfDue(1_050, 50))
    }

    @Test fun `assistant wire format and string copy remain compatible`() {
        val json = Json { encodeDefaults = true }
        val builder = ChunkedText.Builder()
        builder.append("hello\n😀")
        val message = AssistantText("id", 1, builder.snapshot(), reasoning = "thinking")
        val encoded = json.encodeToString<HarnessMessage>(message)
        assertTrue(encoded.contains("\"text\":\"hello\\n😀\""))
        assertFalse(encoded.contains("textContent"))
        assertEquals(message, json.decodeFromString<HarnessMessage>(encoded))
        assertEquals("changed", message.copy(text = "changed").text)
        assertSame(message.textContent, message.copy(reasoning = "new reasoning").textContent)
    }
}
