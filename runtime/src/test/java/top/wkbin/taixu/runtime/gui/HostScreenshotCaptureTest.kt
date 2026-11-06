package top.wkbin.taixu.runtime.gui

import java.nio.file.Files
import java.util.Base64
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import top.wkbin.taixu.runtime.privilege.ShellExecResult

class HostScreenshotCaptureTest {
    private val png = byteArrayOf(0x89.toByte(), 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a) + ByteArray(90_000) { 42 }

    @Test fun `shell capture streams bounded chunks into private app storage`() = runBlocking {
        val directory = Files.createTempDirectory("screenshot").toFile()
        val commands = mutableListOf<String>()
        val target = directory.resolve("screen.png")
        try {
            val result = captureHostScreenshot(target.absolutePath) { command ->
                commands += command
                val output = when {
                    "screencap" in command -> png.size.toString()
                    "dd if=" in command -> {
                        val skip = Regex("skip=(\\d+)").find(command)!!.groupValues[1].toInt()
                        Base64.getEncoder().encodeToString(png.copyOfRange(skip * 32768, minOf((skip + 1) * 32768, png.size)))
                    }
                    else -> ""
                }
                ShellExecResult(true, 0, output, "")
            }
            assertTrue(result.isSuccess)
            assertArrayEquals(png, target.readBytes())
            assertFalse(commands.any { target.absolutePath in it })
            assertTrue(commands.last().contains("rm -f"))
        } finally { directory.deleteRecursively() }
    }

    @Test fun `truncated transfer preserves existing screenshot and removes temporary files`() = runBlocking {
        val directory = Files.createTempDirectory("screenshot-failure").toFile()
        val target = directory.resolve("screen.png").apply { writeText("previous") }
        val commands = mutableListOf<String>()
        try {
            val result = captureHostScreenshot(target.absolutePath) { command ->
                commands += command
                ShellExecResult(true, 0, if ("screencap" in command) "90008" else "", "")
            }
            assertTrue(result.isFailure)
            assertEquals("previous", target.readText())
            assertEquals(listOf("screen.png"), directory.listFiles()!!.map { it.name })
            assertTrue(commands.last().contains("rm -f"))
        } finally { directory.deleteRecursively() }
    }
}
