package top.wkbin.taixu.harness

import java.io.File
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import top.wkbin.taixu.core.common.result.AppError
import top.wkbin.taixu.core.common.result.AppResult
import top.wkbin.taixu.core.common.result.ErrorCode
import top.wkbin.taixu.harness.checkpoint.CheckpointStore

class WorkspaceToolBackendTest {
    @get:Rule val temporary = TemporaryFolder()

    private class MemoryOperations : WorkspaceToolOperations {
        var content: String? = "old"
        var failEdit = false
        var large = false
        var beforeWrite: suspend () -> Unit = {}
        val calls = mutableListOf<String>()
        override suspend fun delete(path: String): Boolean { content = null; return true }
        override suspend fun read(path: String, offset: Int?, limit: Int?, anchors: Boolean): AppResult<String> {
            calls += "read:$path:$offset:$limit:$anchors"
            return AppResult.Success(content.orEmpty())
        }
        override suspend fun readTextForEdit(path: String): AppResult<String> {
            calls += "readTextForEdit:$path"
            return AppResult.Success(content.orEmpty())
        }
        override suspend fun readRawBytes(path: String): AppResult<ByteArray> {
            calls += "raw:$path"
            return AppResult.Success(byteArrayOf(1, 2, 3))
        }
        override suspend fun write(path: String, content: String): AppResult<Unit> {
            calls += "write:$path"
            beforeWrite()
            this.content = content
            return AppResult.Success(Unit)
        }
        override suspend fun editDetailed(path: String, oldText: String, newText: String): AppResult<WorkspaceEditOutcome> {
            calls += "edit:$path"
            if (failEdit) return AppResult.Failure(AppError(ErrorCode.IO, "not found"))
            content = content?.replace(oldText, newText)
            return AppResult.Success(WorkspaceEditOutcome("memory-match", 1, "diff-body"))
        }
        override suspend fun previewOrNull(path: String): String? { calls += "preview:$path"; return content }
        override suspend fun fileSizeOrNull(path: String): Long? {
            calls += "size:$path"
            return if (large) CheckpointStore.SNAPSHOT_MAX_BYTES + 1L else content?.length?.toLong()
        }
    }

    private fun request(tool: HarnessTool, args: String) = WorkspaceToolRequest(
        tool, Json.parseToJsonElement(args) as JsonObject, "session", "project",
    )

    @Test fun `custom operations receive workspace and read pagination`() = runBlocking {
        val memory = MemoryOperations()
        var workspace = ""
        val backend = WorkspaceToolBackend({ workspace = it; memory })
        val result = backend.execute(request(HarnessTool.READ, """{"path":"a.txt","offset":3,"limit":5}"""))
        assertTrue(result.success)
        assertEquals("project", workspace)
        assertEquals(listOf("read:a.txt:3:5:true"), memory.calls)
    }

    @Test fun `write keeps first pre image and updates final post image across repeated writes`() = runBlocking {
        val memory = MemoryOperations()
        val store = CheckpointStore().apply { beginTurn("session", "change") }
        val backend = WorkspaceToolBackend({ memory }, WorkspaceMutationSnapshots(store))
        backend.execute(request(HarnessTool.WRITE, """{"path":"a.txt","content":"new"}"""))
        backend.execute(request(HarnessTool.WRITE, """{"path":"a.txt","content":"latest"}"""))
        assertEquals("latest", store.latestAfterImage("session", "a.txt"))
        store.endTurn("session")
        assertEquals("old", store.planCodeRewind("session", 0).single().content)
        assertEquals(listOf("size:a.txt", "preview:a.txt", "write:a.txt", "size:a.txt"), memory.calls.take(4))
    }

