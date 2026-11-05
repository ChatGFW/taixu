package top.wkbin.taixu.ui.chat

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import top.wkbin.taixu.harness.PendingMessage
import top.wkbin.taixu.harness.QueuedPrompt
import top.wkbin.taixu.harness.queue.PromptQueue
import top.wkbin.taixu.harness.session.QueuedPromptEditingControl

class QueuedPromptEditorTest {
    private val job = SupervisorJob()
    private val scope = CoroutineScope(job + Dispatchers.Unconfined)
    private val sessions = MutableStateFlow("session")
    private val notices = mutableListOf<String>()
    private val prompt = QueuedPrompt("queue", PromptQueue.NEXT_RUN, PendingMessage("original", listOf("image"), taskId = "task"))
    private val cancelled = mutableListOf<Triple<String, String, String>>()
    private val saved = mutableListOf<List<String>>()
    private var beginGate: CompletableDeferred<Unit>? = null
    private var saveFailure = false
    private var saveAccepted = true
    private val control = object : QueuedPromptEditingControl {
        override suspend fun beginQueuedPromptEdit(sessionId: String, itemId: String, token: String): QueuedPrompt {
            beginGate?.await()
            return prompt.copy(isEditing = true)
        }
        override suspend fun saveQueuedPromptEdit(sessionId: String, itemId: String, token: String, text: String): Boolean {
            if (saveFailure) error("storage failed")
            saved += listOf(sessionId, itemId, token, text)
            return saveAccepted
        }
        override fun cancelQueuedPromptEdit(sessionId: String, itemId: String, token: String) {
            cancelled += Triple(sessionId, itemId, token)
        }
    }
    private val editor = QueuedPromptEditor(scope, control, sessions, notices::add)
    @After fun close() { job.cancel() }

    @Test fun `save updates original id using same lease and retains attachments`() {
        editor.begin(prompt)
        val edit = editor.state.value!!
        editor.update("revised")
        assertEquals(listOf("image"), editor.state.value!!.prompt.message.imageUrls)
        editor.save()
        assertNull(editor.state.value)
        assertEquals(listOf("session", "queue", edit.token, "revised"), saved.single())
        assertTrue(cancelled.isEmpty())
    }
    @Test fun `cancel releases reservation and discards independent draft`() {
        editor.begin(prompt)
        val token = editor.state.value!!.token
        editor.update("draft")
        editor.cancel()
        assertNull(editor.state.value)
        assertEquals(Triple("session", "queue", token), cancelled.single())
        assertTrue(saved.isEmpty())
        assertEquals("original", prompt.message.text)
    }
    @Test fun `session switch closes editor with original session identity`() {
        editor.begin(prompt)
        sessions.value = "other"
        assertNull(editor.state.value)
        assertEquals("session", cancelled.single().first)
        assertTrue(saved.isEmpty())
    }
    @Test fun `closing before begin completes still releases caller generated lease`() = runBlocking {
        beginGate = CompletableDeferred()
        editor.begin(prompt)
        editor.cancel()
        beginGate!!.complete(Unit)
        yield()
        assertNull(editor.state.value)
        assertTrue(cancelled.isNotEmpty())
        assertEquals(1, cancelled.distinct().size)
        assertTrue(saved.isEmpty())
    }
    @Test fun `view model scope cancellation releases in flight reservation`() {
        beginGate = CompletableDeferred()
        editor.begin(prompt)
        job.cancel()
        assertNull(editor.state.value)
        assertTrue(cancelled.isNotEmpty())
        assertEquals(1, cancelled.distinct().size)
    }
    @Test fun `storage failure keeps draft for retry`() {
        editor.begin(prompt)
        editor.update("keep my work")
        saveFailure = true
        editor.save()
        assertEquals("keep my work", editor.state.value!!.text)
        assertFalse(editor.state.value!!.busy)
        assertNotNull(editor.state.value!!.error)
        saveFailure = false
        editor.save()
        assertEquals("keep my work", saved.single().last())
        assertNull(editor.state.value)
    }
    @Test fun `removed message reports conflict without submitting new input`() {
        editor.begin(prompt)
        editor.update("draft")
        saveAccepted = false
        editor.save()
        assertNull(editor.state.value)
        assertEquals(1, cancelled.size)
        assertEquals(1, notices.size)
    }
}
