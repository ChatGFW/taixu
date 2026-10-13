package top.wkbin.taixu.runtime.remote

import com.jcraft.jsch.JSch
import com.jcraft.jsch.KeyPair
import kotlinx.coroutines.runBlocking
import org.apache.sshd.server.SshServer
import org.apache.sshd.server.keyprovider.SimpleGeneratorHostKeyProvider
import org.apache.sshd.common.file.virtualfs.VirtualFileSystemFactory
import org.apache.sshd.sftp.server.SftpSubsystemFactory
import org.junit.*
import org.junit.Assert.*
import org.junit.rules.TemporaryFolder
import top.wkbin.taixu.core.model.*
import top.wkbin.taixu.runtime.environment.ExecutionEnvironmentId
import java.io.ByteArrayOutputStream
import java.util.concurrent.atomic.AtomicInteger
import org.apache.sshd.server.command.Command
import org.apache.sshd.server.ExitCallback
import org.apache.sshd.server.Environment
import org.apache.sshd.server.channel.ChannelSession
import java.io.InputStream
import java.io.OutputStream
import top.wkbin.taixu.runtime.shell.ShellCommand
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import top.wkbin.taixu.core.datastore.RemoteServerRepository

/** Local protocol fixture: no remote machine, paid provider, API key or host shell involved. */
class SshRemoteIntegrationTest {
    @get:Rule val folder = TemporaryFolder()
    private lateinit var ssh: SshServer
    private val authentications = AtomicInteger()
    private val connector = SshConnector()
    private val destroyed = AtomicInteger()
    private var lastCommand = ""
    @Before fun start() {
        val root = folder.newFolder("server").toPath()
        java.nio.file.Files.createDirectory(root.resolve("workspace"))
        ssh = SshServer.setUpDefaultServer().apply {
            host = "127.0.0.1"
            port = 0
            keyPairProvider = SimpleGeneratorHostKeyProvider(folder.root.toPath().resolve("host-key"))
            setPasswordAuthenticator { username, password, _ ->
                authentications.incrementAndGet(); username == "agent" && password == "test-password"
            }
            setPublickeyAuthenticator { username, _, _ -> authentications.incrementAndGet(); username == "agent" }
            fileSystemFactory = VirtualFileSystemFactory(root)
            subsystemFactories = listOf(SftpSubsystemFactory.Builder().build())
            setCommandFactory { _, command ->
                lastCommand = command
                FixtureCommand(command.contains("hold-open"), command.contains("memoryTotal"), command.contains("terminal-bytes"))
            }
            start()
        }
    }
    @After fun stop() { ssh.stop(true) }
    private fun server() = RemoteServer("test-server", "Test", "127.0.0.1", ssh.port, "agent", workspace = "/workspace")
    private suspend fun pin(server: RemoteServer, credentials: SshCredentials): RemoteServer {
        try { connector.test(server, credentials); fail("Must request host key confirmation") }
        catch (e: HostKeyConfirmationRequired) { return server.copy(fingerprint = e.fingerprint) }
        error("Missing fingerprint")
    }
    @Test fun `first contact and changed host key never send credentials`() = runBlocking {
        val credentials = SshCredentials("test-password")
        val trusted = pin(server(), credentials)
        assertEquals(0, authentications.get())
        connector.test(trusted, credentials)
        assertEquals(1, authentications.get())
        try { connector.test(trusted.copy(fingerprint = "SHA256:wrong"), credentials); fail() }
        catch (e: IllegalStateException) { assertTrue(e.message!!.contains("指纹")) }
        assertEquals(1, authentications.get())
    }
    @Test fun `password connection reads writes edits and bounds remote paths`() = runBlocking {
        val credentials = SshCredentials("test-password")
        val trusted = pin(server(), credentials)
        val world = connector.openEnvironment(trusted, credentials, ExecutionEnvironmentId("ssh", trusted.distributionId, "/workspace", "one"))
        try {
            assertTrue(world.files.write("example.txt", "one\ntwo\nthree").isSuccess)
            val entries = world.files.listEntries("/workspace").getOrNull()!!
            assertEquals(listOf("example.txt"), entries.map { it.name })
            assertFalse(entries.single().directory)
            assertEquals("two", world.files.read("/workspace/example.txt", 2, 1).getOrNull())
            assertTrue(world.files.editDetailed("example.txt", "two", "changed").isSuccess)
            assertEquals("one\nchanged\nthree", String(world.files.readRawBytes("example.txt").getOrNull()!!))
            assertTrue(world.files.write("../escape", "no").isFailure)
            assertTrue(world.files.read("/outside", null, null).isFailure)
            assertTrue(world.files.delete("example.txt"))
            assertTrue(world.files.delete("example.txt"))
        } finally { world.close(); world.close() }
    }

