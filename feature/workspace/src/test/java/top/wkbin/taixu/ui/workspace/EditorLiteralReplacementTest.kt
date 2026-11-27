package top.wkbin.taixu.ui.workspace

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class EditorLiteralReplacementTest {
    @Test
    fun replacesLiteralMatchesWithoutOverlappingOrParsingBackreferences() {
        assertEquals(
            "X$1\\X$1\\a",
            replaceEditorMatches("aaaaa", "aa", "X$1\\", matchCase = true, maxChars = 20),
        )
        assertEquals("done! @test", replaceEditorMatches("(?). @test", "(?).", "done!", matchCase = true, maxChars = 32))
    }

    @Test
    fun caseInsensitiveReplacementPreservesLineEndingsAndUnchangedText() {
        val document = "TAI\r\nTaI\rtai\n去年"
        assertEquals("墟\r\n墟\r墟\n去年", replaceEditorMatches(document, "tai", "墟", matchCase = false, maxChars = 30))
        assertEquals("TAI\r\nTaI\r墟\n去年", replaceEditorMatches(document, "tai", "墟", matchCase = true, maxChars = 30))
    }

    @Test
    fun supportsDeletionAndStopsBeforeExceedingTheSaveLimit() {
        assertEquals("bc", replaceEditorMatches("abbabc", "ab", "", matchCase = true, maxChars = 4))
        assertEquals("xxb", replaceEditorMatches("aab", "a", "x", matchCase = true, maxChars = 3))
        assertNull(replaceEditorMatches("aab", "a", "xx", matchCase = true, maxChars = 4))
        assertNull(replaceEditorMatches("abcde", "z", "x", matchCase = true, maxChars = 4))
    }
}
