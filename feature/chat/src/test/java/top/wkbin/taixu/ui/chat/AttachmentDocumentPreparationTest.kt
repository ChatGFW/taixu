package top.wkbin.taixu.ui.chat

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AttachmentDocumentPreparationTest {
    @get:Rule val temporary = TemporaryFolder()
    private val context get() = ApplicationProvider.getApplicationContext<Context>()
    private fun docx(): File = temporary.newFile("copy.docx").also { file ->
        ZipOutputStream(file.outputStream()).use { zip ->
            zip.putNextEntry(ZipEntry("word/document.xml"))
            zip.write("<document><p><t>PRIVATE_DOCUMENT_BODY</t></p></document>".toByteArray())
            zip.closeEntry()
        }
    }
    @Test fun `document body stays in sidecar while composer sends only references`() = runBlocking {
        val copy = docx()
        val prepared = AttachmentDocumentPreparation.prepare(context, copy, "报告.docx", null)
        val sidecar = requireNotNull(prepared.file)
        assertEquals("PRIVATE_DOCUMENT_BODY", sidecar.readText())
        val attachment = ChatAttachment(name = "报告.docx", isImage = false, sizeBytes = copy.length(), uri = Uri.fromFile(copy),
            hostFilePath = copy.absolutePath, guestFilePath = "/attachments/copy.docx",
            extractedTextGuestPath = "/attachments/copy.docx.txt", extractedTextHostPath = sidecar.absolutePath)
        val prompt = buildComposerMessage(context, "请读报告", listOf(attachment))
        assertTrue(prompt.contains("/attachments/copy.docx"))
        assertTrue(prompt.contains("/attachments/copy.docx.txt"))
        assertFalse(prompt.contains("PRIVATE_DOCUMENT_BODY"))
        val other = temporary.newFile("unrelated.txt").also { it.writeText("keep") }
        AttachmentDocumentPreparation.delete(attachment)
        assertFalse(copy.exists()); assertFalse(sidecar.exists()); assertTrue(other.exists())
    }
    @Test fun `old platform PDF retains original with a visible and model-readable notice`() = runBlocking {
        val file = temporary.newFile("copy.pdf").also { it.writeText("original PDF") }
        val prepared = AttachmentDocumentPreparation.prepare(context, file, "报告.pdf", "application/pdf")
        assertNull(prepared.file); assertTrue(prepared.notice!!.contains("Android 15"))
        val attachment = ChatAttachment(name = "报告.pdf", isImage = false, sizeBytes = file.length(), uri = Uri.fromFile(file),
            guestFilePath = "/attachments/copy.pdf", documentNotice = prepared.notice)
        val prompt = buildComposerMessage(context, "", listOf(attachment))
        assertTrue(prompt.contains("Android 15")); assertTrue(prompt.contains("/attachments/copy.pdf"))
        assertEquals("original PDF", file.readText())
    }
    @Test fun `corrupt documents preserve originals and unknown file types stay ordinary attachments`() = runBlocking {
        val file = temporary.newFile("broken.docx").also { it.writeText("not a zip") }
        val result = AttachmentDocumentPreparation.prepare(context, file, file.name, null)
        assertNull(result.file); assertNotNull(result.notice); assertTrue(file.exists())
        val ordinary = AttachmentDocumentPreparation.prepare(context, file, "notes.txt", "text/plain")
        assertNull(ordinary.file); assertNull(ordinary.notice)
    }
}
