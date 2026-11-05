package top.wkbin.taixu.ui.chat

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.wkbin.taixu.feature.chat.R

internal class ComposerAttachmentActions(
    private val context: Context,
    private val scope: CoroutineScope,
    private val drafts: SessionComposerDrafts<ChatAttachment>,
    private val notice: (String) -> Unit,
    private val importFiles: suspend (List<Uri>, Boolean) -> AttachmentProcessOutcome = { uris, image ->
        AttachmentHelper.processPicked(context, uris, image, drafts.snapshot().sessionId)
    },
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    fun picked(uris: List<Uri>, isImage: Boolean, sessionId: String = drafts.snapshot().sessionId) {
        if (uris.isEmpty() || !scope.isActive) return
        val lease = drafts.beginImport(sessionId)
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            var prepared = emptyList<ChatAttachment>()
            try {
                val outcome = withContext(ioDispatcher) { importFiles(uris, isImage).also { prepared = it.items } }
                currentCoroutineContext().ensureActive()
                prepared = emptyList()
                val accepted = drafts.finishImport(lease, outcome.items)
                if (accepted) outcome.notice?.let(notice)
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { notice(context.getString(R.string.chat_attachment_process_failed, uris.size)) }
            finally {
                prepared.forEach(AttachmentDocumentPreparation::delete)
                drafts.finishImport(lease, emptyList())
            }
        }
    }
    fun pasted(text: String, sessionId: String = drafts.snapshot().sessionId) {
        val file = AttachmentHelper.writePastedText(context, text, sessionId) ?: return
        drafts.addAttachments(listOf(ChatAttachment(name = file.name, isImage = false, sizeBytes = file.length(),
            uri = Uri.fromFile(file), hostFilePath = file.absolutePath, guestFilePath = "/attachments/${file.name}")), sessionId)
    }
}
