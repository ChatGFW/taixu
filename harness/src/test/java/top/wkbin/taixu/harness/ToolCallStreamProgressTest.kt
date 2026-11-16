package top.wkbin.taixu.harness

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ToolCallStreamProgressTest {

    @Test
    fun `counts newlines in an incomplete content string`() {
        val partial = """{"content":"first\nsecond\nthird"""

        assertEquals(3, countPartialJsonStringLines(partial, "content"))
    }

    @Test
    fun `does not count an escaped backslash followed by n`() {
        val partial = """{"content":"literal\\ntext"}"""

        assertEquals(1, countPartialJsonStringLines(partial, "content"))
    }

    @Test
    fun `counts edit fields independently while json is streaming`() {
        val partial = """{"path":"a.kt","oldText":"a\nb","newText":"a\nb\nc"""

        assertEquals(2, countPartialJsonStringLines(partial, "oldText"))
        assertEquals(3, countPartialJsonStringLines(partial, "newText"))
    }

    @Test
    fun `returns null before field value starts`() {
        assertNull(countPartialJsonStringLines("""{"path":"a.kt","content""", "content"))
    }

    @Test fun `all tool argument growth publishes progress including single line content`() {
        for (tool in listOf("base", "write", "edit", "use_capability")) {
            val accumulator = ToolCallAccumulator(name = tool)
            val updates = mutableListOf<ToolCallStreamProgress>()
            accumulator.arguments.append("{\"command\":\"echo first")
            accumulator.publishProgress(updates::add, force = true)
            accumulator.arguments.append(" more")
            accumulator.publishProgress(updates::add, force = true)
            assertEquals(2, updates.size)
            assertEquals(accumulator.arguments.length, updates.last().argumentChars)
            assertEquals(tool, updates.last().name)
        }
    }

    @Test fun `write publishes first frame before arguments start`() {
        val updates = mutableListOf<ToolCallStreamProgress>()
        ToolCallAccumulator(name = "write").publishProgress(updates::add, force = true)
        assertEquals(listOf("write"), updates.map { it.name })
        assertEquals(null, updates.single().path)
        assertEquals(0, updates.single().addedLines)
    }

    @Test fun `partial path is extracted while streaming and target keeps basename`() {
        val partial = """{"path":"/workspace/DeliveryArtifactActions.kt","content":"first\nse"""
        val progress = ToolCallStreamProgress(
            name = "write", addedLines = 2, deletedLines = 0, path = partialJsonStringValue(partial, "path"),
        )
        assertEquals("/workspace/DeliveryArtifactActions.kt", progress.path)
        assertEquals("DeliveryArtifactActions.kt", progress.displayTarget())
    }

    @Test fun `escaped path separators are unescaped for display`() {
        val partial = """{"path":"C:\\work\\space\\Main.kt","content":"x"""
        assertEquals("C:\\work\\space\\Main.kt", partialJsonStringValue(partial, "path"))
    }

    @Test fun `file progress keeps one stable start timestamp`() {
        val accumulator = ToolCallAccumulator(name = "write")
        val updates = mutableListOf<ToolCallStreamProgress>()
        accumulator.publishProgress(updates::add, force = true)
        accumulator.arguments.append("""{"content":"a\nb""")
        accumulator.publishProgress(updates::add, force = true)
        assertEquals(2, updates.size)
        assertTrue(updates[0].startedAtMs > 0L)
        assertEquals(updates[0].startedAtMs, updates[1].startedAtMs)
    }
}
