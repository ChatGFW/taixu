package top.wkbin.taixu.core.markdown

import org.junit.After
import org.junit.Assert.*
import org.junit.Test

class MarkdownParserTest {
    @After fun clear() = clearParsedMarkdownCaches()

    @Test fun `blocks retain code literal data unicode tables and media order`() {
        val source = "# 标题😀\n\n段落 **粗体**\n\n```kotlin\n![literal](https://a.test/x.png)\n\nval a = 1\n```\n\n| A | B |\n| --- | --- |\n| 中文 | 😀 |\n\n前 ![图片](/workspace/a.png) 后"
        val blocks = parseMarkdownBlocks(source)
        assertEquals(MdHeading(1, "标题😀"), blocks[0])
        assertEquals(MdParagraph("段落 **粗体**"), blocks[1])
        assertEquals(MdCodeBlock("kotlin", "![literal](https://a.test/x.png)\n\nval a = 1"), blocks[2])
        assertEquals(MdTable(listOf("A", "B"), listOf(listOf("中文", "😀"))), blocks[3])
        assertEquals(listOf(MdParagraph("前"), MdRemoteMedia("/workspace/a.png", "图片"), MdParagraph("后")), blocks.drop(4))
    }

    @Test fun `all media forms preserve order without fetching resources`() {
        assertEquals(listOf(MdRemoteMedia("https://a.test/1.png", "alt"), MdParagraph("then"),
            MdRemoteMedia("./two.png", ""), MdParagraph("and"), MdRemoteMedia("https://a.test/3.jpg", "")),
            parseMarkdownBlocks("![alt](https://a.test/1.png) then <img src=\"./two.png\"> and https://a.test/3.jpg"))
        assertEquals(listOf(MdParagraph("<script>alert(1)</script>")), parseMarkdownBlocks("<script>alert(1)</script>"))
    }

    @Test fun `same id and java hash collision must never reuse another body`() {
        assertEquals("Aa".hashCode(), "BB".hashCode())
        resolveMarkdownBlocks("Aa", "message", 1)
        assertEquals(listOf(MdParagraph("BB")), resolveMarkdownBlocks("BB", "message", 1))
    }

    @Test fun `unchanged body hits cache but replacement and short streams remain fresh`() {
        val original = resolveMarkdownBlocks("# heading", "id", 1)
        assertSame(original, resolveMarkdownBlocks("# heading", "id", 1))
        assertEquals(listOf(MdHeading(1, "changed")), resolveMarkdownBlocks("# changed", "id", 1))
        assertEquals(listOf(MdParagraph("Hi")), resolveMarkdownBlocks("Hi", "live"))
        assertEquals(listOf(MdParagraph("Hi there")), resolveMarkdownBlocks("Hi there", "live"))
    }

    @Test fun `oversized input is parsed without retaining a large cache entry`() {
        val body = "x".repeat(1_048_577)
        val first = resolveMarkdownBlocks(body, "huge")
        val second = resolveMarkdownBlocks(body, "huge")
        assertEquals(first, second)
        assertNotSame(first, second)
    }

    @Test fun `newline forms produce identical final blocks`() {
        val lf = "# head\n\n- one\n- two\n\n> quote\n\n---"
        assertEquals(parseMarkdownBlocks(lf), parseMarkdownBlocks(lf.replace("\n", "\r\n")))
        assertEquals(parseMarkdownBlocks(lf), parseMarkdownBlocks(lf.replace("\n", "\r")))
    }
}
