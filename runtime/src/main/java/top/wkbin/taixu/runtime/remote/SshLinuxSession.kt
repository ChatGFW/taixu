package top.wkbin.taixu.runtime.remote

import com.jcraft.jsch.ChannelExec
import com.jcraft.jsch.Session
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import top.wkbin.taixu.runtime.shell.*
import java.io.InputStream
import java.nio.charset.StandardCharsets

/** Streams are drained separately; closing a channel unblocks both readers. */
internal class SshLinuxSession private constructor(private val channel: ChannelExec) : LinuxSession {
    private val stdout = channel.inputStream
    private val stderr = channel.errStream
    private val input = channel.outputStream
    override val isAlive get() = channel.isConnected && !channel.isClosed
    val exitCode get() = channel.exitStatus
    /** PTY renderer consumes bytes directly, preserving escape sequences and split UTF-8. */
    val terminalBytes: Flow<ByteArray> = channelFlow {
        suspend fun read(stream: InputStream) {
            val buffer = ByteArray(4096)
            while (true) {
                val size = runInterruptible(Dispatchers.IO) { stream.read(buffer) }
                if (size < 0) break
                if (size > 0) send(buffer.copyOf(size))
            }
        }
        launch { read(stdout) }
        launch { read(stderr) }
    }
    override val output: Flow<TerminalOutput> = channelFlow {
        suspend fun read(stream: InputStream, kind: TerminalStream) {
            val reader = stream.reader(StandardCharsets.UTF_8)
            val buffer = CharArray(4096)
            while (true) {
                val size = try { runInterruptible(Dispatchers.IO) { reader.read(buffer) } }
                catch (e: java.io.IOException) { currentCoroutineContext().ensureActive(); throw e }
                if (size < 0) break
                send(TerminalOutput(kind, String(buffer, 0, size)))
            }
        }
        launch { read(stdout, TerminalStream.STDOUT) }
        launch { read(stderr, TerminalStream.STDERR) }
    }
    override suspend fun write(data: ByteArray) = runInterruptible(Dispatchers.IO) {
        synchronized(input) { input.write(data); input.flush() }
    }
    override suspend fun resize(columns: Int, rows: Int) { channel.setPtySize(columns, rows, 0, 0) }
    override suspend fun interrupt() { channel.sendSignal("INT") }
    override suspend fun close() = withContext(NonCancellable + Dispatchers.IO) {
        if (isAlive) {
            channel.sendSignal("TERM")
            repeat(30) { if (isAlive) delay(100) }
            if (isAlive) {
                channel.sendSignal("KILL")
                repeat(20) { if (isAlive) delay(100) }
            }
            check(!isAlive) { "服务器未确认远程进程退出，保留连接以便重试停止" }
        }
        channel.disconnect()
    }

    companion object {
        suspend fun open(session: Session, config: SessionConfig, pty: Boolean): SshLinuxSession = withContext(Dispatchers.IO) {
            val channel = session.openChannel("exec") as ChannelExec
            try {
                channel.setPty(pty)
                if (pty) { channel.setPtyType("xterm-256color"); channel.setPtySize(config.columns, config.rows, 0, 0) }
                channel.setCommand(remoteCommand(config.commandLine, config.workingDirectory, config.environment))
                val process = SshLinuxSession(channel)
                runInterruptible { channel.connect(15_000) }
                currentCoroutineContext().ensureActive()
                process
            } catch (t: Throwable) { channel.disconnect(); throw t }
        }
    }
}
