package top.wkbin.taixu.ui.workspace

import org.junit.Assert.*
import org.junit.Test

class EditorDraftTest {
    @Test fun `edits made during a save remain dirty`() {
        val draft = EditorDraft()
        draft.load("app", "main.kt", "original")
        draft.edit("first edit")
        val saving = draft.snapshot()
        draft.edit("second edit")
        assertTrue(draft.markSaved(saving))
        assertEquals("second edit", draft.text)
        assertTrue(draft.isDirty)
        draft.reset()
        assertEquals("first edit", draft.text)
    }

    @Test fun `empty draft saves an empty file`() {
        val draft = EditorDraft()
        draft.load("app", "main.kt", "original")
        draft.edit("")
        assertEquals("", draft.snapshot().text)
        draft.markSaved(draft.snapshot())
        assertFalse(draft.isDirty)
    }

    @Test fun `save completion from an earlier document is ignored`() {
        val draft = EditorDraft()
        draft.load("app", "main.kt", "original")
        draft.edit("edit")
        val saving = draft.snapshot()
        draft.clear()
        draft.load("app", "main.kt", "reopened")
        assertFalse(draft.markSaved(saving))
        assertEquals("reopened", draft.text)
        assertFalse(draft.isDirty)
    }

    @Test fun `same file can restore the current draft`() {
        val draft = EditorDraft()
        draft.load("app", "main.kt", "original")
        draft.edit("unsaved")
        assertTrue(draft.matches("app", "main.kt"))
        assertFalse(draft.matches("other", "main.kt"))
        assertEquals("unsaved", draft.text)
        assertTrue(draft.isDirty)
    }
}
