package top.wkbin.taixu.runtime.shell

import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Job
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** Foreground cancellation must retain a process whose teardown failed. */
internal class ShellProcessOwner(val process: Process, private val forget: (ShellProcessOwner) -> Unit) {
    private val mutex = Mutex()
    private val processMonitor = Any()
    private var stopped = false
    var ownership: AutoCloseable? = null
    val readers = mutableListOf<Job>()
    private val released = BooleanArray(3)
    private val streams = listOf({ process.outputStream }, { process.inputStream }, { process.errorStream })

    fun stopProcess() = synchronized(processMonitor) {
        if (!stopped) {
            if (process.isAlive) process.destroyForcibly()
            check(process.waitFor(1_000, TimeUnit.MILLISECONDS)) { "命令进程尚未退出，保留资源供重试" }
            stopped = true
        }
    }
    suspend fun close() = withContext(NonCancellable + Dispatchers.IO) {
        mutex.withLock {
            readers.forEach { it.cancel() }
            stopProcess()
            readers.joinAll()
            val failures = mutableListOf<Throwable>()
            streams.forEachIndexed { index, stream ->
                if (!released[index]) try { stream().close(); released[index] = true }
                catch (failure: Throwable) { failures += failure }
            }
            if (failures.isNotEmpty()) throw ProcessCleanupException(failures)
            ownership?.close()
            ownership = null
            forget(this@ShellProcessOwner)
        }
    }
}
