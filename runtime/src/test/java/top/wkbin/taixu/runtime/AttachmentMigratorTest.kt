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
        // 空会话是"无会话上下文"的显式回退：必须落到 shared，绝不能是含 sessions/ 的全局根。
        assertEquals(AttachmentPaths.shared(filesDir).canonicalPath, AttachmentPaths.session(filesDir, "").canonicalPath)
        assertFalse(File(AttachmentPaths.shared(filesDir), "sessions").exists())
    }

    @Test fun mountSourcesNeverExposeTheSessionTree() {
        val sources = listOf(
            AttachmentPaths.session(filesDir, "s1"),
            AttachmentPaths.session(filesDir, null),
            AttachmentPaths.session(filesDir, ""),
        )
        sources.forEach { dir ->
            assertFalse("$dir 不应把 sessions/ 暴露给 guest", File(dir, "sessions").exists())
            assertFalse(
                "$dir 不应是含 sessions/ 的全局根",
                dir.canonicalPath == AttachmentPaths.global(filesDir).canonicalPath,
            )
        }
        assertEquals(
            AttachmentPaths.session(filesDir, null).canonicalPath,
            AttachmentPaths.session(filesDir, "").canonicalPath,
        )
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
        // 多会话引用完成后源文件已删，不应再被清进 legacy。
        assertFalse(File(AttachmentPaths.orphan(filesDir), "b.png").exists())
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

    @Test fun repairsPreviouslyOrphanedBlobAttachmentsAfterTheMarkerWasWritten() = runBlocking {
        val name = "image-" + "a".repeat(180) + ".png"
        writeGlobal(name)
        migrator.migrate(emptyList())
        assertFalse(sessionFile("s1", name).exists())
        val repaired = migrator.migrate(listOf("s1" to "Read /attachments/$name"))
        assertTrue(repaired.skipped)
        assertEquals(name, sessionFile("s1", name).readText())
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