    @Test fun `remote artifact hashing streams bytes beyond the raw read limit and stays confined`() = runBlocking {
        val credentials = SshCredentials("test-password")
        val trusted = pin(server(), credentials)
        val world = connector.openEnvironment(trusted, credentials, ExecutionEnvironmentId("ssh", trusted.distributionId, "/workspace", "one"))
        try {
            val bytes = ByteArray(17 * 1024 * 1024) { (it * 13).toByte() }
            java.io.File(folder.root, "server/workspace/artifact.apk").writeBytes(bytes)
            assertTrue(world.files.readRawBytes("artifact.apk").isFailure)
            val observed = world.files.fingerprint("artifact.apk", 20L * 1024 * 1024).getOrNull()!!
            assertEquals(bytes.size.toLong(), observed.bytes)
            assertEquals(java.security.MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }, observed.sha256)
            assertTrue(world.files.fingerprint("artifact.apk", 8).isFailure)
            assertTrue(world.files.fingerprint("../outside.apk").isFailure)
            assertTrue(world.files.listArtifactEntries("missing").getOrNull()!!.isEmpty())
        } finally { world.close() }
    }
    @Test fun `encrypted RSA private key authenticates`() = runBlocking {
        val key = KeyPair.genKeyPair(JSch(), KeyPair.RSA, 2048)
        val output = ByteArrayOutputStream()
        try { key.writePrivateKey(output, "key-passphrase".toByteArray()) } finally { key.dispose() }
        val credentials = SshCredentials(output.toString("UTF-8"), "key-passphrase")
        val trusted = pin(server().copy(authentication = SshAuthentication.PRIVATE_KEY), credentials)
        connector.test(trusted, credentials)
        assertTrue(authentications.get() >= 1)
    }
    @Test fun `shell context quotes paths and rejects injected environment keys`() {
        val command = remoteCommand("printf ok", "/tmp/a'b", mapOf("VALUE" to "a'b\n\$(bad)"))
        assertTrue(command.contains("'\"'\"'"))
        assertTrue(command.endsWith("printf ok\n}"))
        try { remoteCommand("true", "/tmp", mapOf("X;touch hacked" to "value")); fail() }
        catch (_: IllegalArgumentException) { }
    }
    @Test fun `remote execution reports both streams exit status and working directory`() = runBlocking {
        val credentials = SshCredentials("test-password")
        val trusted = pin(server(), credentials)
        val world = connector.openEnvironment(trusted, credentials, ExecutionEnvironmentId("ssh", trusted.distributionId, "/workspace", "execute"))
        try {
            val result = world.execute(ShellCommand("fixture", world.workingDirectory(), mapOf("TEST" to "a'b")))
            assertEquals(7, result.exitCode)
            assertEquals("remote stdout", result.stdout)
            assertEquals("remote stderr", result.stderr)
            assertTrue(lastCommand.startsWith("cd -- '/workspace'"))
            assertTrue(lastCommand.contains("export TEST='a'\"'\"'b';"))
        } finally { world.close() }
    }
    @Test fun `PTY byte stream preserves UTF8 and terminal control sequences`() = runBlocking {
        val credentials = SshCredentials("test-password")
        val trusted = pin(server(), credentials)
        val world = connector.openEnvironment(trusted, credentials, ExecutionEnvironmentId("ssh", trusted.distributionId, "/workspace", "pty-bytes"))
        try {
            val pty = world.openTerminal(top.wkbin.taixu.runtime.shell.SessionConfig(commandLine = "terminal-bytes", workingDirectory = world.workingDirectory())) as SshLinuxSession
            val output = ByteArrayOutputStream()
            withTimeout(5_000) { pty.terminalBytes.collect { output.write(it) } }
            assertArrayEquals("\u001b[31m中文😀\u001b[0m".toByteArray(Charsets.UTF_8), output.toByteArray())
        } finally { world.close() }
    }
    @Test fun `background stop and timeout request remote process termination`() = runBlocking {
        val credentials = SshCredentials("test-password")
        val trusted = pin(server(), credentials)
        val world = connector.openEnvironment(trusted, credentials, ExecutionEnvironmentId("ssh", trusted.distributionId, "/workspace", "process"))
        try {
            val process = world.startBackground("owned", ShellCommand("hold-open", "/workspace"))
            assertTrue(process.session.isAlive)
            assertTrue(world.stopBackground("owned"))
            assertFalse(world.stopBackground("owned"))
            assertTrue(world.listBackground().isEmpty())
            try { world.execute(ShellCommand("hold-open", "/workspace", timeoutMs = 100)); fail() }
            catch (_: kotlinx.coroutines.TimeoutCancellationException) { }
            withTimeout(3000) { while (destroyed.get() < 2) delay(20) }
        } finally { world.close() }
    }
    @Test fun `dashboard and workshop use explicit SSH targets and reject stale file saves`() = runBlocking {
        val credentials = SshCredentials("test-password")
        val trusted = pin(server(), credentials)
        val workspace = java.io.File(folder.root, "server/workspace")
        val project = java.io.File(workspace, "remote-project").apply { mkdir() }
        val file = java.io.File(project, "README.md").apply { writeText("remote content") }
        val otherRoot = java.io.File(workspace, "other").apply { mkdir() }
        java.io.File(otherRoot, "other-project").mkdir()
        val other = trusted.copy(id = "other-server", name = "Other", workspace = "/workspace/other")
        val repository = object : RemoteServerRepository {
            override val servers = MutableStateFlow(listOf(trusted, other))
            override val activeId = MutableStateFlow<String?>(other.id)
            override suspend fun credentials(id: String) = credentials
            override suspend fun save(server: RemoteServer, credentials: SshCredentials) = Unit
            override suspend fun select(id: String?) { activeId.value = id }
            override suspend fun remove(id: String) = Unit
        }
        val service = RemoteSystemService(repository, connector)
        assertTrue(service.projects(trusted.id).any { it.name == "remote-project" })
        assertEquals(listOf("other-project"), service.projects(other.id).map { it.name })
        assertEquals("remote content", service.read(trusted.id, "remote-project/README.md"))
        service.save(trusted.id, "remote-project/README.md", "remote content", "edited remotely")
        assertEquals("edited remotely", file.readText())
        file.writeText("external edit")
        try { service.save(trusted.id, "remote-project/README.md", "edited remotely", "overwrite"); fail() }
        catch (_: IllegalStateException) { }
        assertEquals("external edit", file.readText())
        val metrics = service.inspect(trusted.id)
        assertEquals("x86_64", metrics.architecture)
        assertEquals("Fixture Linux", metrics.distribution)
        assertTrue(lastCommand.contains("df -Pk -- '/workspace'"))
        try { service.list(trusted.id, "../outside"); fail() } catch (_: IllegalArgumentException) { }
        repository.servers.value = listOf(trusted.copy(workspace = other.workspace), other)
        try { service.save(trusted.id, "remote-project/README.md", "external edit", "stale overwrite", trusted); fail() }
        catch (e: IllegalStateException) { assertTrue(e.message!!.contains("档案")) }
        assertEquals("external edit", file.readText())
    }
    private inner class FixtureCommand(private val hold: Boolean, private val metrics: Boolean = false, private val terminalBytes: Boolean = false) : Command {
        private lateinit var stdout: OutputStream
        private lateinit var stderr: OutputStream
        private lateinit var exit: ExitCallback
        override fun setInputStream(input: InputStream) = Unit
        override fun setOutputStream(output: OutputStream) { stdout = output }
        override fun setErrorStream(output: OutputStream) { stderr = output }
        override fun setExitCallback(callback: ExitCallback) { exit = callback }
        override fun start(channel: ChannelSession, environment: Environment) {
            environment.addSignalListener({ _, _ -> exit.onExit(143) })
            if (terminalBytes) {
                "\u001b[31m中文😀\u001b[0m".toByteArray(Charsets.UTF_8).forEach { stdout.write(it.toInt() and 255); stdout.flush() }
                exit.onExit(0); return
            }
            if (metrics) {
                stdout.write(("distribution\tFixture Linux\narchitecture\tx86_64\nkernel\t6.8\n" +
                    "memoryTotal\t8192\nmemoryUsed\t1024\ndiskTotal\t107374182400\ndiskUsed\t10737418240\n" +
                    "load\t0.1 0.2 0.3\nuptime\t3600\nprocesses\t42\n").toByteArray())
                stdout.flush(); exit.onExit(0); return
            }
            stdout.write("remote stdout".toByteArray()); stdout.flush()
            stderr.write("remote stderr".toByteArray()); stderr.flush()
            if (!hold) exit.onExit(7)
        }
        override fun destroy(channel: ChannelSession) { destroyed.incrementAndGet() }
    }
}
