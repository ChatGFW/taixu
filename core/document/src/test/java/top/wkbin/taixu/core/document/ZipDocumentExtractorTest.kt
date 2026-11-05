package top.wkbin.taixu.core.document

import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ZipDocumentExtractorTest {
    @get:Rule val temporary = TemporaryFolder()
    private fun zip(name: String, entries: Map<String, String>): File = temporary.newFile(name).also { file ->
        ZipOutputStream(file.outputStream()).use { zip ->
            entries.forEach { (path, text) -> zip.putNextEntry(ZipEntry(path)); zip.write(text.toByteArray()); zip.closeEntry() }
        }
    }
    private fun docx(text: String) = """<w:document xmlns:w="urn:w"><w:body><w:p><w:r><w:t>$text</w:t></w:r></w:p></w:body></w:document>"""
    private fun text(result: DocumentExtraction) = (result as DocumentExtraction.Text).content

    @Test fun `docx keeps paragraphs inline breaks tabs and entities`() {
        val xml = """<w:document xmlns:w="urn:w"><w:body>
            <w:p><w:r><w:t>第一段 &amp; 内容</w:t></w:r></w:p>
            <w:p><w:r><w:t>第二段</w:t><w:br/><w:t>续行</w:t><w:tab/><w:t>末尾</w:t></w:r></w:p>
            </w:body></w:document>"""
        assertEquals("第一段 & 内容\n第二段\n续行\t末尾", text(ZipDocumentExtractor.extract(zip("a.docx", mapOf("word/document.xml" to xml)), DocumentFormat.DOCX)))
    }

    @Test fun `one huge paragraph is truncated inside the text limit`() {
        val source = zip("large.docx", mapOf("word/document.xml" to docx("界".repeat(10_000))))
        val result = ZipDocumentExtractor.extract(source, DocumentFormat.DOCX, ExtractionLimits(maxTextChars = 256)) as DocumentExtraction.Text
        assertTrue(result.truncated)
        assertTrue(result.content.length <= 256)
        assertTrue(result.content.contains("已截断"))
    }

    @Test fun `archive traversal entities duplicates and malformed documents are rejected`() {
        val unsafe = zip("unsafe.docx", mapOf("../escape" to "bad", "word/document.xml" to docx("body")))
        assertEquals(DocumentExtraction.Unavailable(DocumentExtraction.Reason.INVALID), ZipDocumentExtractor.extract(unsafe, DocumentFormat.DOCX))
        val secret = temporary.newFile("secret.txt").also { it.writeText("never-expose-this") }
        val xml = """<!DOCTYPE document [<!ENTITY xxe SYSTEM "${secret.toURI()}">]>""" + docx("&xxe;")
        assertEquals(DocumentExtraction.Unavailable(DocumentExtraction.Reason.INVALID), ZipDocumentExtractor.extract(zip("entity.docx", mapOf("word/document.xml" to xml)), DocumentFormat.DOCX))
        val duplicate = zip("duplicate.docx", mapOf("word/document.xml" to docx("first"), "word/document.xmX" to docx("second")))
        // Same-size replacement creates duplicate ZIP names in both local headers and central directory.
        val bytes = duplicate.readBytes()
        val from = "word/document.xmX".toByteArray(); val to = "word/document.xml".toByteArray()
        for (i in 0..bytes.size - from.size) if (from.indices.all { bytes[i + it] == from[it] }) to.copyInto(bytes, i)
        duplicate.writeBytes(bytes)
        assertEquals(DocumentExtraction.Unavailable(DocumentExtraction.Reason.INVALID), ZipDocumentExtractor.extract(duplicate, DocumentFormat.DOCX))
    }

    @Test fun `compressed expansion entry count and input limits reject without sidecar`() {
        val file = zip("bounded.docx", mapOf("word/document.xml" to docx("a".repeat(10_000))))
        assertEquals(DocumentExtraction.Unavailable(DocumentExtraction.Reason.LIMIT), ZipDocumentExtractor.extract(file, DocumentFormat.DOCX, ExtractionLimits(maxExpandedBytes = 512)))
        assertEquals(DocumentExtraction.Unavailable(DocumentExtraction.Reason.LIMIT), ZipDocumentExtractor.extract(file, DocumentFormat.DOCX, ExtractionLimits(maxEntryBytes = 512)))
        assertEquals(DocumentExtraction.Unavailable(DocumentExtraction.Reason.TOO_LARGE), ZipDocumentExtractor.extract(file, DocumentFormat.DOCX, ExtractionLimits(maxInputBytes = 10)))
        val many = zip("many.docx", mapOf("a" to "a", "word/document.xml" to docx("body")))
        assertEquals(DocumentExtraction.Unavailable(DocumentExtraction.Reason.LIMIT), ZipDocumentExtractor.extract(many, DocumentFormat.DOCX, ExtractionLimits(maxEntries = 1)))
        assertFalse(File(file.parentFile, file.name + ".txt").exists())
    }

    @Test fun `pptx follows presentation relationship order rather than slide filename order`() {
        val file = zip("ordered.pptx", mapOf(
            "ppt/presentation.xml" to """<p:presentation xmlns:p="urn:p" xmlns:r="urn:office/relationships"><p:sldIdLst><p:sldId id="5" r:id="second"/><p:sldId id="6" r:id="first"/></p:sldIdLst></p:presentation>""",
            "ppt/_rels/presentation.xml.rels" to """<Relationships><Relationship Id="first" Type="urn:office/slide" Target="slides/slide1.xml"/><Relationship Id="second" Type="urn:office/slide" Target="slides/slide2.xml"/></Relationships>""",
            "ppt/slides/slide1.xml" to "<slide><p><t>最后看到</t></p></slide>",
            "ppt/slides/slide2.xml" to "<slide><p><t>先看到</t></p></slide>",
        ))
        val body = text(ZipDocumentExtractor.extract(file, DocumentFormat.PPTX))
        assertTrue(body.indexOf("先看到") < body.indexOf("最后看到"))
    }

    private fun epub(href: String = "Text/second.xhtml", body: String = "第二章") = zip("book-${System.nanoTime()}.epub", mapOf(
        "META-INF/container.xml" to """<container><rootfiles><rootfile full-path="OPS/book.opf"/></rootfiles></container>""",
        "OPS/book.opf" to """<package><manifest><item id="a" href="Text/first.xhtml" media-type="application/xhtml+xml"/><item id="b" href="$href" media-type="application/xhtml+xml"/></manifest><spine><itemref idref="b"/><itemref idref="a"/></spine></package>""",
        "OPS/Text/first.xhtml" to "<html><head><title>不属于正文</title></head><body><p>第一章</p></body></html>",
        "OPS/Text/second.xhtml" to "<html><body><p>$body</p><script>不执行也不显示</script><style>不显示样式</style></body></html>",
    ))

    @Test fun `epub follows spine order and excludes scripts styles and head`() {
        val body = text(ZipDocumentExtractor.extract(epub(body = "第二章&nbsp;正文"), DocumentFormat.EPUB))
        assertTrue(body.indexOf("第二章 正文") < body.indexOf("第一章"))
        assertFalse(body.contains("不显示")); assertFalse(body.contains("不执行")); assertFalse(body.contains("不属于正文"))
    }

    @Test fun `epub rejects network and outside-root chapter references`() {
        for (href in listOf("https://example.invalid/chapter", "../../secret.xhtml", "%2fetc/passwd"))
            assertEquals(DocumentExtraction.Unavailable(DocumentExtraction.Reason.INVALID), ZipDocumentExtractor.extract(epub(href), DocumentFormat.EPUB))
    }

    @Test fun `derived text publishes atomically and original bytes stay intact`() {
        val source = zip("sidecar.docx", mapOf("word/document.xml" to docx("正文")))
        val original = source.readBytes()
        val result = ZipDocumentExtractor.extract(source, DocumentFormat.DOCX) as DocumentExtraction.Text
        val sidecar = requireNotNull(DocumentSidecar.write(source, result))
        assertEquals("sidecar.docx.txt", sidecar.name)
        assertEquals("正文", sidecar.readText())
        assertArrayEquals(original, source.readBytes())
        assertFalse(source.parentFile!!.listFiles()!!.any { it.name.startsWith(".document-") })
    }

    @Test fun `empty docx reports no text and elapsed budget fails explicitly`() {
        assertEquals(DocumentExtraction.Unavailable(DocumentExtraction.Reason.NO_TEXT), ZipDocumentExtractor.extract(zip("empty.docx", mapOf("word/document.xml" to docx(""))), DocumentFormat.DOCX))
        var now = 0L
        val budget = ExtractionBudget(ExtractionLimits(timeoutMs = 1)) { now }
        now = 1_000_001
        try { budget.check(); fail("Deadline must be enforced") }
        catch (e: ExtractionStop) { assertEquals(DocumentExtraction.Reason.TIMEOUT, e.reason) }
    }
}
