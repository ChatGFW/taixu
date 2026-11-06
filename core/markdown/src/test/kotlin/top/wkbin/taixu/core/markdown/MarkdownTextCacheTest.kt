package top.wkbin.taixu.core.markdown

import java.util.concurrent.Executors
import org.junit.Assert.*
import org.junit.Test

class MarkdownTextCacheTest {
    @Test fun `character budget evicts least recently used source`() {
        val cache = MarkdownTextCache<String>(3, 10)
        cache.put("a", "aaaa", "A"); cache.put("b", "bbbb", "B")
        assertEquals("A", cache.get("a", "aaaa"))
        cache.put("c", "cccc", "C")
        assertNull(cache.get("b", "bbbb"))
        assertEquals("A", cache.get("a", "aaaa"))
        assertEquals(8, cache.sourceChars)
    }
    @Test fun `entry limit replacement and oversized sources keep accounting correct`() {
        val cache = MarkdownTextCache<String>(2, 10)
        cache.put("a", "a", "A"); cache.put("b", "b", "B"); cache.put("c", "c", "C")
        assertNull(cache.get("a", "a"))
        cache.put("b", "bb", "B2")
        assertNull(cache.get("b", "b")); assertEquals("B2", cache.get("b", "bb"))
        cache.put("b", "x".repeat(11), "too big")
        assertNull(cache.get("b", "bb")); assertEquals(1, cache.sourceChars)
        cache.evictAll(); assertEquals(0, cache.size); assertEquals(0, cache.sourceChars)
    }
    @Test fun `concurrent renderers never observe a mismatched source`() {
        val cache = MarkdownTextCache<String>(8, 80)
        val executor = Executors.newFixedThreadPool(4)
        try {
            val tasks = (0..3).map { worker -> executor.submit {
                repeat(1_000) { n ->
                    val source = "$worker-$n"
                    cache.put("same", source, source)
                    val result = cache.get("same", source)
                    assertTrue(result == null || result == source)
                }
            } }
            tasks.forEach { it.get() }
            assertTrue(cache.sourceChars <= 80)
        } finally { executor.shutdownNow() }
    }
}