    @Test fun `edit returns strategy and diff metadata and captures actual final content`() = runBlocking {
        val memory = MemoryOperations().apply { content = "prefix old suffix" }
        val store = CheckpointStore().apply { beginTurn("session", "edit") }
        val result = WorkspaceToolBackend(
            { memory },
            WorkspaceMutationSnapshots(store),
            hashlinePolicy = { top.wkbin.taixu.harness.text.HashlinePolicy(legacyEditEnabled = true) },
        ).execute(request(HarnessTool.EDIT, """{"path":"a.txt","oldText":"old","newText":"new"}"""))
        assertTrue(result.success)
        assertTrue(result.output.contains("memory-match"))
        assertFalse(result.output.contains("diff-body"))
        assertEquals("diff-body", result.metadata["diff"])
        assertEquals("prefix new suffix", store.latestAfterImage("session", "a.txt"))
    }

    @Test fun `failed edit does not create post image or claim a mutation`() = runBlocking {
        val memory = MemoryOperations().apply { failEdit = true }
        val store = CheckpointStore().apply { beginTurn("session", "edit") }
        val result = WorkspaceToolBackend(
            { memory },
            WorkspaceMutationSnapshots(store),
            hashlinePolicy = { top.wkbin.taixu.harness.text.HashlinePolicy(legacyEditEnabled = true) },
        ).execute(request(HarnessTool.EDIT, """{"path":"a.txt","oldText":"old","newText":"new"}"""))
        assertFalse(result.success)
        assertEquals("old", memory.content)
        assertNull(store.latestAfterImage("session", "a.txt"))
        assertTrue(result.output.contains("请立即调用 read"))
    }

    @Test fun `hashline edit applies ops and captures checkpoint`() = runBlocking {
        val memory = MemoryOperations().apply { content = "function hello() {\n  return \"world\";\n}\n" }
        val store = CheckpointStore().apply { beginTurn("session", "edit") }
        val line2 = top.wkbin.taixu.runtime.environment.HashlineAnchors.anchorOf(2, "  return \"world\";")
        val result = WorkspaceToolBackend({ memory }, WorkspaceMutationSnapshots(store)).execute(
            request(
                HarnessTool.EDIT,
                """{"path":"hello.js","ops":[{"op":"replace","start":"$line2","content":"  return \"hashline\";"}]}""",
            ),
        )
        assertTrue(result.success)
        assertTrue(result.output.contains("匹配策略：hashline"))
        assertTrue(result.output.contains("操作 1"))
        assertTrue(result.output.contains("替换区间"))
        assertTrue(result.output.contains("DIFF_STAT:"))
        assertEquals("function hello() {\n  return \"hashline\";\n}\n", memory.content)
        assertEquals("hashline", result.metadata["strategy"])
        assertEquals("1", result.metadata["hashline_ops"])
        assertNotNull(result.metadata["diff"])
        assertEquals("function hello() {\n  return \"hashline\";\n}\n", store.latestAfterImage("session", "hello.js"))
    }

    @Test fun `hashline insert at file head into empty file preserves trailing newline`() = runBlocking {
        val memory = MemoryOperations().apply { content = "" }
        val store = CheckpointStore().apply { beginTurn("session", "edit") }
        val result = WorkspaceToolBackend({ memory }, WorkspaceMutationSnapshots(store)).execute(
            request(
                HarnessTool.EDIT,
                """{"path":"new.js","ops":[{"op":"insert_after","after":"0:00","content":"console.log(1);\n"}]}""",
            ),
        )
        assertTrue(result.success)
        assertEquals("console.log(1);\n", memory.content)
        assertEquals("hashline", result.metadata["strategy"])
        assertEquals("console.log(1);\n", store.latestAfterImage("session", "new.js"))
    }

