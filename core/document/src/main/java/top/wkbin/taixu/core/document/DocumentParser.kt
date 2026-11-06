package top.wkbin.taixu.core.document

import android.content.Context
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.runInterruptible

class DocumentParser(context: Context) {
    private val pdf = PdfDocumentClient(context.applicationContext)
    suspend fun extract(file: File, name: String = file.name, mime: String? = null): DocumentExtraction =
        withContext(Dispatchers.IO) {
            when (val format = DocumentFormat.detect(name, mime)) {
                null -> DocumentExtraction.Unavailable(DocumentExtraction.Reason.UNSUPPORTED)
                DocumentFormat.PDF -> pdf.extract(file)
                else -> runInterruptible { ZipDocumentExtractor.extract(file, format) }
            }
        }
}
