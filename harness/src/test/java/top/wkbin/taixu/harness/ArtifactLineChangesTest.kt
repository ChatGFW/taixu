package top.wkbin.taixu.harness

import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import top.wkbin.taixu.harness.text.UnifiedDiffGenerator

class ArtifactLineChangesTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test fun lineCountsIgnoreContextAndTrailingNewline() {
        assertEquals(1 to 1, UnifiedDiffGenerator.lineCounts("a\nb\nc\n", "a\nB\nc\n"))
        assertEquals(2 to 0, UnifiedDiffGenerator.lineCounts("", "a\nb\n"))
        assertEquals(0 to 2, UnifiedDiffGenerator.lineCounts("a\nb\n", ""))
        assertEquals(0 to 0, UnifiedDiffGenerator.lineCounts("a\r\nb\r\n", "a\nb\n"))
        assertEquals(1 to 1, UnifiedDiffGenerator.lineCounts("--- header\n", "+++ header\n"))
    }

    @Test fun newAndOverwrittenFilesUseActualBeforeAndAfterText() = runBlocking {
        val root = temporary.newFolder("workspace")
        val backend = WorkspaceToolBackend({ WorkspaceFileAccess(root) })
        suspend fun write(content: String): ArtifactLineChanges? {
            val args = kotlinx.serialization.json.buildJsonObject {
                put("path", kotlinx.serialization.json.JsonPrimitive("Main.kt"))
                put("content", kotlinx.serialization.json.JsonPrimitive(content))
            }
            val result = backend.execute(WorkspaceToolRequest(HarnessTool.WRITE, args, "s", "/workspace"))
            assertTrue(result.success)
            return ToolFileArtifacts.decode(result.metadata[ToolFileArtifacts.METADATA_KEY])!!.files.single().lineChanges
        }
        assertEquals(ArtifactLineChanges(3, 0), write("a\nb\nc\n"))
        assertEquals(ArtifactLineChanges(1, 1), write("a\nB\nc\n"))
        assertEquals(ArtifactLineChanges(0, 0), write("a\nB\nc\n"))
        assertEquals(ArtifactLineChanges(0, 2), write("a\n"))
        File(root, "Main.kt").writeText("\u0000binary")
        assertNull(write("text"))
    }

    @Test fun emptyWritesProduceValidZeroByteArtifacts() = runBlocking {
        val root = temporary.newFolder("empty-workspace")
        val args = kotlinx.serialization.json.buildJsonObject {
            put("path", kotlinx.serialization.json.JsonPrimitive("empty.png"))
            put("content", kotlinx.serialization.json.JsonPrimitive(""))
        }
        val outcome = WorkspaceToolBackend({ WorkspaceFileAccess(root) })
            .execute(WorkspaceToolRequest(HarnessTool.WRITE, args, "s", "/workspace"))
        val batch = ToolFileArtifacts.decode(outcome.metadata[ToolFileArtifacts.METADATA_KEY])!!
        assertTrue(batch.complete)
        assertEquals(0L, batch.files.single().bytes)
        assertEquals(FileArtifactKind.FILE, batch.files.single().kind)
        assertEquals(ArtifactLineChanges(0, 0), batch.files.single().lineChanges)
    }

    @Test fun oldMetadataHasNoInventedCountsAndNegativeCountsAreRejected() {
        val file = ToolFileArtifact("Main.kt", "a".repeat(64), 1, FileArtifactKind.FILE,
            FileArtifactOrigin.AFTER_WRITE, 10)
        assertNull(ToolFileArtifacts.decode(ToolFileArtifacts.encode(listOf(file)))!!.files.single().lineChanges)
        assertNull(ToolFileArtifacts.decode(ToolFileArtifacts.encode(listOf(file.copy(lineChanges = ArtifactLineChanges(-1, 0))))))
    }
}