    @Test fun `stale hashline edit does not write or snapshot after`() = runBlocking {
        val memory = MemoryOperations().apply { content = "a\nb\n" }
        val store = CheckpointStore().apply { beginTurn("session", "edit") }
        val result = WorkspaceToolBackend({ memory }, WorkspaceMutationSnapshots(store)).execute(
            request(HarnessTool.EDIT, """{"path":"a.txt","ops":[{"op":"replace","start":"2:00","content":"z"}]}"""),
        )
        assertFalse(result.success)
        assertEquals("a\nb\n", memory.content)
        assertNull(store.latestAfterImage("session", "a.txt"))
        assertEquals("HASHLINE_STALE", result.metadata["hashline_error"])
        assertTrue(result.output.contains("锚点已过期"))
        assertTrue(result.output.contains("read(\"a.txt\", offset=1, limit=80)"))
    }

    @Test fun `mixed hashline and legacy edit args are rejected`() = runBlocking {
        val memory = MemoryOperations()
        val result = WorkspaceToolBackend({ memory }).execute(
            request(
                HarnessTool.EDIT,
                """{"path":"a.txt","oldText":"a","newText":"b","ops":[{"op":"delete","start":"1:00"}]}""",
            ),
        )
        assertFalse(result.success)
        assertEquals("HASHLINE_MIXED_MODE", result.metadata["hashline_error"])
        assertTrue(memory.calls.isEmpty())
    }

    @Test fun `empty ops array returns HASHLINE_EMPTY_OPS`() = runBlocking {
        val memory = MemoryOperations().apply { content = "a\n" }
        val result = WorkspaceToolBackend({ memory }).execute(
            request(HarnessTool.EDIT, """{"path":"a.txt","ops":[]}"""),
        )
        assertFalse(result.success)
        assertEquals("HASHLINE_EMPTY_OPS", result.metadata["hashline_error"])
        assertTrue(memory.calls.isEmpty())
    }

    @Test fun `hashline policy can disable ops or legacy and records stats`() = runBlocking {
        top.wkbin.taixu.harness.text.HashlineEditStats.resetForTests()
        val memory = MemoryOperations().apply { content = "a\n" }
        val opsOff = WorkspaceToolBackend(
            { memory },
            hashlinePolicy = {
                top.wkbin.taixu.harness.text.HashlinePolicy(editOpsEnabled = false)
            },
        ).execute(request(HarnessTool.EDIT, """{"path":"a.txt","ops":[{"op":"delete","start":"1:00"}]}"""))
        assertFalse(opsOff.success)
        assertEquals("HASHLINE_OPS_DISABLED", opsOff.metadata["hashline_error"])
        assertEquals(1, top.wkbin.taixu.harness.text.HashlineEditStats.snapshot()["ops_disabled"])

        val legacyOff = WorkspaceToolBackend(
            { memory },
            hashlinePolicy = {
                top.wkbin.taixu.harness.text.HashlinePolicy(legacyEditEnabled = false)
            },
        ).execute(request(HarnessTool.EDIT, """{"path":"a.txt","oldText":"a","newText":"b"}"""))
        assertFalse(legacyOff.success)
        assertEquals("HASHLINE_LEGACY_DISABLED", legacyOff.metadata["hashline_error"])
        assertEquals(1, top.wkbin.taixu.harness.text.HashlineEditStats.snapshot()["legacy_disabled"])
        assertEquals("a\n", memory.content)
    }

    @Test fun `default policy rejects legacy oldText without enabling`() = runBlocking {
        top.wkbin.taixu.harness.text.HashlineEditStats.resetForTests()
        val memory = MemoryOperations().apply { content = "a\n" }
        val result = WorkspaceToolBackend({ memory }).execute(
            request(HarnessTool.EDIT, """{"path":"a.txt","oldText":"a","newText":"b"}"""),
        )
        assertFalse(result.success)
        assertEquals("HASHLINE_LEGACY_DISABLED", result.metadata["hashline_error"])
        assertEquals("a\n", memory.content)
    }

