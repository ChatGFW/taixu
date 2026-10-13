package top.wkbin.taixu.runtime.shell

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

/** One per local runtime: known memory-heavy foreground workloads share a FIFO, cancellable slot. */
internal class ForegroundCommandAdmission(private val nanoTime: () -> Long = System::nanoTime) {
    private val memorySlot = Semaphore(1)

    suspend fun execute(command: ShellCommand, executor: ShellExecutor, run: suspend () -> CommandResult): CommandResult {
        if (!CommandResourcePolicy.needsMemorySlot(command)) return command.withOutputCapture(run)
        val queuedAt = nanoTime()
        return memorySlot.withPermit {
            currentCoroutineContext().ensureActive()
            check(!executor.hasUnsettled()) { "前台进程清理尚未完成，暂不能启动新的重型命令；先结束残留进程或重试运行时清理。" }
            val waitMs = ((nanoTime() - queuedAt) / 1_000_000).coerceAtLeast(0)
            // Command timeout and private output capture start only after admission.
            command.withOutputCapture(run).let { it.copy(resourceWaitMs = it.resourceWaitMs + waitMs) }
        }
    }
}
