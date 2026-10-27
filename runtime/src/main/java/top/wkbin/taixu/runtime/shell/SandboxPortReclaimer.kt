package top.wkbin.taixu.runtime.shell

import android.os.Process as AndroidProcess
import java.io.File
import java.nio.file.Files

/**
 * 回环端口孤儿回收。
 *
 * 网关真正的 LISTEN 者往往不是 PRoot tracer 本身，而是它的孙进程（`sh` 下的 node/python）。tracer 被
 * SIGKILL（进程卡死、App 被系统回收、旧版本收尾缺失）时来不及带走它们，它们就被 reparent 到 init：
 * **端口还占着，本进程的进程表里却查无此人**。此后停止看起来成功了，下一次启动却永远面对
 * 「服务端口已被占用，拒绝连接未知进程」。频繁启停的插件因此再难起来。
 *
 * 这里按「端口事实 + 归属事实」精确回收，而不是见孤儿就杀：
 * 1. 从 `/proc/net/tcp{,6}` 找出 [port] 上 LISTEN 的 socket inode，再经 `/proc/<pid>/fd` 反查到持有点
 *    —— 只有同 uid 的 fd 可读，因此他应用/系统的监听者天然落选，仍由调用方按「端口已被占用」拒绝；
 * 2. 沿持有点向上找这棵进程树的根，只有**全程都落在本运行时目录内、最后停在 init 或已消失父进程**
 *    才算被遗弃；途中碰到宿主/系统进程或在册会话根就放弃 —— 那棵树还有主人（终端里手起的服务、
 *    工具中心正在跑的网关都不会被误伤）。
 *
 * 读不到 procfs 时一律不杀，只报告 0；调用方退回原有的「拒绝启动」语义。
 */
class SandboxPortReclaimer(
    private val ownedRoots: List<File>,
    private val proc: SandboxProcFS = LinuxSandboxProcFS,
    private val signal: (Long) -> Boolean = ::killOwnedProcess,
) {
    constructor(ownedRoot: File) : this(listOf(ownedRoot))

    private val ownedPrefixes: List<String> by lazy {
        ownedRoots.mapNotNull { root -> runCatching { root.canonicalFile.absolutePath }.getOrNull() }
            .map { if (it.endsWith(File.separator)) it else it + File.separator }
    }

    /**
     * 回收 [port] 上被遗弃的本运行时监听者及其整棵子树，返回成功收到信号的进程数。
     *
     * [protectedRoots] 是调用方仍在册的会话根 pid；命中即整棵树放过。
     */
    fun reclaim(port: Int, protectedRoots: Set<Long> = emptySet()): Int {
        val table = proc.entries()
        if (table.isEmpty() || ownedPrefixes.isEmpty()) return 0
        val abandoned = abandonedRoots(port, table, protectedRoots)
        if (abandoned.isEmpty()) return 0
        val childrenOf = childrenByParent(table)
        return abandoned.flatMap { root -> listOf(root) + descendantsOf(childrenOf, root) }
            .distinct()
            .count { signal(it) }
    }

    private fun abandonedRoots(
        port: Int,
        table: Map<Long, SandboxProcEntry>,
        protectedRoots: Set<Long>,
    ): List<Long> {
        val inodes = proc.listeningSocketInodes(port)
        if (inodes.isEmpty()) return emptyList()
        return table.keys
            .filter { pid -> isOwned(pid, table) && proc.socketInodes(pid).any(inodes::contains) }
            .mapNotNull { listener -> abandonedRoot(listener, table, protectedRoots) }
            .distinct()
    }

    /**
     * 从 [listener] 向上走到这棵树的根；只有「全程都在本运行时目录内、最后停在 init 或已消失父进程」
     * 才算被遗弃。碰到在册会话根或宿主/系统进程就返回 null：那棵树还有主人。
     */
    private fun abandonedRoot(listener: Long, table: Map<Long, SandboxProcEntry>, protectedRoots: Set<Long>): Long? {
        if (listener in protectedRoots || !isOwned(listener, table)) return null
        var current = listener
        while (true) {
            val parent = table[current]?.parent
            if (parent == null || parent <= INIT_PID || parent !in table) return current
            if (parent in protectedRoots || !isOwned(parent, table)) return null
            current = parent
        }
    }

    private fun childrenByParent(table: Map<Long, SandboxProcEntry>): Map<Long, List<Long>> = table.entries
        .filter { it.value.parent in table.keys }
        .groupBy({ checkNotNull(it.value.parent) }, { it.key })

    private fun descendantsOf(childrenOf: Map<Long, List<Long>>, rootPid: Long): List<Long> {
        val found = mutableListOf<Long>()
        val pending = ArrayDeque(childrenOf[rootPid].orEmpty())
        while (pending.isNotEmpty()) {
            val pid = pending.removeFirst()
            found += pid
            pending += childrenOf[pid].orEmpty()
        }
        return found
    }

    private fun isOwned(pid: Long, table: Map<Long, SandboxProcEntry>): Boolean = isOwned(table[pid]?.executable)

    private fun isOwned(executable: String?): Boolean {
        val path = executable ?: return false
        val normalized = path.removeSuffix(DELETED_SUFFIX)
        return ownedPrefixes.any { normalized.startsWith(it) }
    }

    private companion object {
        const val INIT_PID = 1L
        const val DELETED_SUFFIX = " (deleted)"
    }
}

