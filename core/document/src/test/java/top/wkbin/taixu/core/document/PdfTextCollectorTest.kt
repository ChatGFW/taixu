package top.wkbin.taixu.core.document

import org.junit.Assert.*
import org.junit.Test

class PdfTextCollectorTest {
    @Test fun `page cap limits traversal and marks derived text incomplete`() {
        val visited = mutableListOf<Int>()
        val result = collectPdfText(10, ExtractionLimits(maxPages = 2)) { visited += it; listOf("正文 $it") } as DocumentExtraction.Text
        assertEquals(listOf(0, 1), visited)
        assertTrue(result.truncated)
        assertTrue(result.content.contains("第 1 页"))
        assertTrue(result.content.contains("正文 1"))
        assertFalse(result.content.contains("正文 2"))
    }
    @Test fun `scanned pages remain no text rather than synthetic success headers`() {
        assertEquals(DocumentExtraction.Unavailable(DocumentExtraction.Reason.NO_TEXT), collectPdfText(3, ExtractionLimits()) { emptyList() })
    }
    @Test fun `one very large text block cannot exceed output limit`() {
        val result = collectPdfText(1, ExtractionLimits(maxTextChars = 128)) { listOf("字".repeat(10_000)) } as DocumentExtraction.Text
        assertTrue(result.truncated)
        assertTrue(result.content.length <= 128)
    }
}
