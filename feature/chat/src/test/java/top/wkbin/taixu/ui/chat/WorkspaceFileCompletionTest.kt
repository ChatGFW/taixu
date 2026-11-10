package top.wkbin.taixu.ui.chat

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import top.wkbin.taixu.core.common.result.AppError
import top.wkbin.taixu.core.common.result.AppResult
import top.wkbin.taixu.core.common.result.ErrorCode
import top.wkbin.taixu.runtime.environment.ExecutionFileEntry
import top.wkbin.taixu.runtime.environment.ExecutionFiles
import top.wkbin.taixu.runtime.environment.WorkspaceEditOutcome
import top.wkbin.taixu.ui.components.RuntimeIconName

class WorkspaceFileCompletionTest {
    @Test fun `indexes nested files and applies scoped ignore and negation`() = runBlocking {
        val files = FakeFiles(mapOf(
            "/workspace/p" to listOf(".gitignore", "src/", "node_modules/", "secret.log", "keep.log", "../escape"),
            "/workspace/p/src" to listOf(".gitignore", "main.kt", "test.kt", "out/"),
        ), mapOf("/workspace/p/.gitignore" to "*.log\n!keep.log", "/workspace/p/src/.gitignore" to "test.kt\nout/"))
        val found = WorkspaceFileCompletion().complete("one", "/workspace/p", false, files, "").map { it.path }
        assertTrue(found.containsAll(listOf("keep.log", "src/main.kt")))
        assertFalse(found.any { it.contains("secret") || it.contains("test.kt") || it.contains("out") || it.contains("node_modules") || it.contains("escape") })
        assertEquals(listOf("/workspace/p", "/workspace/p/src"), files.listed)
        assertEquals(listOf("/workspace/p/.gitignore", "/workspace/p/src/.gitignore"), files.read)
    }

    @Test fun `cache is scoped to environment and expires`() = runBlocking {
        var now = 0L
        val index = WorkspaceFileCompletion { now }
        val first = FakeFiles(mapOf("/workspace/p" to listOf("first.kt")))
        val second = FakeFiles(mapOf("/workspace/p" to listOf("second.kt")))
        assertEquals("first.kt", index.complete("host1", "/workspace/p", true, first, "").single().path)
        index.complete("host1", "/workspace/p", true, first, "first")
        assertEquals(1, first.listed.size)
        assertEquals("second.kt", index.complete("host2", "/workspace/p", true, second, "").single().path)
        now = 20_001
        index.complete("host2", "/workspace/p", true, second, "")
        assertEquals(2, second.listed.size)
    }

    @Test fun `outside paths fail without touching filesystem and results stay bounded`() = runBlocking {
        val files = FakeFiles(mapOf("/workspace/p" to (1..1000).map { "file$it.kt" }))
        val index = WorkspaceFileCompletion()
        assertTrue(index.complete("x", "/workspace/p", false, files, "../secret").isEmpty())
        assertTrue(index.complete("x", "/workspace/p", false, files, "/etc/passwd").isEmpty())
        assertTrue(files.listed.isEmpty())
        assertEquals(20, index.complete("x", "/workspace/p", false, files, "file").size)
        assertTrue(index.complete("x", "/workspace/p", false, files, "file999").isEmpty())
    }

    @Test fun `explicit directory navigation gets fresh bounded inventory without bypassing ignores`() = runBlocking {
        val files = FakeFiles(mapOf(
            "/workspace/p" to (1..600).map { "root$it.txt" } + listOf("src/", "ignored/", ".gitignore"),
            "/workspace/p/src" to listOf("deep.kt"),
            "/workspace/p/ignored" to listOf("secret.kt"),
        ), mapOf("/workspace/p/.gitignore" to "ignored/"))
        val index = WorkspaceFileCompletion()
        index.complete("x", "/workspace/p", false, files, "")
        assertEquals("src/deep.kt", index.complete("x", "/workspace/p", false, files, "/workspace/p/src/").single().path)
        assertTrue(index.complete("x", "/workspace/p", false, files, "ignored/").isEmpty())
        assertFalse(files.listed.contains("/workspace/p/ignored"))
    }

