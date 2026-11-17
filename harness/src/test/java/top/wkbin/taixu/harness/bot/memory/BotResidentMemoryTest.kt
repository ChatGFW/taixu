package top.wkbin.taixu.harness.bot.memory

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import top.wkbin.taixu.core.common.logging.AppLogger
import top.wkbin.taixu.runtime.ElfInspector
import top.wkbin.taixu.runtime.RuntimePathManager
import top.wkbin.taixu.runtime.rootfs.RootfsValidator

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BotResidentMemoryTest {
    private val context = ApplicationProvider.getApplicationContext<Context>().also {
        it.applicationInfo.nativeLibraryDir = it.cacheDir.absolutePath
    }
    private val manager = BotMemoryManager(RuntimePathManager(context, RootfsValidator(ElfInspector())), AppLogger(context) { it })
    private val resident = BotResidentMemory(manager)

    @Test fun `same session keeps the first snapshot until the user invalidates it`() = runBlocking {
        val id = UUID.randomUUID().toString()
        try {
            manager.initRepo(id, "Test")
            manager.writeMemory(id, BotMemoryManager.PERSONA, "v1-persona", "persona v1")
            val first = resident.render(id, "session-a")
            assertTrue(first.contains("v1-persona"))

            manager.writeMemory(id, BotMemoryManager.PERSONA, "v2-persona", "persona v2")
            assertTrue("模型自写不应改变本会话注入", resident.render(id, "session-a").contains("v1-persona"))

            resident.invalidateBot(id)
            assertTrue(resident.render(id, "session-a").contains("v2-persona"))
        } finally {
            manager.deleteMemory(id)
        }
    }

    @Test fun `sessions freeze independent snapshots`() = runBlocking {
        val id = UUID.randomUUID().toString()
        try {
            manager.initRepo(id, "Test")
            manager.writeMemory(id, BotMemoryManager.PERSONA, "old", "persona old")
            resident.render(id, "session-a")
            resident.render(id, "session-b")
            manager.writeMemory(id, BotMemoryManager.PERSONA, "new", "persona new")

            resident.invalidateSession("session-a")
            assertTrue(resident.render(id, "session-a").contains("new"))
            assertTrue(resident.render(id, "session-b").contains("old"))
        } finally {
            manager.deleteMemory(id)
        }
    }

    @Test fun `knowledge bodies stay on demand`() = runBlocking {
        val id = UUID.randomUUID().toString()
        try {
            manager.initRepo(id, "Test")
            manager.writeMemory(id, BotMemoryManager.PERSONA, "persona", "persona")
            manager.writeMemory(id, "knowledge/api.md", "SECRET-KNOWLEDGE-BODY", "knowledge")
            val text = resident.render(id, "session-a")
            assertFalse(text.contains("SECRET-KNOWLEDGE-BODY"))
            assertTrue(text.contains("memory-read"))
        } finally {
            manager.deleteMemory(id)
        }
    }

    @Test fun `oversized files are truncated in injection only`() = runBlocking {
        val id = UUID.randomUUID().toString()
        try {
            manager.initRepo(id, "Test")
            manager.writeMemory(id, BotMemoryManager.PERSONA, "p".repeat(20_000), "persona big")
            manager.writeMemory(id, BotMemoryManager.PREFERENCES, "important preference", "preference")
            val text = resident.render(id, "session-a")

            assertTrue(text.length <= BotResidentMemory.MAX_TOTAL_CHARS)
            assertTrue(text.contains("important preference"))
            assertTrue(text.contains("超出 ${BotResidentMemory.PERSONA_CHARS} 字符上限"))
            assertEquals(20_000, manager.readMemory(id, BotMemoryManager.PERSONA).length)
        } finally {
            manager.deleteMemory(id)
        }
    }

    @Test fun `empty memory renders nothing`() = runBlocking {
        val id = UUID.randomUUID().toString()
        try {
            manager.initRepo(id, "Test")
            assertEquals("", resident.render(id, "session-a"))
        } finally {
            manager.deleteMemory(id)
        }
    }
}
