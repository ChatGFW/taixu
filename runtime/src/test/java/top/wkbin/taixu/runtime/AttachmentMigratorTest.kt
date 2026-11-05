package top.wkbin.taixu.runtime

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import top.wkbin.taixu.core.common.logging.AppLogger
import top.wkbin.taixu.core.common.logging.SensitiveDataRedactor

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AttachmentMigratorTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val filesDir: File = context.filesDir
    private val migrator = AttachmentMigrator(context, AppLogger(context, SensitiveDataRedactor { it }))

    private fun writeGlobal(name: String, text: String = name) {
        val file = File(AttachmentPaths.global(filesDir), name)
        file.parentFile?.mkdirs()
        file.writeText(text)
    }

    private fun sessionFile(sessionId: String, name: String) = File(AttachmentPaths.session(filesDir, sessionId), name)

    @Test fun sessionSegmentsCannotEscapeTheAttachmentsTree() {
        val root = AttachmentPaths.sessionsRoot(filesDir).canonicalPath
        listOf("../../etc", "a/b", "..", ".", "___", "s-1").forEach { raw ->
            val dir = AttachmentPaths.session(filesDir, raw).canonicalPath
            assertTrue("$raw -> $dir", dir.startsWith(root))
        }
        // 空会话是"无会话上下文"的显式回退，落到共享根而不是任何会话目录。
        assertEquals(AttachmentPaths.global(filesDir).canonicalPath, AttachmentPaths.session(filesDir, "").canonicalPath)
    }

    @Test fun extractsOnlyAttachmentNames() {
        assertEquals(
            setOf("a.png", "b.txt"),
            AttachmentMigrator.referencedAttachmentNames(
                """{"t":"/attachments/a.png and /attachments/b.txt but /attachments/skills/x.md"}""",
            ),
        )
    }

    @Test fun movesSingleOwnerCopiesSharedAndOrphansTheRest() = runBlocking {
        writeGlobal("a.png")
        writeGlobal("b.png")
        writeGlobal("unused.bin")
        writeGlobal("skills/keep.md", "skill")

        val summary = migrator.migrate(
            listOf(
                "s1" to """{"t":"/attachments/a.png /attachments/b.png"}""",
                "s2" to """{"t":"/attachments/b.png"}""",
            ),
        )

        assertFalse(summary.skipped)
        assertTrue(sessionFile("s1", "a.png").isFile)
        assertTrue(sessionFile("s1", "b.png").isFile)
        assertTrue(sessionFile("s2", "b.png").isFile)
        assertFalse(File(AttachmentPaths.global(filesDir), "a.png").exists())
        assertTrue(File(AttachmentPaths.orphan(filesDir), "unused.bin").isFile)
        assertTrue(File(AttachmentPaths.skills(filesDir), "keep.md").isFile)
    }

    @Test fun secondRunIsSkippedByTheMarker() = runBlocking {
        writeGlobal("a.png")
        val first = migrator.migrate(listOf("s1" to """{"t":"/attachments/a.png"}"""))
        val second = migrator.migrate(listOf("s1" to """{"t":"/attachments/a.png"}"""))

        assertFalse(first.skipped)
        assertTrue(second.skipped)
        assertTrue(sessionFile("s1", "a.png").isFile)
    }

    @Test fun relocatesV2SessionRootsIntoTheAttachmentsTree() = runBlocking {
        val legacy = File(AttachmentPaths.base(filesDir), "sessions/s1/attachments")
        legacy.mkdirs()
        File(legacy, "x.png").writeText("x")

        migrator.migrate(emptyList())

        assertTrue(File(AttachmentPaths.session(filesDir, "s1"), "x.png").isFile)
        assertFalse(legacy.exists())
    }

    @Test fun inspectReportsWhereFilesEndedUp() = runBlocking {
        writeGlobal("a.png")
        writeGlobal("skills/keep.md", "skill")
        migrator.migrate(listOf("s1" to """{"t":"/attachments/a.png"}"""))

        val stats = AttachmentMigrator.inspect(filesDir)

        assertTrue(stats.contains("sessions=1"))
        assertTrue(stats.contains("migrated=yes"))
        assertTrue(stats.contains("global=0"))
    }

    @Test fun alreadyMigratedFilesAreNotDuplicated() = runBlocking {
        writeGlobal("a.png")
        sessionFile("s1", "a.png").parentFile?.mkdirs()
        sessionFile("s1", "a.png").writeText("pre-existing")

        migrator.migrate(listOf("s1" to """{"t":"/attachments/a.png"}"""))

        assertEquals("pre-existing", sessionFile("s1", "a.png").readText())
    }
}
