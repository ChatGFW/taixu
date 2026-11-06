package top.wkbin.taixu.core.document

import java.io.File
import java.util.Locale

/** Text is derived, untrusted content. Originals are always retained by the caller. */
sealed interface DocumentExtraction {
    data class Text(val content: String, val truncated: Boolean = false) : DocumentExtraction
    data class Unavailable(val reason: Reason) : DocumentExtraction
    enum class Reason { UNSUPPORTED, PDF_REQUIRES_ANDROID_15, TOO_LARGE, INVALID, NO_TEXT, PROTECTED, LIMIT, TIMEOUT, IO }
}

enum class DocumentFormat {
    DOCX, PPTX, EPUB, PDF;
    companion object {
        fun detect(name: String, mime: String? = null): DocumentFormat? =
            entries.firstOrNull { it.name.lowercase(Locale.ROOT) == name.substringAfterLast('.', "").lowercase(Locale.ROOT) }
                ?: when (mime?.substringBefore(';')?.lowercase(Locale.ROOT)) {
                    "application/pdf" -> PDF
                    "application/epub+zip" -> EPUB
                    "application/vnd.openxmlformats-officedocument.wordprocessingml.document" -> DOCX
                    "application/vnd.openxmlformats-officedocument.presentationml.presentation" -> PPTX
                    else -> null
                }
    }
}

data class ExtractionLimits(
    val maxInputBytes: Long = 32L * 1024 * 1024,
    val maxExpandedBytes: Long = 64L * 1024 * 1024,
    val maxEntryBytes: Long = 8L * 1024 * 1024,
    val maxEntries: Int = 2048,
    val maxParts: Int = 256,
    val maxTextChars: Int = 256 * 1024,
    val maxPages: Int = 200,
    val timeoutMs: Long = 10_000,
) {
    init {
        require(maxInputBytes in 1..32L * 1024 * 1024 && maxExpandedBytes in 1..64L * 1024 * 1024)
        require(maxEntryBytes in 1..8L * 1024 * 1024 && maxEntries in 1..2048 && maxParts in 1..256)
        require(maxTextChars in 128..256 * 1024 && maxPages in 1..200 && timeoutMs in 1..10_000)
    }
}

internal class ExtractionStop(val reason: DocumentExtraction.Reason) : RuntimeException()
internal class TextFull : RuntimeException()

internal class ExtractionBudget(val limits: ExtractionLimits, private val clock: () -> Long = System::nanoTime) {
    private val started = clock()
    private var expanded = 0L
    fun check() {
        if (Thread.currentThread().isInterrupted || clock() - started >= limits.timeoutMs * 1_000_000)
            throw ExtractionStop(DocumentExtraction.Reason.TIMEOUT)
    }
    fun bytes(count: Int) {
        check()
        expanded += count
        if (expanded > limits.maxExpandedBytes) throw ExtractionStop(DocumentExtraction.Reason.LIMIT)
    }
}

internal class ExtractedText(private val limit: Int) {
    private val value = StringBuilder()
    var truncated = false
    private var hasContent = false
    fun append(text: String, semantic: Boolean = true) {
        val remaining = limit - value.length
        if (semantic && (0 until minOf(text.length, remaining)).any { !text[it].isWhitespace() }) hasContent = true
        if (text.length <= remaining) value.append(text)
        else { value.append(text, 0, remaining); truncated = true; throw TextFull() }
    }
    fun line() { if (value.isNotEmpty() && value.last() != '\n') append("\n", semantic = false) }
    fun finish(): DocumentExtraction {
        val text = value.toString().trim()
        if (!hasContent || text.isEmpty()) return DocumentExtraction.Unavailable(DocumentExtraction.Reason.NO_TEXT)
        val marker = "\n[正文提取已截断，请结合原文件分段读取]"
        val bounded = if (truncated) text.take((limit - marker.length).coerceAtLeast(0)) + marker else text
        return DocumentExtraction.Text(bounded, truncated)
    }
}

internal fun validateInput(file: File, limits: ExtractionLimits) {
    if (!file.isFile) throw ExtractionStop(DocumentExtraction.Reason.IO)
    if (file.length() > limits.maxInputBytes) throw ExtractionStop(DocumentExtraction.Reason.TOO_LARGE)
}
