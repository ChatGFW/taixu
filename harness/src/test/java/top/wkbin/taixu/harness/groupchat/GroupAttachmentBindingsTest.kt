package top.wkbin.taixu.harness.groupchat

import java.nio.file.Files
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class GroupAttachmentBindingsTest {
    @Test fun `each recipient receives attachments referenced by the actual prompt`() = runBlocking {
        val root = Files.createTempDirectory("group-attachments").toFile()
        try {
            val global = root.resolve("global")
            val path = "bot-input/12345678-1234-1234-1234-123456789abc/report.txt"
            global.resolve(path).apply { parentFile!!.mkdirs(); writeText("report") }
            for (id in listOf("bot-one", "bot-two")) {
                val session = root.resolve(id)
                bindGroupAttachments(global, session, "Read /attachments/$path")
                bindGroupAttachments(global, session, "Read /attachments/$path")
                assertEquals("report", session.resolve(path).readText())
                assertFalse(session.resolve("sessions").exists())
            }
            root.resolve("bot-one/$path").writeText("changed")
            bindGroupAttachments(global, root.resolve("bot-one"), "Read /attachments/$path")
            assertEquals("changed", root.resolve("bot-one/$path").readText())
            assertEquals("report", root.resolve("bot-two/$path").readText())
        } finally { root.deleteRecursively() }
    }
}