    @Test fun `hashline insert after file head writes empty file with trailing newline`() = runBlocking {
        val memory = MemoryOperations().apply { content = "" }
        val store = CheckpointStore().apply { beginTurn("session", "edit") }
        val result = WorkspaceToolBackend({ memory }, WorkspaceMutationSnapshots(store)).execute(
            request(
                HarnessTool.EDIT,
                """{"path":"new.txt","ops":[{"op":"insert_after","after":"0:00","content":"hello\n"}]}""",
            ),
        )
        assertTrue(result.success)
        assertEquals("hello\n", memory.content)
        assertEquals("hashline", result.metadata["strategy"])
        assertEquals("hello\n", store.latestAfterImage("session", "new.txt"))
    }

    @Test fun `new file records absent pre image and populated post image`() = runBlocking {
        val memory = MemoryOperations().apply { content = null }
        val store = CheckpointStore().apply { beginTurn("session", "create") }
        WorkspaceToolBackend({ memory }, WorkspaceMutationSnapshots(store))
            .execute(request(HarnessTool.WRITE, """{"path":"new.txt","content":"created"}"""))
        assertEquals("created", store.latestAfterImage("session", "new.txt"))
        store.endTurn("session")
        assertNull(store.planCodeRewind("session", 0).single().content)
    }

    @Test fun `oversized snapshot is skipped without pretending file was absent`() = runBlocking {
        val memory = MemoryOperations().apply { large = true }
        val store = CheckpointStore().apply { beginTurn("session", "change") }
        WorkspaceToolBackend({ memory }, WorkspaceMutationSnapshots(store))
            .execute(request(HarnessTool.WRITE, """{"path":"a.txt","content":"new"}"""))
        store.endTurn("session")
        assertTrue(store.planCodeRewind("session", 0).isEmpty())
        assertFalse(memory.calls.any { it.startsWith("preview:") })
    }

    @Test fun `image is read as bytes while svg remains text`() = runBlocking {
        val memory = MemoryOperations()
        val backend = WorkspaceToolBackend({ memory }, imagePayload = { it })
        val image = backend.execute(request(HarnessTool.READ, """{"path":"photo.png"}"""))
        assertEquals("data:image/png;base64,AQID", image.metadata["image_payload"])
        backend.execute(request(HarnessTool.READ, """{"path":"diagram.svg"}"""))
        assertEquals(listOf("raw:photo.png", "read:diagram.svg:null:null:true"), memory.calls)
    }

    @Test fun `invalid arguments cannot start snapshots or mutations`() = runBlocking {
        val memory = MemoryOperations()
        val backend = WorkspaceToolBackend({ memory })
        assertTrue(runCatching { backend.execute(request(HarnessTool.WRITE, """{"path":"a.txt"}""")) }.exceptionOrNull() is IllegalArgumentException)
        assertTrue(memory.calls.isEmpty())
    }

    @Test fun `cancellation after backend write cannot capture post image or return success`() = runBlocking {
        val memory = MemoryOperations()
        val started = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        memory.beforeWrite = { withContext(NonCancellable) { started.complete(Unit); release.await() } }
        val store = CheckpointStore().apply { beginTurn("session", "change") }
        val backend = WorkspaceToolBackend({ memory }, WorkspaceMutationSnapshots(store))
        var returned = false
        val job = launch { backend.execute(request(HarnessTool.WRITE, """{"path":"a.txt","content":"new"}""")); returned = true }
        started.await()
        job.cancel()
        release.complete(Unit)
        job.join()
        assertFalse(returned)
        assertNull(store.latestAfterImage("session", "a.txt"))
        assertEquals(1, memory.calls.count { it.startsWith("size:") })
    }

    @Test fun `local operations preserve traversal protection through the new backend`() = runBlocking {
        val workspace = temporary.newFolder("workspace")
        val outside = File(workspace.parentFile, "outside.txt").apply { writeText("keep") }
        val backend = WorkspaceToolBackend({ WorkspaceFileAccess(workspace) })
        val result = backend.execute(request(HarnessTool.WRITE, """{"path":"../outside.txt","content":"replace"}"""))
        assertFalse(result.success)
        assertEquals("keep", outside.readText())
    }
}
