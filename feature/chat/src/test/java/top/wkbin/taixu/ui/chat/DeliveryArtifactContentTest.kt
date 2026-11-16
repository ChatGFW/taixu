package top.wkbin.taixu.ui.chat

import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import top.wkbin.taixu.core.common.files.FileDigest

class DeliveryArtifactContentTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test fun `preview distinguishes text oversized binary and unreadable files`() = runBlocking {
        val file = temporary.newFile()
        file.writeText("hello 中文")
        assertEquals(DeliveryTextPreview.Text("hello 中文"), readDeliveryText(file))
        file.writeText("a".repeat(1024 * 1024))
        assertTrue(readDeliveryText(file) is DeliveryTextPreview.Text)
        file.appendText("a")
        assertEquals(DeliveryTextPreview.TooLarge, readDeliveryText(file))
        file.writeBytes(byteArrayOf(0, 1, 2))
        assertEquals(DeliveryTextPreview.Binary, readDeliveryText(file))
        assertEquals(DeliveryTextPreview.Unreadable, readDeliveryText(File(temporary.root, "missing")))
    }

    @Test fun `integrity verification detects same size replacements and missing files`() = runBlocking {
        val file = temporary.newFile().apply { writeText("first") }
        val sha256 = FileDigest.sha256(file)!!
        assertEquals(DeliveryIntegrity.MATCHED, verifyDeliveryFile(file, sha256))
        val stamp = file.lastModified()
        file.writeText("other")
        file.setLastModified(stamp)
        assertEquals(DeliveryIntegrity.CHANGED, verifyDeliveryFile(file, sha256))
        assertTrue(file.delete())
        assertEquals(DeliveryIntegrity.MISSING, verifyDeliveryFile(file, sha256))
        assertEquals(DeliveryIntegrity.UNRESOLVED, verifyDeliveryFile(null, sha256))
    }
}
