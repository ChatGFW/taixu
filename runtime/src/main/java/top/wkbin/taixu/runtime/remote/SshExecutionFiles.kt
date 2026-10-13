package top.wkbin.taixu.runtime.remote

import com.jcraft.jsch.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import top.wkbin.taixu.core.common.result.*
import top.wkbin.taixu.runtime.environment.*
import java.util.UUID

internal class SshExecutionFiles(private val session: Session, private val workspace: String, private val logicalWorkspace: String, private val mutex: Mutex) : ExecutionFiles {
    private suspend fun <T> access(operation: (ChannelSftp, String) -> T): T = mutex.withLock {
        runInterruptible(Dispatchers.IO) {
            val channel = session.openChannel("sftp") as ChannelSftp
            try {
                channel.connect(15_000)
                val root = channel.realpath(workspace).trimEnd('/')
                require(root.isNotEmpty()) { "远程工作区不能解析为根目录" }
                operation(channel, root)
            } finally { channel.disconnect() }
        }
    }
    private fun resolve(channel: ChannelSftp, root: String, path: String, followFinalLink: Boolean = true): String {
        require('\u0000' !in path && path.split('/').none { it == ".." }) { "非法远程路径" }
        val target = if (path == logicalWorkspace || path.startsWith("$logicalWorkspace/")) root + path.removePrefix(logicalWorkspace)
            else if (path.startsWith('/')) path else "$root/$path"
        // Both existing symlinks and new-file parents must stay inside the real workspace.
        val resolved = if (!followFinalLink) channel.realpath(target.substringBeforeLast('/')) + "/" + target.substringAfterLast('/')
        else try { channel.realpath(target) } catch (e: SftpException) {
            if (e.id != ChannelSftp.SSH_FX_NO_SUCH_FILE) throw e
            channel.realpath(target.substringBeforeLast('/')) + "/" + target.substringAfterLast('/')
        }
        require(resolved == root || resolved.startsWith("$root/")) { "路径超出远程工作区" }
        return resolved
    }
    private fun bytes(channel: ChannelSftp, path: String): ByteArray = channel.get(path).use {
        val bytes = it.readBounded(MAX_BYTES)
        require(bytes.size <= MAX_BYTES) { "远程文件超过 16 MiB" }
        bytes
    }
    private fun writeAtomic(channel: ChannelSftp, target: String, content: String) {
        val bytes = content.toByteArray(Charsets.UTF_8)
        require(bytes.size <= MAX_BYTES) { "远程文件超过 16 MiB" }
        val previous = try { channel.stat(target) } catch (e: SftpException) {
            if (e.id != ChannelSftp.SSH_FX_NO_SUCH_FILE) throw e
            null
        }
        require(previous?.isDir != true) { "不能覆盖目录" }
        val temporary = target.substringBeforeLast('/') + "/.taixu-${UUID.randomUUID()}.tmp"
        try {
            bytes.inputStream().use { channel.put(it, temporary) }
            channel.chmod(previous?.permissions?.and(511) ?: 384, temporary)
            // JSch uses posix-rename@openssh.com when supported; otherwise replacement fails closed.
            channel.rename(temporary, target)
        } finally {
            try { channel.rm(temporary) } catch (e: SftpException) { if (e.id != ChannelSftp.SSH_FX_NO_SUCH_FILE) throw e }
        }
    }
    private suspend fun <T> result(operation: suspend () -> T): AppResult<T> = try {
        AppResult.Success(operation())
    } catch (e: CancellationException) { throw e }
    catch (e: Exception) {
        currentCoroutineContext().ensureActive()
        AppResult.Failure(AppError(ErrorCode.IO, "远程文件操作失败：请检查工作区路径、权限和连接"))
    }

    override suspend fun read(path: String, offset: Int?, limit: Int?): AppResult<String> = result {
        require(offset == null || offset >= 0)
        require(limit == null || limit >= 0)
        access { channel, root ->
            val target = resolve(channel, root, path)
            if (channel.stat(target).isDir) {
                channel.ls(target).filterIsInstance<ChannelSftp.LsEntry>().take(2000).joinToString("\n") { it.filename }
            } else String(bytes(channel, target), Charsets.UTF_8).lines().drop(((offset ?: 1) - 1).coerceAtLeast(0)).take((limit ?: 2000).coerceAtMost(2000)).joinToString("\n")
        }
    }
    override suspend fun listEntries(path: String): AppResult<List<ExecutionFileEntry>> = result {
        access { channel, root ->
            channel.ls(resolve(channel, root, path)).filterIsInstance<ChannelSftp.LsEntry>()
                .filter { it.filename != "." && it.filename != ".." }.also { require(it.size <= 2000) { "目录条目超过 2000" } }
                .mapNotNull { item ->
                    // 单条目越界符号链接 / stat 权限不足不应中断整目录列举：跳过该条目。
                    try {
                        val target = resolve(channel, root, path.trimEnd('/') + "/" + item.filename)
                        val attributes = channel.stat(target)
                        ExecutionFileEntry(item.filename, attributes.isDir, attributes.size, attributes.mTime.toLong() * 1000)
                    } catch (e: IllegalArgumentException) { null }
                    catch (e: SftpException) { null }
                }.sortedWith(compareByDescending<ExecutionFileEntry> { it.directory }.thenBy { it.name.lowercase() })
        }
    }
    override suspend fun readRawBytes(path: String): AppResult<ByteArray> = result {
        access { channel, root -> bytes(channel, resolve(channel, root, path)) }
    }
    override suspend fun write(path: String, content: String): AppResult<Unit> = result {
        access { channel, root -> writeAtomic(channel, resolve(channel, root, path), content) }
    }
    override suspend fun editDetailed(path: String, oldText: String, newText: String): AppResult<WorkspaceEditOutcome> = result {
        require(oldText.isNotEmpty())
        access { channel, root ->
            val target = resolve(channel, root, path)
            val before = String(bytes(channel, target), Charsets.UTF_8)
            val index = before.indexOf(oldText)
            require(index >= 0 && before.indexOf(oldText, index + oldText.length) < 0) { "待替换文本必须唯一匹配" }
            writeAtomic(channel, target, before.replaceRange(index, index + oldText.length, newText))
            WorkspaceEditOutcome("exact", 1, null)
        }
    }
    override suspend fun previewOrNull(path: String) = read(path, 1, 80).getOrNull()
    override suspend fun fileSizeOrNull(path: String): Long? = result {
        access { channel, root -> channel.stat(resolve(channel, root, path)).size }
    }.getOrNull()
    override suspend fun delete(path: String): Boolean = result {
        access { channel, root ->
            try { channel.rm(resolve(channel, root, path, followFinalLink = false)); true }
            catch (e: SftpException) { if (e.id == ChannelSftp.SSH_FX_NO_SUCH_FILE) true else throw e }
        }
    }.getOrNull() ?: false
    companion object { private const val MAX_BYTES = 16 * 1024 * 1024 }
}