    @Test fun `failed listing has no local fallback and cancellation propagates`() = runBlocking {
        val files = FakeFiles(emptyMap())
        assertTrue(WorkspaceFileCompletion().complete("remote", "/workspace/p", true, files, "").isEmpty())
        files.cancel = true
        try {
            WorkspaceFileCompletion().complete("remote", "/workspace/p", true, files, "")
            fail("Cancellation must propagate")
        } catch (_: CancellationException) { }
    }

    @Test fun `directory references allow drilling and quoted filenames stay intact`() {
        assertEquals("@/workspace/p/src/", completionReference("/workspace/p/src", true))
        assertEquals("@\"/workspace/p/a b/", completionReference("/workspace/p/a b", true))
        assertEquals("@\"/workspace/p/a b.txt\" ", completionReference("/workspace/p/a b.txt", false))
        assertNull(trailingMentionStart("mail user@example.com"))
        assertNull(trailingMentionStart("@done "))
        assertEquals(4, trailingMentionStart("读一下 @src/"))
        assertEquals(3, trailingMentionStart("读一下@src/"))
        assertEquals(0, trailingMentionStart("@\"/workspace/a b/"))
        assertNull(trailingMentionStart("@\"/workspace/a b\" "))
    }

    @Test fun `stale suggestion cannot overwrite another session workspace or query`() {
        val item = MentionItem("/workspace/p/readme.md", "readme.md", "", "", MentionType.FILE,
            RuntimeIconName.File, "one", "/workspace/p", "read")
        assertEquals("请读 @/workspace/p/readme.md ", insertMention("请读 @read", item, "one", "/workspace/p"))
        assertNull(insertMention("请读 @read", item, "two", "/workspace/p"))
        assertNull(insertMention("请读 @read", item, "one", "/workspace/other"))
        assertNull(insertMention("请读 @changed", item, "one", "/workspace/p"))
    }

    @Test fun `ignore glob supports root paths double star and adversarial wildcard sequences`() {
        assertTrue(WorkspaceGlob("src/**/test?.kt", true).matches("src/test1.kt"))
        assertTrue(WorkspaceGlob("src/**/test?.kt", true).matches("src/a/b/test2.kt"))
        assertFalse(WorkspaceGlob("src/*.kt", true).matches("src/a/main.kt"))
        assertTrue(WorkspaceGlob("*.log", false).matches("src/logs/out.log"))
        assertFalse(WorkspaceGlob("*a".repeat(100) + "z", false).matches("a".repeat(255)))
    }

    private class FakeFiles(val tree: Map<String, List<String>>, val ignores: Map<String, String> = emptyMap()) : ExecutionFiles {
        val listed = mutableListOf<String>()
        val read = mutableListOf<String>()
        var cancel = false
        override suspend fun listEntries(path: String): AppResult<List<ExecutionFileEntry>> {
            if (cancel) throw CancellationException()
            listed += path
            return tree[path]?.let { children -> AppResult.Success(children.map { ExecutionFileEntry(it.trimEnd('/'), it.endsWith('/'), 100, 0) }) }
                ?: AppResult.Failure(AppError(ErrorCode.IO, "unavailable"))
        }
        override suspend fun readTextForEdit(path: String): AppResult<String> { read += path; return AppResult.Success(ignores[path].orEmpty()) }
        override suspend fun read(path: String, offset: Int?, limit: Int?, anchors: Boolean): AppResult<String> = error("Unexpected file content read")
        override suspend fun readRawBytes(path: String): AppResult<ByteArray> = error("Unexpected file read")
        override suspend fun write(path: String, content: String): AppResult<Unit> = error("read only")
        override suspend fun editDetailed(path: String, oldText: String, newText: String): AppResult<WorkspaceEditOutcome> = error("read only")
        override suspend fun previewOrNull(path: String): String? = null
        override suspend fun fileSizeOrNull(path: String): Long? = null
        override suspend fun delete(path: String) = error("read only")
    }
}
