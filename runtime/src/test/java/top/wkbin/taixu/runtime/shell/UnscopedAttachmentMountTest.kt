package top.wkbin.taixu.runtime.shell

import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ApplicationInfo
import androidx.test.core.app.ApplicationProvider
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import top.wkbin.taixu.core.model.StorageMountBinding
import top.wkbin.taixu.runtime.AttachmentPaths
import top.wkbin.taixu.runtime.ElfInspector
import top.wkbin.taixu.runtime.RuntimePathManager
import top.wkbin.taixu.runtime.backend.BackendSelector
import top.wkbin.taixu.runtime.backend.GuestCommandBuilder
import top.wkbin.taixu.runtime.rootfs.RootfsValidator

/**
 * 无会话（以及后台进程）不得把附件全局根挂进 guest。
 * 全局根下面就是 `sessions/`，挂进去就能读到其他会话的文件。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class UnscopedAttachmentMountTest {
    private val context: Context = object : ContextWrapper(ApplicationProvider.getApplicationContext<Context>()) {
        override fun getApplicationInfo(): ApplicationInfo = baseContext.applicationInfo.apply {
            nativeLibraryDir = File(baseContext.filesDir, "lib").apply { mkdirs() }.absolutePath
        }
    }
    private val paths = RuntimePathManager(context, RootfsValidator(ElfInspector()))

    @Test fun backgroundMountsStayInsideOneSessionOrShared() {
        val secret = File(AttachmentPaths.sessionsRoot(context.filesDir), "other/secret.txt")
        secret.parentFile?.mkdirs()
        secret.writeText("secret")
        val seen = mutableListOf<Mounted>()
        val registry = ProcessRegistryImpl(paths, recordingSelector(seen))

        capture(registry, seen, ShellCommand("true"))
        capture(registry, seen, ShellCommand("true", sessionId = ""))
        capture(registry, seen, ShellCommand("true", sessionId = "s1"))
        capture(registry, seen, ShellCommand("true", sessionId = "../../etc"))

        val unscoped = seen[0]
        val blank = seen[1]
        val session = seen[2]
        val traversal = seen[3]
        val shared = paths.sharedAttachmentsDir.canonicalPath
        val global = paths.attachmentsDir.canonicalPath
        val sessions = AttachmentPaths.sessionsRoot(context.filesDir).canonicalPath

        assertEquals(shared, unscoped.attachments.canonicalPath)
        assertEquals(shared, blank.attachments.canonicalPath)
        assertNotEquals(global, unscoped.attachments.canonicalPath)
        assertFalse(secret.canonicalPath.startsWith(unscoped.attachments.canonicalPath + File.separator))
        assertFalse(File(unscoped.attachments, "sessions").exists())

        assertEquals(AttachmentPaths.session(context.filesDir, "s1").canonicalPath, session.attachments.canonicalPath)
        assertFalse(secret.canonicalPath.startsWith(session.attachments.canonicalPath + File.separator))
        assertTrue(traversal.attachments.canonicalPath.startsWith(sessions + File.separator))
        assertFalse(traversal.attachments.canonicalPath.split(File.separator).any { it == ".." })
        seen.forEach {
            assertEquals(paths.attachmentsSkillsDir.canonicalPath, it.skills?.canonicalPath)
            assertNotEquals(global, it.attachments.canonicalPath)
        }
    }

    @Test fun productionGuestBuildersNeverBindTheGlobalRoot() {
        val root = runtimeSources()
        val forbidden = "attachmentsDir = pathManager.attachmentsDir"
        root.walkTopDown().filter { it.isFile && it.extension == "kt" }.forEach { file ->
            assertFalse(file.path, file.readText().contains(forbidden))
        }
        val health = File(root, "RuntimeHealthChecker.kt").readText()
        val registry = File(root, "shell/ProcessRegistry.kt").readText()
        val runtime = File(root, "LinuxRuntimeImpl.kt").readText()
        assertTrue(health.contains("attachmentsDir = pathManager.sharedAttachmentsDir"))
        assertTrue(registry.contains("pathManager.sessionAttachmentsDir(command.sessionId)"))
        assertTrue(registry.contains("sharedSkillsDir = pathManager.attachmentsSkillsDir"))
        assertFalse(runtime.contains("?: pathManager.attachmentsDir"))
    }

    private fun capture(registry: ProcessRegistryImpl, seen: MutableList<Mounted>, command: ShellCommand) {
        val before = seen.size
        val failure = runCatching { runBlocking { registry.start("mount-$before", command) } }.exceptionOrNull()
        assertTrue(failure is MountRecorded)
        assertEquals(before + 1, seen.size)
    }

    private fun recordingSelector(seen: MutableList<Mounted>): BackendSelector = BackendSelector {
        object : GuestCommandBuilder {
            override fun build(
                prootBinary: File,
                rootfsDir: File,
                workspaceDir: File,
                homeDir: File,
                optDir: File,
                tmpDir: File,
                attachmentsDir: File,
                sharedSkillsDir: File?,
                command: ShellCommand,
                mounts: List<StorageMountBinding>,
                emulatorBinary: File?,
                hostCredential: Map<String, String>,
            ): List<String> {
                seen += Mounted(attachmentsDir, sharedSkillsDir)
                throw MountRecorded()
            }

            override fun buildInteractive(
                prootBinary: File,
                rootfsDir: File,
                workspaceDir: File,
                homeDir: File,
                optDir: File,
                tmpDir: File,
                attachmentsDir: File,
                sharedSkillsDir: File?,
                config: SessionConfig,
                ptyMarker: String?,
                nativePty: Boolean,
                mounts: List<StorageMountBinding>,
                emulatorBinary: File?,
                hostCredential: Map<String, String>,
            ): List<String> = error("unused")
        }
    }

    private fun runtimeSources(): File {
        val candidates = listOf(
            File("src/main/java/top/wkbin/taixu/runtime"),
            File("runtime/src/main/java/top/wkbin/taixu/runtime"),
        )
        return candidates.firstOrNull { it.isDirectory }
            ?: error("找不到 runtime 源码，cwd=${File(".").absolutePath}")
    }

    private class Mounted(val attachments: File, val skills: File?)
    private class MountRecorded : RuntimeException()
}