/** pid 归属信息；抽出来是为了让单元测试不必真的读 procfs。 */
data class SandboxProcEntry(
    val parent: Long?,
    val executable: String?,
)

interface SandboxProcFS {
    /** 当前可见的进程表；读不到的字段为 null（权限受限或进程已退出）。 */
    fun entries(): Map<Long, SandboxProcEntry>

    /** [port] 上处于 LISTEN 的套接字 inode 集合；网络表不可读时为空。 */
    fun listeningSocketInodes(port: Int): Set<Long>

    /** [pid] 打开的套接字 inode 集合；只有同 uid 的进程可读。 */
    fun socketInodes(pid: Long): Set<Long>
}

/** 真机实现：只读 `/proc` 下同 uid 可见的条目，读不到就当作没有。 */
object LinuxSandboxProcFS : SandboxProcFS {
    override fun entries(): Map<Long, SandboxProcEntry> = runCatching {
        File(PROC).listFiles().orEmpty().mapNotNull { entry ->
            val pid = entry.name.toLongOrNull() ?: return@mapNotNull null
            val status = runCatching { File(entry, "status").readText() }.getOrNull() ?: return@mapNotNull null
            val parent = status.lineSequence()
                .firstOrNull { it.startsWith("PPid:") }?.substringAfter('\t')?.trim()?.toLong()
            pid to SandboxProcEntry(parent, executableOf(pid))
        }.toMap()
    }.getOrDefault(emptyMap())

    override fun listeningSocketInodes(port: Int): Set<Long> = runCatching {
        val inodes = mutableSetOf<Long>()
        SOCKET_TABLES.forEach { path ->
            val table = File(path).takeIf { it.canRead() } ?: return@forEach
            table.forEachLine { line ->
                val fields = line.trim().split(WHITESPACE)
                if (fields.size < INODE_COLUMN || fields[STATE_COLUMN] != LISTEN_STATE) return@forEachLine
                if (fields[LOCAL_COLUMN].substringAfterLast(':').toIntOrNull(16) != port) return@forEachLine
                fields[INODE_COLUMN].toLongOrNull()?.let(inodes::add)
            }
        }
        inodes
    }.getOrDefault(emptySet())

    override fun socketInodes(pid: Long): Set<Long> = runCatching {
        File(PROC, "$pid/fd").listFiles().orEmpty().mapNotNullTo(mutableSetOf()) { fd ->
            val target = runCatching { Files.readSymbolicLink(fd.toPath()).toString() }.getOrNull()
                ?: return@mapNotNullTo null
            target.removePrefix(SOCKET_PREFIX).removeSuffix(SOCKET_SUFFIX)
                .takeIf { target.startsWith(SOCKET_PREFIX) && target.endsWith(SOCKET_SUFFIX) }?.toLongOrNull()
        }
    }.getOrDefault(emptySet())

    private fun executableOf(pid: Long): String? =
        runCatching { File(PROC, "$pid/exe").canonicalFile.absolutePath }.getOrNull()

    private const val PROC = "/proc"
    private const val SOCKET_PREFIX = "socket:["
    private const val SOCKET_SUFFIX = "]"
    private const val LISTEN_STATE = "0A"

    // /proc/net/tcp{,6} 的列序：sl | local_address | rem_address | st | ... | inode
    private const val LOCAL_COLUMN = 1
    private const val STATE_COLUMN = 3
    private const val INODE_COLUMN = 9
    private val SOCKET_TABLES = listOf("/proc/net/tcp", "/proc/net/tcp6")
    private val WHITESPACE = Regex("\\s+")
}

/** 只能信号同 uid 进程；他 uid 的失败会被静默计为「没收到」。 */
private fun killOwnedProcess(pid: Long): Boolean = runCatching {
    AndroidProcess.killProcess(pid.toInt())
    true
}.getOrDefault(false)
