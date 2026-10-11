package top.wkbin.taixu.runtime.shell

import java.io.File
import java.io.IOException
import java.io.InputStreamReader
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * 交互式会话的无 JNI backend，底层由 Debian `script` 创建 PTY。
 *
 * 它提供 stdin/stdout 生命周期、ANSI 输出流和可注入的窗口调整回调；后续接入真正
 * forkpty backend 时无需修改 LinuxSession API。
 */
class ProcessLinuxSession internal constructor(
    private val process: Process,
    private val allowSttyResize: Boolean = true,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO),
    private val resizeCallback: (suspend (columns: Int, rows: Int) -> Unit)? = null,
    private val cleanupCallback: suspend () -> Unit = {},
) : LinuxSession {
    constructor(
        command: List<String>,
        hostEnvironment: Map<String, String> = emptyMap(),
        allowSttyResize: Boolean = true,
        scope: CoroutineScope = CoroutineScope(Dispatchers.IO),
        resizeCallback: (suspend (columns: Int, rows: Int) -> Unit)? = null,
        cleanupCallback: suspend () -> Unit = {},
    ) : this(startProcess(command, hostEnvironment, scope), allowSttyResize, scope, resizeCallback, cleanupCallback)

    private val outputChannel = Channel<TerminalOutput>(OUTPUT_BUFFER_CAPACITY)
    private val closed = AtomicBoolean(false)
    private val closeMutex = Mutex()
    private var processStopped = false
    private val released = BooleanArray(4)
    private val releaseSteps: List<suspend () -> Unit> = listOf(
        { process.outputStream.close() },
        { process.inputStream.close() },
        { process.errorStream.close() },
        { cleanupCallback() },
    )
    private val sessionScope = CoroutineScope(
        scope.coroutineContext.minusKey(Job) + SupervisorJob(scope.coroutineContext[Job]),
    )
    private val readerJob: Job = sessionScope.launch {
        try {
            InputStreamReader(process.inputStream, Charsets.UTF_8).use { input ->
                val buffer = CharArray(4096)
                while (!closed.get()) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    if (read > 0) {
                        outputChannel.send(
                            TerminalOutput(
                                TerminalStream.STDOUT,
                                String(buffer, 0, read),
                            ),
                        )
                    }
                }
            }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (io: IOException) {
            // close()/process.destroy() closes the stream from another thread while
            // this coroutine is blocked in read(); Android surfaces that as
            // InterruptedIOException. Treat it as EOF instead of crashing the app.
        } finally { outputChannel.close() }
    }.also { it.invokeOnCompletion { outputChannel.close() } }

    override val output: Flow<TerminalOutput> = outputChannel.receiveAsFlow()

    // Android's Process API does not expose a portable PID accessor; keep this
    // nullable rather than parsing implementation-specific /proc state.
    override val pid: Long? get() = null

    override val isAlive: Boolean
        get() = !closed.get() && process.isAlive

    override suspend fun write(data: ByteArray) = withContext(Dispatchers.IO) {
        if (!closed.get()) {
            process.outputStream.write(data)
            process.outputStream.flush()
        }
    }

    override suspend fun resize(columns: Int, rows: Int) {
        if (closed.get()) return
        // The Script backend records the PTY slave path and updates it through a
        // separate Runtime command. This avoids injecting `stty` into a TUI's
        // stdin while keeping the LinuxSession contract backend-independent.
        if (resizeCallback != null) {
            resizeCallback.invoke(columns, rows)
        } else if (allowSttyResize) {
            write("stty cols $columns rows $rows\n".toByteArray())
        }
    }

    override suspend fun interrupt() = write(byteArrayOf(3))

    override suspend fun close() {
        val closing = currentCoroutineContext()[ClosingSessions]?.sessions.orEmpty()
        check(this !in closing) { "进程清理回调不能再次关闭所属会话" }
        withContext(NonCancellable + Dispatchers.IO + ClosingSessions(closing + this)) {
            closeMutex.withLock {
                closed.set(true)
                readerJob.cancel()
                sessionScope.cancel()
                outputChannel.close()
                // A blocking stream read needs the process to exit before it can be joined.
                if (!processStopped) {
                    stopProcess()
                    processStopped = true
                }
                readerJob.join()
                val failures = mutableListOf<Throwable>()
                releaseSteps.forEachIndexed { index, release ->
                    if (!released[index]) {
                        try { release(); released[index] = true }
                        catch (failure: Throwable) { failures += failure }
                    }
                }
                if (failures.isNotEmpty()) throw ProcessCleanupException(failures)
            }
        }
    }

    private fun stopProcess() {
        if (process.isAlive) process.destroy()
        if (!process.waitFor(PROCESS_SHUTDOWN_TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
            process.destroyForcibly()
            check(process.waitFor(PROCESS_SHUTDOWN_TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
                "进程强制终止后仍未退出，保留会话供重试"
            }
        }
    }

    private class ClosingSessions(val sessions: Set<ProcessLinuxSession>) :
        AbstractCoroutineContextElement(Key) {
        companion object Key : CoroutineContext.Key<ClosingSessions>
    }

    private companion object {
        const val PROCESS_SHUTDOWN_TIMEOUT_MS = 500L
        const val OUTPUT_BUFFER_CAPACITY = 1024

        fun startProcess(command: List<String>, hostEnvironment: Map<String, String>, scope: CoroutineScope): Process {
            scope.coroutineContext.ensureActive()
            return ProcessBuilder(command).directory(File("/"))
                .apply { environment().clear(); environment().putAll(hostEnvironment) }
                .redirectErrorStream(true).start()
        }
    }
}
