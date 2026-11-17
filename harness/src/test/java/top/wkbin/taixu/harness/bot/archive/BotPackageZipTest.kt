package top.wkbin.taixu.harness.bot.archive

import java.io.File
import java.nio.file.Files
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BotPackageZipTest {
    @Test fun `history is excluded when not requested`() {
        withMemory { memory ->
            File(memory, "persona.md").writeText("hello")
            File(memory, ".git/objects/ab").apply { parentFile?.mkdirs() }.writeText("old-secret")

            val names = zipNames(memory, includeHistory = false)
            assertTrue(names.contains("memory/persona.md"))
            assertFalse(names.any { it.contains(".git") })
        }
    }

    @Test fun `history stays when requested and lock files are skipped`() {
        withMemory { memory ->
            File(memory, "persona.md").writeText("hello")
            File(memory, ".git/objects/ab").apply { parentFile?.mkdirs() }.writeText("old-secret")
            File(memory, ".git/index.lock").apply { parentFile?.mkdirs() }.writeText("lock")

            val names = zipNames(memory, includeHistory = true)
            assertTrue(names.contains("memory/.git/objects/ab"))
            assertFalse(names.any { it.endsWith("index.lock") })
        }
    }

    private fun withMemory(block: (File) -> Unit) {
        val memory = Files.createTempDirectory("bot-memory").toFile()
        try {
            block(memory)
        } finally {
            memory.deleteRecursively()
        }
    }

    private fun zipNames(memory: File, includeHistory: Boolean): Set<String> {
        val zipFile = Files.createTempFile("bot-package", ".zip").toFile()
        try {
            ZipOutputStream(zipFile.outputStream()).use { writeMemoryToZip(it, memory, includeHistory) }
            ZipFile(zipFile).use { zip ->
                return zip.entries().asSequence().map { it.name }.toSet()
            }
        } finally {
            zipFile.delete()
        }
    }
}
