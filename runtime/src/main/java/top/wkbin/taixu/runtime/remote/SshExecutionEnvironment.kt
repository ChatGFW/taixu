package top.wkbin.taixu.runtime.remote

import com.jcraft.jsch.Session
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import top.wkbin.taixu.core.model.SshCredentials
import top.wkbin.taixu.runtime.environment.*
import top.wkbin.taixu.runtime.shell.*
import java.util.concurrent.ConcurrentHashMap

/** One SSH connection per immutable Harness owner, including independently owned children. */
internal class SshExecutionEnvironment(
    private val connection: Session,
    override val id: ExecutionEnvironmentId,
    private val credentials: SshCredentials,
    override val logicalWorkspace: String = "/workspace",
) : ExecutionEnvironment {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()
    private var closed = false
    private val sessions = mutableSetOf<SshLinuxSession>()
    private data class Background(val process: ManagedProcess, val job: Job, val logs: ArrayDeque<String>)
    private val background = ConcurrentHashMap<String, Background>()
    override val label: String get() = connection.host
    override val files: ExecutionFiles = SshExecutionFiles(connection, id.workspace, logicalWorkspace,
        fileLocks.computeIfAbsent("${connection.host}:${connection.port}:${connection.userName}:${id.workspace}") { Mutex() })
    override suspend fun outputSecrets() = listOf(credentials.secret, credentials.passphrase).filter { it.isNotEmpty() }
    override fun workingDirectory(explicit: String?): String {
        val path = explicit?.takeIf { it.isNotBlank() } ?: return id.workspace
        return if (path == logicalWorkspace || path.startsWith("$logicalWorkspace/")) id.workspace + path.removePrefix(logicalWorkspace)
        else if (!path.startsWith('/')) "${id.workspace}/$path" else path
    }

    private suspend fun open(config: SessionConfig, pty: Boolean): SshLinuxSession = mutex.withLock {
        check(!closed) { "远程执行环境已关闭" }
        val session = SshLinuxSession.open(connection, config, pty)
        sessions.add(session)
        session
    }
    override suspend fun openSession(config: SessionConfig): LinuxSession = open(config, false)
    override suspend fun openTerminal(config: SessionConfig): LinuxSession = open(config, true)
    override suspend fun execute(command: ShellCommand): CommandResult {
        require(!command.useQemuCompatibility) { "远程环境不使用手机 QEMU" }
        val started = System.currentTimeMillis()
        val session = open(SessionConfig(commandLine = command.commandLine, workingDirectory = command.workingDirectory,
            environment = command.environment), command.forcePty)
        val stdout = StringBuilder()
        val stderr = StringBuilder()
        var failure: Throwable? = null
        return try {
            withTimeout(command.timeoutMs) {
                session.output.collect { output ->
                    val buffer = if (output.stream == TerminalStream.STDERR) stderr else stdout
                    if (buffer.length < MAX_OUTPUT) buffer.append(output.text.take(MAX_OUTPUT - buffer.length))
                    command.onOutput?.invoke(output.text)
                }
                while (session.isAlive) delay(10)
                CommandResult(session.exitCode, stdout.toString(), stderr.toString(), System.currentTimeMillis() - started)
            }
        } catch (t: Throwable) { failure = t; throw t }
        finally {
            try { withContext(NonCancellable) { session.close(); mutex.withLock { sessions.remove(session) } } }
            catch (cleanup: Throwable) { failure?.addSuppressed(cleanup) ?: throw cleanup }
        }
    }
    override suspend fun startBackground(id: String, command: ShellCommand): ManagedProcess = mutex.withLock {
        check(!closed)
        check(background[id] == null) { "远程进程名称已存在，请先停止" }
        require(!command.useQemuCompatibility)
        val session = SshLinuxSession.open(connection, SessionConfig(commandLine = command.commandLine,
            workingDirectory = command.workingDirectory, environment = command.environment), command.forcePty)
        try {
            sessions.add(session)
            val logs = ArrayDeque<String>()
            val job = scope.launch(start = CoroutineStart.LAZY) {
                session.output.collect { output -> synchronized(logs) {
                    logs.addLast(output.text.take(8192))
                    while (logs.size > 256) logs.removeFirst()
                } }
            }
            val process = ManagedProcess(id, System.currentTimeMillis(), session, type = ProcessType.COMMAND)
            background[id] = Background(process, job, logs)
            job.start()
            process
        } catch (t: Throwable) { session.close(); sessions.remove(session); throw t }
    }
    override suspend fun stopBackground(id: String): Boolean = withContext(NonCancellable) {
        mutex.withLock {
            val entry = background[id] ?: return@withLock false
            entry.process.session.close()
            entry.job.cancelAndJoin()
            sessions.remove(entry.process.session)
            background.remove(id, entry)
            true
        }
    }
    override fun listBackground() = background.values.map { it.process }
    override fun getBackgroundLogs(id: String): List<String> = background[id]?.logs?.let { synchronized(it) { it.toList() } }.orEmpty()
    override suspend fun close() = withContext(NonCancellable) {
        val failures = mutex.withLock {
            closed = true
            val collected = mutableListOf<Throwable>()
            for (session in sessions.toList()) {
                try { session.close(); sessions.remove(session) } catch (t: Throwable) { collected.add(t) }
            }
            // Keep failed channels reachable for a later stop retry; only confirmed disposal releases transport.
            if (collected.isEmpty()) {
                connection.disconnect()
                scope.coroutineContext[Job]?.cancelAndJoin()
                background.clear()
            }
            collected
        }
        if (failures.isNotEmpty()) throw ProcessCleanupException(failures)
    }
    companion object {
        private const val MAX_OUTPUT = 2 * 1024 * 1024
        private val fileLocks = ConcurrentHashMap<String, Mutex>()
    }
}
