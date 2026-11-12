package top.wkbin.taixu.core.datastore

import android.util.AtomicFile
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import top.wkbin.taixu.core.model.RemoteServer
import top.wkbin.taixu.core.model.SshCredentials

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RemoteServerPrimaryLocalTest {
    @get:Rule val folder = TemporaryFolder()
    @get:Rule val renameGuard = RequiresAtomicRenameOverwrite()

    private val encrypt = { plain: String -> "test:$plain" }
    private val decrypt = { encrypted: String ->
        encrypted.removePrefix("test:").takeIf { encrypted.startsWith("test:") }
    }

    private fun repository(file: File) =
        EncryptedRemoteServerRepository(AtomicFile(file), encrypt, decrypt)

    @Test
    fun `select rejects remote primary and save keeps active null`() = runBlocking {
        val file = File(folder.root, "remote-servers.enc")
        val repo = repository(file)
        val server = RemoteServer(
            id = "srv",
            name = "Srv",
            host = "example.test",
            username = "root",
            workspace = "/home/root",
            fingerprint = "SHA256:fixture",
        )
        repo.save(server, SshCredentials("password"))
        assertNull(repo.activeId.value)
        assertEquals(1, repo.servers.value.size)
        try {
            repo.select("srv")
            fail("remote primary should be rejected")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("主系统固定为本机"))
        }
        assertNull(repo.activeId.value)
        repo.select(null)
        assertNull(repo.activeId.value)
    }

    @Test
    fun `historical remote primary is cleared on load`() = runBlocking {
        val file = File(folder.root, "remote-servers.enc")
        val repo = repository(file)
        val server = RemoteServer(
            id = "legacy",
            name = "Legacy",
            host = "legacy.test",
            username = "agent",
            workspace = "/workspace",
            fingerprint = "SHA256:legacy",
        )
        repo.save(server, SshCredentials("secret"))
        val encrypted = file.readText()
        val plain = checkNotNull(decrypt(encrypted))
        require("\"active\":null" in plain) { plain }
        file.writeText(encrypt(plain.replace("\"active\":null", "\"active\":\"legacy\"")))
        val reopened = repository(file)
        assertNull(reopened.activeId.value)
        assertEquals(listOf("legacy"), reopened.servers.value.map { it.id })
        val cleared = checkNotNull(decrypt(file.readText()))
        assertTrue("\"active\":null" in cleared || !cleared.contains("\"active\":\"legacy\""))
    }
}
