package top.wkbin.taixu.core.document

import android.content.Context
import android.content.ComponentName
import android.content.pm.ServiceInfo
import android.content.ContextWrapper
import android.content.Intent
import android.content.ServiceConnection
import androidx.test.core.app.ApplicationProvider
import java.io.File
import java.util.concurrent.Executor
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
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
class PdfDocumentClientTest {
    @get:Rule val temporary = TemporaryFolder()
    private val context get() = ApplicationProvider.getApplicationContext<Context>()
    private fun source() = temporary.newFile("${System.nanoTime()}.pdf").also { it.writeText("PDF fixture; never reaches the native worker") }

    @Test fun `old Android retains original and returns explicit platform requirement`() = runBlocking {
        val file = source()
        val original = file.readBytes()
        assertEquals(DocumentExtraction.Unavailable(DocumentExtraction.Reason.PDF_REQUIRES_ANDROID_15), DocumentParser(context).extract(file))
        assertArrayEquals(original, file.readBytes())
    }

    @Test fun `failed bind reports IO and cleans temporary output`() = runBlocking {
        val fake = BindingContext(context, bind = false)
        val before = pdfTemps()
        assertEquals(DocumentExtraction.Unavailable(DocumentExtraction.Reason.IO), PdfDocumentClient(fake, { true }).extract(source()))
        assertEquals(before, pdfTemps())
    }

    @Test fun `timeout and caller cancellation unbind the isolated worker`() = runBlocking {
        val before = pdfTemps()
        val fake = BindingContext(context, bind = true)
        val client = PdfDocumentClient(fake, { true }, ExtractionLimits(timeoutMs = 100))
        assertEquals(DocumentExtraction.Unavailable(DocumentExtraction.Reason.TIMEOUT), client.extract(source()))
        assertEquals(1, fake.unbound)
        val cancel = BindingContext(context, bind = true)
        val request = async { PdfDocumentClient(cancel, { true }).extract(source()) }
        cancel.started.await()
        request.cancelAndJoin()
        assertEquals(1, cancel.unbound)
        assertEquals(before, pdfTemps())
    }

    @Test fun `worker replies cannot exceed host byte or character bounds`() {
        val file = source()
        val limits = ExtractionLimits(maxTextChars = 128)
        file.writeText("a".repeat(129))
        assertEquals(DocumentExtraction.Unavailable(DocumentExtraction.Reason.LIMIT), readPdfResult(file, "text", false, limits))
        file.writeText("正文")
        assertEquals(DocumentExtraction.Text("正文", true), readPdfResult(file, "text", true, limits))
        assertEquals(DocumentExtraction.Unavailable(DocumentExtraction.Reason.PROTECTED), readPdfResult(file, "PROTECTED", false, limits))
        file.delete()
        assertEquals(DocumentExtraction.Unavailable(DocumentExtraction.Reason.IO), readPdfResult(file, "text", false, limits))
    }

    @Test fun `worker declaration is private and uses an isolated process`() {
        val info = context.packageManager.getServiceInfo(ComponentName(context, PdfTextService::class.java), 0)
        assertFalse(info.exported)
        assertTrue(info.flags and ServiceInfo.FLAG_ISOLATED_PROCESS != 0)
        assertTrue(info.processName.endsWith(":document_parser"))
    }

    private fun pdfTemps() = context.cacheDir.listFiles().orEmpty().filter { it.name.startsWith("document-pdf-") }.map { it.name }.toSet()
    private class BindingContext(base: Context, private val bind: Boolean) : ContextWrapper(base) {
        val started = CompletableDeferred<Unit>()
        var unbound = 0
        override fun bindIsolatedService(service: Intent, flags: Int, instanceName: String, executor: Executor, connection: ServiceConnection): Boolean {
            assertTrue(instanceName.matches(Regex("[A-Za-z0-9_.]+")))
            assertTrue(service.component!!.className.endsWith("PdfTextService"))
            started.complete(Unit)
            return bind // Deliberately no callback: simulates a worker that never connects.
        }
        override fun unbindService(connection: ServiceConnection) { unbound++ }
    }
}
