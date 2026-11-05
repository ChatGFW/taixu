package top.wkbin.taixu.ui.chat

import android.content.Context
import java.io.File
import top.wkbin.taixu.core.document.DocumentExtraction
import top.wkbin.taixu.core.document.DocumentFormat
import top.wkbin.taixu.core.document.DocumentParser
import top.wkbin.taixu.core.document.DocumentSidecar
import top.wkbin.taixu.feature.chat.R

internal data class PreparedAttachmentText(val file: File? = null, val notice: String? = null)
internal object AttachmentDocumentPreparation {
    suspend fun prepare(context: Context, file: File, name: String, mime: String?): PreparedAttachmentText {
        if (DocumentFormat.detect(name, mime) == null) return PreparedAttachmentText()
        return when (val result = DocumentParser(context).extract(file, name, mime)) {
            is DocumentExtraction.Text -> {
                val sidecar = DocumentSidecar.write(file, result)
                val notice = when {
                    sidecar == null -> R.string.chat_document_write_failed
                    result.truncated -> R.string.chat_document_truncated
                    else -> null
                }
                PreparedAttachmentText(sidecar, notice?.let(context::getString))
            }
            is DocumentExtraction.Unavailable -> PreparedAttachmentText(notice = context.getString(when (result.reason) {
                DocumentExtraction.Reason.PDF_REQUIRES_ANDROID_15 -> R.string.chat_document_pdf_requires_15
                DocumentExtraction.Reason.TOO_LARGE -> R.string.chat_document_too_large
                DocumentExtraction.Reason.NO_TEXT -> R.string.chat_document_no_text
                DocumentExtraction.Reason.PROTECTED -> R.string.chat_document_protected
                DocumentExtraction.Reason.LIMIT -> R.string.chat_document_limit
                DocumentExtraction.Reason.TIMEOUT -> R.string.chat_document_timeout
                else -> R.string.chat_document_invalid
            }))
        }
    }

    fun delete(attachment: ChatAttachment) {
        AttachmentHelper.deleteHostFile(attachment.hostFilePath)
        AttachmentHelper.deleteHostFile(attachment.extractedTextHostPath)
    }
}
