package top.wkbin.taixu.harness.text

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import top.wkbin.taixu.runtime.environment.HashlineAnchors
import top.wkbin.taixu.runtime.environment.HashlineAnchors.ApplyException
import top.wkbin.taixu.runtime.environment.HashlineAnchors.Kind
import top.wkbin.taixu.runtime.environment.HashlineAnchors.Op

class HashlineAnchorsTest {

    @Test
    fun `hash is stable for normalized line and ignores trailing cr`() {
        val a = HashlineAnchors.lineHash("  return 1")
        val b = HashlineAnchors.lineHash(HashlineAnchors.normalizeLine("  return 1\r"))
        assertEquals(a, b)
        assertEquals(2, a.length)
    }

    @Test
    fun `indent change alters hash`() {
        assertFalse(
            HashlineAnchors.lineHash("  x") == HashlineAnchors.lineHash("    x"),
        )
    }

    @Test
    fun `logical lines drop trailing empty segment after final newline`() {
        assertEquals(listOf("a", "b"), HashlineAnchors.logicalLines("a\nb\n"))
        assertEquals(listOf("a", "b"), HashlineAnchors.logicalLines("a\nb"))
        assertEquals(emptyList<String>(), HashlineAnchors.logicalLines(""))
        assertEquals(listOf(""), HashlineAnchors.logicalLines("\n"))
    }

    @Test
    fun `format page uses absolute line numbers and hash header`() {
        val page = HashlineAnchors.formatPage(
            displayPath = "/workspace/hello.js",
            allLines = listOf("function hello() {", "  return \"world\";", "}"),
            startIndex0 = 1,
            endIndex0Exclusive = 3,
        )
        assertTrue(page.startsWith("[文件 /workspace/hello.js 共 3 行，当前显示第 2-3 行；hash=fnv1a16]"))
        assertTrue(page.contains("\n2:"))
        assertTrue(page.contains("|  return \"world\";"))
        assertFalse(page.contains("内容未完"))
    }

    @Test
    fun `replace single line by anchor`() {
        val content = "function hello() {\n  return \"world\";\n}\n"
        val line2 = HashlineAnchors.anchorOf(2, "  return \"world\";")
        val result = HashlineAnchors.apply(
            content,
            listOf(Op(Kind.REPLACE, start = line2, content = "  return \"hashline\";")),
        )
        assertEquals("function hello() {\n  return \"hashline\";\n}\n", result.updated)
        assertEquals(1, result.operations)
        assertEquals(1, result.linesRemoved)
        assertEquals(1, result.linesAdded)
    }

    @Test
    fun `insert after and delete keep crlf`() {
        val content = "a\r\nb\r\nc\r\n"
        val b = HashlineAnchors.anchorOf(2, "b")
        val inserted = HashlineAnchors.apply(
            content,
            listOf(Op(Kind.INSERT_AFTER, after = b, content = "x")),
        )
        assertEquals("a\r\nb\r\nx\r\nc\r\n", inserted.updated)

        val c = HashlineAnchors.anchorOf(3, "c")
        val deleted = HashlineAnchors.apply(
            content,
            listOf(Op(Kind.DELETE, start = c)),
        )
        assertEquals("a\r\nb\r\n", deleted.updated)
    }

    @Test
    fun `stale anchor refuses write`() {
        val content = "a\nb\n"
        val stale = HashlineAnchors.Anchor(2, "00")
        val error = runCatching {
            HashlineAnchors.apply(content, listOf(Op(Kind.REPLACE, start = stale, content = "z")))
        }.exceptionOrNull()
        assertTrue(error is ApplyException.Stale)
        assertTrue(error!!.message.orEmpty().contains("锚点已过期"))
    }

    @Test
    fun `overlapping replace ops are rejected`() {
        val content = "a\nb\nc\n"
        val a = HashlineAnchors.anchorOf(1, "a")
        val b = HashlineAnchors.anchorOf(2, "b")
        val c = HashlineAnchors.anchorOf(3, "c")
        val error = runCatching {
            HashlineAnchors.apply(
                content,
                listOf(
                    Op(Kind.REPLACE, start = a, end = b, content = "x"),
                    Op(Kind.DELETE, start = b, end = c),
                ),
            )
        }.exceptionOrNull()
        assertTrue(error is ApplyException.Overlap)
    }

    @Test
    fun `pipe in line content is preserved after first separator`() {
        val formatted = HashlineAnchors.formatLine(1, "a|b|c")
        assertEquals("1:${HashlineAnchors.lineHash("a|b|c")}|a|b|c", formatted)
    }

    @Test
    fun `multi op applies from bottom so earlier anchors stay valid`() {
        val content = "a\nb\nc\n"
        val a = HashlineAnchors.anchorOf(1, "a")
        val c = HashlineAnchors.anchorOf(3, "c")
        val result = HashlineAnchors.apply(
            content,
            listOf(
                Op(Kind.REPLACE, start = a, content = "A"),
                Op(Kind.REPLACE, start = c, content = "C"),
            ),
        )
        assertEquals("A\nb\nC\n", result.updated)
    }

    @Test
    fun `insert after 0 colon 00 prepends to non-empty and empty files`() {
        val head = HashlineAnchors.parseAnchor("0:00")
        assertEquals(HashlineAnchors.FILE_HEAD, head)

        val prepended = HashlineAnchors.apply(
            "body\n",
            listOf(Op(Kind.INSERT_AFTER, after = head, content = "hdr")),
        )
        assertEquals("hdr\nbody\n", prepended.updated)

        val intoEmpty = HashlineAnchors.apply(
            "",
            listOf(Op(Kind.INSERT_AFTER, after = head, content = "only")),
        )
        assertEquals("only", intoEmpty.updated)

        // 空原文件：content 以换行结束 → 新文件保留尾换行（不额外空行）
        val intoEmptyFromNlContent = HashlineAnchors.apply(
            "",
            listOf(Op(Kind.INSERT_AFTER, after = head, content = "only\n")),
        )
        assertEquals("only\n", intoEmptyFromNlContent.updated)
    }

    @Test
    fun `replace cannot use file head sentinel`() {
        val error = runCatching {
            HashlineAnchors.apply(
                "a\n",
                listOf(Op(Kind.REPLACE, start = HashlineAnchors.FILE_HEAD, content = "z")),
            )
        }.exceptionOrNull()
        assertTrue(error is ApplyException.Invalid)
    }

    @Test
    fun `same content yields same hash across line numbers`() {
        val h1 = HashlineAnchors.anchorOf(1, "same")
        val h9 = HashlineAnchors.anchorOf(9, "same")
        assertEquals(h1.hash, h9.hash)
        assertEquals(1, h1.line)
        assertEquals(9, h9.line)
    }

    @Test
    fun `replace multi-line range and single line without trailing newline`() {
        val multi = "a\nb\nc\n"
        val a = HashlineAnchors.anchorOf(1, "a")
        val b = HashlineAnchors.anchorOf(2, "b")
        val replaced = HashlineAnchors.apply(
            multi,
            listOf(Op(Kind.REPLACE, start = a, end = b, content = "X\nY")),
        )
        assertEquals("X\nY\nc\n", replaced.updated)
        assertEquals(2, replaced.linesRemoved)
        assertEquals(2, replaced.linesAdded)

        val noNl = "solo"
        val s = HashlineAnchors.anchorOf(1, "solo")
        val edited = HashlineAnchors.apply(
            noNl,
            listOf(Op(Kind.REPLACE, start = s, content = "done")),
        )
        assertEquals("done", edited.updated)
        assertFalse(edited.updated.endsWith("\n"))
    }
}
