package top.wkbin.taixu.ui.chat

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DeliveryArtifactPathsTest {

    private val root = File("/data/app/files/linux-runtime/workspace")

    @Test
    fun `workspace aliases resolve consistently`() {
        listOf("/workspace/proj-1", "workspace/proj-1", "\\workspace\\proj-1", "proj-1").forEach {
            assertEquals(File(root, "proj-1/Main.kt"), DeliveryArtifactPaths.hostFile(it, "Main.kt", root))
        }
        assertEquals(File(root, "workspace/Main.kt"), DeliveryArtifactPaths.hostFile("/workspace/workspace", "Main.kt", root))
        assertEquals(File(root, "Main.kt"), DeliveryArtifactPaths.hostFile("workspace", "Main.kt", root))
    }

    @Test
    fun `relative artifact path resolves under the project directory`() {
        val file = DeliveryArtifactPaths.hostFile("/workspace/proj-1", "app/build/outputs/apk/release/app.apk", root)
        assertEquals(File(root, "proj-1/app/build/outputs/apk/release/app.apk"), file)

        val log = DeliveryArtifactPaths.hostFile("/workspace/proj-1", ".taixu-outputs/run-1.log", root)
        assertEquals(File(root, "proj-1/.taixu-outputs/run-1.log"), log)
    }

    @Test
    fun `escaping and malformed relative paths are rejected`() {
        assertNull(DeliveryArtifactPaths.hostFile("/workspace/proj-1", "../other/secret.txt", root))
        assertNull(DeliveryArtifactPaths.hostFile("/workspace/proj-1", "a//b.txt", root))
        assertNull(DeliveryArtifactPaths.hostFile("/workspace/proj-1", "./b.txt", root))
        assertNull(DeliveryArtifactPaths.hostFile("/workspace/proj-1", "", root))
        assertNull(DeliveryArtifactPaths.hostFile("", "a.txt", root))
        assertNull(DeliveryArtifactPaths.hostFile("/workspace/proj-1", "a.txt", workspaceRoot = null))
    }

    @Test
    fun `root and nested workspaces preserve the full location`() {
        assertEquals(File(root, "Main.kt"), DeliveryArtifactPaths.hostFile("/workspace", "Main.kt", root))
        assertEquals(File(root, "proj/sub/Main.kt"), DeliveryArtifactPaths.hostFile("/workspace/proj/sub", "Main.kt", root))
        assertNull(DeliveryArtifactPaths.hostFile("/sdcard/project", "Main.kt", root))
        assertNull(DeliveryArtifactPaths.hostFile("/workspace/proj/../other", "Main.kt", root))
    }

    @Test
    fun `leading slash on the relative path is normalised`() {
        assertEquals(File(root, "proj-1/report.pdf"), DeliveryArtifactPaths.hostFile("/workspace/proj-1", "/report.pdf", root))
    }
}
