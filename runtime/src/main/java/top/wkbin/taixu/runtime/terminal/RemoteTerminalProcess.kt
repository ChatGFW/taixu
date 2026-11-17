package top.wkbin.taixu.runtime.terminal

import android.util.Log
import com.termux.terminal.TerminalSession
import com.termux.terminal.TerminalSessionBackend
import java.io.PipedInputStream
import java.io.PipedOutputStream
import java.nio.charset.StandardCharsets
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import top.wkbin.taixu.runtime.remote.SshLinuxSession
import top.wkbin.taixu.runtime.shell.LinuxSession

/**
 * 把任意 [LinuxSession]（远程 SSH PTY）适配成 Termux 渲染后端：
 * 输出经管道喂给 `TerminalSession` 的输入线程，输入/尺寸/终止转发给远程会话。
 */
internal class LinuxSessionTerminalBackend(
    private val session: LinuxSession,
    private val scope: CoroutineScope,
) : TerminalSessionBackend {

    private val pipeOut = PipedOutputStream()
    private val pipeIn = PipedInputStream(pipeOut, PIPE_BUFFER_BYTES)

    /** 待发往远端的按键/粘贴数据；由唯一的写协程顺序消费。 */
    private val inputQueue = Channel<ByteArray>(Channel.UNLIMITED)

    @Volatile private var pump: Job? = null
    @Volatile private var writer: Job? = null
    @Volatile private var closed = false

    override fun start(rows: Int, columns: Int): Int {
        if (closed) return -1
        setSize(rows, columns)
        pump = scope.launch(Dispatchers.IO) {
            try {
                suspend fun forward(bytes: ByteArray) {
                    pipeOut.write(bytes)
                    pipeOut.flush()
                }
                if (session is SshLinuxSession) session.terminalBytes.collect { forward(it) }
                else session.output.collect { forward(it.text.toByteArray(StandardCharsets.UTF_8)) }
            } catch (t: CancellationException) {
                throw t
            } catch (_: Throwable) {
                // 输出流异常等同会话结束：由 finally 关闭管道，read() 随即返回 EOF。
            } finally {
                runCatching { pipeOut.close() }
            }
        }
        writer = scope.launch(Dispatchers.IO) {
            for (chunk in inputQueue) {
                runCatching { session.write(chunk) }
            }
        }
        // SSH 会话不暴露本地 pid：用正哨兵值让 Termux 侧 isRunning / write 判定成立。
        return session.pid?.takeIf { it > 0L }?.toInt() ?: 1
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int =
        pipeIn.read(buffer, offset, length)

    override fun write(buffer: ByteArray, offset: Int, length: Int) {
        if (closed || length <= 0) return
        // Termux 在输入线程上直接调这里：原来 runBlocking 等 SSH 写完，网络慢时按键卡在
        // 调用线程（UI 上就是输入延迟/卡顿）。改为投递到后台唯一写协程，顺序由队列保证。
        inputQueue.trySend(buffer.copyOfRange(offset, offset + length))
    }

    override fun setSize(rows: Int, columns: Int) {
        scope.launch(Dispatchers.IO) { runCatching { session.resize(columns, rows) } }
    }

    override fun waitForExit(): Int {
        // 保留阻塞语义：调用方是 Termux 的后台等待线程，需要拿到退出码。
        runBlocking { while (session.isAlive) delay(50) }
        return (session as? SshLinuxSession)?.exitCode ?: -1
    }

    override fun kill() {
        // 终止必须同步完成：返回后 Termux 侧就认为会话已死，不能留后台收尾。
        runBlocking { session.close() }
    }

    override fun close() {
        if (closed) return
        closed = true
        inputQueue.close()
        writer?.cancel()
        runBlocking { session.close() }
        pump?.cancel()
        runCatching { pipeIn.close() }
        runCatching { pipeOut.close() }
    }

    private companion object {
        const val PIPE_BUFFER_BYTES = 64 * 1024
    }
}

/** 远程终端进程工厂：用外部 backend 驱动同一 Termux 渲染层。 */
fun interface RemoteTerminalProcessFactory {
    fun create(
        backend: TerminalSessionBackend,
        name: String,
        client: TerminalSessionClientRouter,
    ): TerminalProcess
}

class TermuxRemoteTerminalProcessFactory : RemoteTerminalProcessFactory {
    override fun create(
        backend: TerminalSessionBackend,
        name: String,
        client: TerminalSessionClientRouter,
    ): TerminalProcess {
        Log.i("TaiXuTerminal", "createRemoteSession name=$name")
        val terminal = TerminalSession(name, 2000, client, backend)
        return object : TerminalProcess {
            override val session = terminal
            override val isAlive get() = terminal.isRunning
            override suspend fun finish() {
                withContext(Dispatchers.IO) { terminal.finishIfRunning() }
                check(withTimeoutOrNull(5_000) { while (terminal.isRunning) delay(10); true } == true) {
                    "终端进程尚未退出，保留资源供重试"
                }
            }
            override fun write(data: ByteArray) = terminal.write(data, 0, data.size)
        }
    }
}
