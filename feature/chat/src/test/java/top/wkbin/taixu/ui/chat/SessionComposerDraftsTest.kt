package top.wkbin.taixu.ui.chat

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.After
import org.junit.Assert.*
import org.junit.Test

internal class MemoryComposerTextState(var value: ComposerTextSnapshot = ComposerTextSnapshot()) : ComposerDraftTextState {
    override fun restore() = value
    override fun save(snapshot: ComposerTextSnapshot) { value = snapshot }
}

class SessionComposerDraftsTest {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
    private val sessions = MutableStateFlow("a")
    private val persistence = MemoryComposerTextState()
    private val released = mutableListOf<String>()
    private var limited = 0
    private val drafts = SessionComposerDrafts(sessions, scope, persistence, released::add) { limited++ }
    @After fun close() { scope.cancel() }

    @Test fun `switching sessions keeps text and attachment ownership separate`() {
        drafts.setText("draft A"); drafts.addAttachments(listOf("image A"))
        sessions.value = "b"
        assertEquals("", drafts.text.value); assertTrue(drafts.attachments.value.isEmpty())
        drafts.setText("draft B"); drafts.addAttachments(listOf("document B"))
        sessions.value = "a"
        assertEquals("draft A", drafts.text.value); assertEquals(listOf("image A"), drafts.attachments.value)
        sessions.value = "b"
        assertEquals("draft B", drafts.text.value); assertEquals(listOf("document B"), drafts.attachments.value)
        assertTrue(released.isEmpty())
    }
    @Test fun `old UI event updates only the captured session`() {
        drafts.setText("a")
        sessions.value = "b"; drafts.setText("b")
        drafts.setText("late edit A", "a")
        assertEquals("b", drafts.text.value)
        sessions.value = "a"; assertEquals("late edit A", drafts.text.value)
    }
    @Test fun `pending imports return to their originating session and processing is scoped`() {
        val first = drafts.beginImport()
        assertTrue(drafts.processing.value)
        sessions.value = "b"; assertFalse(drafts.processing.value)
        val second = drafts.beginImport(); assertTrue(drafts.processing.value)
        drafts.finishImport(first, listOf("A"))
        assertTrue(drafts.processing.value); assertTrue(drafts.attachments.value.isEmpty())
        drafts.finishImport(second, listOf("B")); assertFalse(drafts.processing.value)
        assertEquals(listOf("B"), drafts.attachments.value)
        sessions.value = "a"; assertEquals(listOf("A"), drafts.attachments.value)
    }
    @Test fun `all concurrent imports must finish before processing clears`() {
        val first = drafts.beginImport(); val second = drafts.beginImport()
        drafts.finishImport(first, listOf("one")); assertTrue(drafts.processing.value)
        drafts.finishImport(second, listOf("two")); assertFalse(drafts.processing.value)
        assertFalse(drafts.finishImport(first, listOf("one")))
        assertTrue(released.isEmpty())
        assertEquals(listOf("one", "two"), drafts.attachments.value)
    }
    @Test fun `sending captured attachments never deletes files or clears another draft`() {
        drafts.setText("a"); drafts.addAttachments(listOf("image"))
        val captured = drafts.snapshot()
        sessions.value = "b"; drafts.setText("keep B"); drafts.addAttachments(listOf("document B"))
        assertEquals(listOf("image"), drafts.detachAttachments(captured.sessionId))
        drafts.setText("", captured.sessionId)
        drafts.removeAttachment("image", captured.sessionId)
        assertTrue(released.isEmpty()); assertEquals("keep B", drafts.text.value)
        assertEquals(listOf("document B"), drafts.attachments.value)
    }
    @Test fun `deletion invalidates imports and late UI changes cannot resurrect a draft`() {
        drafts.setText("private draft"); drafts.addAttachments(listOf("old"))
        val lease = drafts.beginImport()
        drafts.discardSession("a"); sessions.value = "b"
        drafts.setText("must not return", "a")
        drafts.finishImport(lease, listOf("late"))
        drafts.finishImport(lease, listOf("late"))
        drafts.addAttachments(listOf("after deletion"), "a")
        assertEquals(listOf("old", "late", "after deletion"), released)
        assertFalse(persistence.value.texts.containsKey("a"))
        assertEquals("", drafts.text.value); assertTrue(drafts.attachments.value.isEmpty())
    }
    @Test fun `removing an attachment from an inactive session keeps current input intact`() {
        drafts.addAttachments(listOf("old")); sessions.value = "b"; drafts.setText("keep")
        drafts.removeAttachment("old", "a")
        assertEquals(listOf("old"), released); assertEquals("keep", drafts.text.value)
        sessions.value = "a"; assertTrue(drafts.attachments.value.isEmpty())
    }
    @Test fun `initial blank owner transfers text and in flight import to first real session`() {
        val ids = MutableStateFlow("")
        val local = SessionComposerDrafts(ids, scope, MemoryComposerTextState(), released::add)
        val lease = local.beginImport()
        ids.value = "first"
        assertTrue(local.processing.value)
        local.setText("startup typing", "")
        local.finishImport(lease, listOf("startup file"))
        assertEquals("startup typing", local.text.value)
        assertEquals(listOf("startup file"), local.attachments.value)
        ids.value = "second"
        assertEquals("", local.text.value); assertTrue(local.attachments.value.isEmpty())
    }
    @Test fun `restoration restores each session text without serializing attachments`() {
        drafts.setText("A"); drafts.addAttachments(listOf("data:image/base64," + "x".repeat(100_000)))
        sessions.value = "b"; drafts.setText("B")
        scope.cancel()
        val newScope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            val restored = SessionComposerDrafts(sessions, newScope, persistence, released::add)
            assertEquals("B", restored.text.value)
            sessions.value = "a"
            assertEquals("A", restored.text.value); assertTrue(restored.attachments.value.isEmpty())
        } finally { newScope.cancel() }
    }
    @Test fun `oversized snapshots preserve complete in memory drafts and notify once`() {
        val large = "字".repeat(40_000)
        drafts.setText(large)
        assertEquals(large, drafts.text.value); assertFalse(persistence.value.texts.containsKey("a"))
        assertEquals(1, limited)
        drafts.setText(large + "more"); assertEquals(1, limited)
        sessions.value = "b"; drafts.setText("small")
        assertEquals("small", persistence.value.texts["b"])
        sessions.value = "a"; assertEquals(large + "more", drafts.text.value)
    }
    @Test fun `share metadata follows its owner even when two sessions share identical text`() {
        drafts.share("same text")
        assertEquals(ComposerSharedText("a", "same text"), drafts.sharedText.value)
        sessions.value = "b"; assertNull(drafts.sharedText.value)
        drafts.share("same text")
        assertEquals(ComposerSharedText("b", "same text"), drafts.sharedText.value)
        drafts.dismissShare("a")
        assertEquals(ComposerSharedText("b", "same text"), drafts.sharedText.value)
        sessions.value = "a"; assertNull(drafts.sharedText.value)
        assertEquals("same text", drafts.text.value)
    }
    @Test fun `clearing or sending one draft removes only that share action`() {
        drafts.share("A"); sessions.value = "b"; drafts.share("B")
        drafts.setText("", "a")
        assertEquals(ComposerSharedText("b", "B"), drafts.sharedText.value)
        sessions.value = "a"; assertNull(drafts.sharedText.value)
    }

    @Test fun `state snapshot is bounded by recent session count and character budget`() {
        repeat(80) { n -> sessions.value = "s$n"; drafts.setText("text$n") }
        assertEquals(64, persistence.value.texts.size)
        assertTrue(persistence.value.texts.containsKey("s79"))
        assertFalse(persistence.value.texts.containsKey("s0"))
        assertTrue(persistence.value.texts.entries.sumOf { it.key.length + it.value.length } <= 32_768)
        sessions.value = "s0"; assertEquals("text0", drafts.text.value)
    }
}
