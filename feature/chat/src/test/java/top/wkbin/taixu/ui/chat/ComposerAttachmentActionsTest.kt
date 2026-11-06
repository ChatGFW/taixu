package top.wkbin.taixu.ui.chat

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ComposerAttachmentActionsTest {
    @get:Rule val temporary = TemporaryFolder()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
    private val sessions = MutableStateFlow("a")
    private val drafts = SessionComposerDrafts(sessions, scope, MemoryComposerTextState(), AttachmentDocumentPreparation::delete)
    private val context get() = ApplicationProvider.getApplicationContext<Context>()
    private val notices = mutableListOf<String>()
    @After fun close() { scope.cancel() }
    private fun attachment() = temporary.newFile("import.txt").also { it.writeText("private body") }.let {
        ChatAttachment(name = it.name, isImage = false, sizeBytes = it.length(), uri = Uri.fromFile(it), hostFilePath = it.absolutePath)
    }
    private fun actions(process: suspend (List<Uri>, Boolean, String) -> AttachmentProcessOutcome) =
        ComposerAttachmentActions(context, scope, drafts, notices::add, process, Dispatchers.Unconfined)

    @Test fun `import keeps the session it started in even after switching`() = runBlocking {
        val item = attachment()
        val gate = CompletableDeferred<Unit>()
        var seen: String? = null
        val actions = actions { _, _, sessionId ->
            seen = sessionId
            gate.await()
            AttachmentProcessOutcome(listOf(item), null)
        }

        actions.picked(listOf(item.uri), isImage = false, sessionId = "a")
        sessions.value = "b"
        gate.complete(Unit)
        yield()

        assertEquals("a", seen)
    }

    @Test fun `finished import returns to original draft after switching sessions`() = runBlocking {
        val item = attachment()
        val gate = CompletableDeferred<Unit>()
        val actions = actions { _, _, _ -> gate.await(); AttachmentProcessOutcome(listOf(item), null) }
        actions.picked(listOf(item.uri), false)
        assertTrue(drafts.processing.value)
        sessions.value = "b"; drafts.setText("keep B")
        gate.complete(Unit); yield()
        assertEquals("keep B", drafts.text.value); assertTrue(drafts.attachments.value.isEmpty())
        sessions.value = "a"
        assertEquals(listOf(item), drafts.attachments.value)
        assertTrue(java.io.File(item.hostFilePath!!).exists())
    }
    @Test fun `cancelled delivery releases prepared files instead of orphaning them`() = runBlocking {
        val item = attachment()
        val gate = CompletableDeferred<Unit>()
        actions { _, _, _ -> withContext(NonCancellable) { gate.await() }; AttachmentProcessOutcome(listOf(item), null) }
            .picked(listOf(item.uri), false)
        scope.cancel(); gate.complete(Unit); yield()
        assertFalse(java.io.File(item.hostFilePath!!).exists())
        assertTrue(drafts.attachments.value.isEmpty()); assertFalse(drafts.processing.value)
    }
    @Test fun `deleted draft discards late imported files and ignores old callbacks`() = runBlocking {
        val item = attachment()
        val gate = CompletableDeferred<Unit>()
        actions { _, _, _ -> gate.await(); AttachmentProcessOutcome(listOf(item), "old notice") }.picked(listOf(item.uri), false)
        drafts.discardSession("a"); sessions.value = "b"
        gate.complete(Unit); yield()
        assertFalse(java.io.File(item.hostFilePath!!).exists())
        assertTrue(drafts.attachments.value.isEmpty()); assertTrue(notices.isEmpty())
    }
    @Test fun `unexpected preparation failure leaves existing draft intact and clears processing`() {
        drafts.setText("keep text")
        actions { _, _, _ -> error("provider failed") }.picked(listOf(Uri.parse("content://test/document")), false)
        assertEquals("keep text", drafts.text.value)
        assertFalse(drafts.processing.value); assertEquals(1, notices.size)
    }
}
