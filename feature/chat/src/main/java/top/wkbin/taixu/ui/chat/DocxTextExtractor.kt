package top.wkbin.taixu.ui.chat

import java.io.File
import top.wkbin.taixu.core.document.DocumentExtraction
import top.wkbin.taixu.core.document.DocumentFormat
import top.wkbin.taixu.core.document.DocumentSidecar
import top.wkbin.taixu.core.document.ExtractionLimits
import top.wkbin.taixu.core.document.ZipDocumentExtractor

/** Compatibility facade for existing DOCX callers. New attachment types use DocumentParser. */
object DocxTextExtractor {
    val MAX_TEXT_CHARS: Int = ExtractionLimits().maxTextChars
    fun extract(file: File): String? =
        (ZipDocumentExtractor.extract(file, DocumentFormat.DOCX) as? DocumentExtraction.Text)?.content
    fun writeSidecarText(docx: File): File? =
        (ZipDocumentExtractor.extract(docx, DocumentFormat.DOCX) as? DocumentExtraction.Text)?.let { DocumentSidecar.write(docx, it) }
}
