package top.wkbin.taixu.core.document

import java.io.File
import java.io.InputStreamReader
import java.nio.charset.CodingErrorAction

/** Replies contain only status. Derived text is always bounded before reading it into the host. */
internal fun readPdfResult(file: File, status: String?, truncated: Boolean, limits: ExtractionLimits): DocumentExtraction = try {
    if (status != "text") DocumentExtraction.Unavailable(
        DocumentExtraction.Reason.entries.firstOrNull { it.name == status } ?: DocumentExtraction.Reason.INVALID)
    else if (file.length() > limits.maxTextChars * 4L) DocumentExtraction.Unavailable(DocumentExtraction.Reason.LIMIT)
    else {
        val text = StringBuilder()
        val decoder = Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
        InputStreamReader(file.inputStream(), decoder).use { reader ->
            val buffer = CharArray(4096)
            while (true) {
                val read = reader.read(buffer)
                if (read < 0) break
                if (text.length + read > limits.maxTextChars) throw ExtractionStop(DocumentExtraction.Reason.LIMIT)
                text.append(buffer, 0, read)
            }
        }
        if (text.isBlank()) DocumentExtraction.Unavailable(DocumentExtraction.Reason.NO_TEXT)
        else DocumentExtraction.Text(text.toString(), truncated)
    }
} catch (e: ExtractionStop) { DocumentExtraction.Unavailable(e.reason) }
catch (_: Exception) { DocumentExtraction.Unavailable(DocumentExtraction.Reason.IO) }
