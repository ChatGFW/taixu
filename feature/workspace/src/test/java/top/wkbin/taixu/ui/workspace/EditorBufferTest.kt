package top.wkbin.taixu.ui.workspace

import io.github.rosemoe.sora.text.Content
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EditorBufferTest {
    @Test
    fun fileBuffersRetainIndependentUndoAndLineEndings() {
        val original = "第一行\r\nsecond\n"
        val first = Content(original).apply { setUndoEnabled(true) }
        val second = Content("other file\n").apply { setUndoEnabled(true) }

        first.replace(0, 0, 0, 3, "已编辑")
        second.insert(0, 0, "another ")
        assertEquals("已编辑\r\nsecond\n", first.toString())
        assertEquals("another other file\n", second.toString())

        assertTrue(first.canUndo())
        first.undo()
        assertEquals(original, first.toString())
        assertEquals("another other file\n", second.toString())
    }
}
