package top.wkbin.taixu.runtime.shell

import java.io.File
import java.nio.file.Files
import org.junit.Assert.*
import org.junit.Test

class SandboxPortReclaimerTest {
    private val ownedRoot: File = Files.createTempDirectory("taixu-reclaim").toFile()
    private val guestBinary = File(ownedRoot, "distros/ubuntu/rootfs/usr/bin/node").absolutePath
    private val guestShell = File(ownedRoot, "distros/ubuntu/rootfs/bin/sh").absolutePath
    private val tracerBinary = File(ownedRoot, "lib/arm64/libproot.so").absolutePath
    private val hostBinary = "/system/bin/app_process64"

    private class FakeProcFS(
        val entries: Map<Long, SandboxProcEntry> = emptyMap(),
        val listening: Map<Int, Set<Long>> = emptyMap(),
        val sockets: Map<Long, Set<Long>> = emptyMap(),
    ) : SandboxProcFS {
        override fun entries() = entries
        override fun listeningSocketInodes(port: Int) = listening[port].orEmpty()
        override fun socketInodes(pid: Long) = sockets[pid].orEmpty()
    }

    private fun reclaimer(fs: FakeProcFS, killed: MutableList<Long> = mutableListOf()) =
        SandboxPortReclaimer(listOf(ownedRoot), fs) { pid -> killed += pid; true }

    /** 上一代 tracer 已消失：node 监听者连同整棵树都被收回。 */
    @Test fun abandonedListenerOnThePortIsReclaimedWithItsTree() {
        val killed = mutableListOf<Long>()
        val fs = FakeProcFS(
            entries = mutableMapOf(
                40L to SandboxProcEntry(1L, tracerBinary),
                41L to SandboxProcEntry(40L, guestShell),
                42L to SandboxProcEntry(41L, guestBinary),
            ),
            listening = mutableMapOf(3065 to setOf(900L)),
            sockets = mutableMapOf(42L to setOf(900L)),
        )
        assertEquals(3, reclaimer(fs, killed).reclaim(3065))
        assertEquals(listOf(40L, 41L, 42L), killed)
    }

    /** 终端里手起的服务挂在活的 tracer 之下：整棵树都有主人，绝不误伤。 */
    @Test fun listenerStillAnchoredToAHostOwnedTreeIsNeverTouched() {
        val killed = mutableListOf<Long>()
        val fs = FakeProcFS(
            entries = mutableMapOf(
                7L to SandboxProcEntry(1L, hostBinary),
                40L to SandboxProcEntry(7L, tracerBinary),
                41L to SandboxProcEntry(40L, guestBinary),
            ),
            listening = mutableMapOf(8080 to setOf(900L)),
            sockets = mutableMapOf(41L to setOf(900L)),
        )
        assertEquals(0, reclaimer(fs, killed).reclaim(8080))
        assertTrue(killed.isEmpty())
    }

    /** 在册会话根（工具中心正在跑的网关）列为保护名单，携带它的整棵树放过。 */
    @Test fun treeCarryingARegisteredSessionRootIsLeftAlone() {
        val killed = mutableListOf<Long>()
        val fs = FakeProcFS(
            entries = mutableMapOf(
                40L to SandboxProcEntry(1L, tracerBinary),
                41L to SandboxProcEntry(40L, guestBinary),
            ),
            listening = mutableMapOf(3065 to setOf(900L)),
            sockets = mutableMapOf(41L to setOf(900L)),
        )
        assertEquals(0, reclaimer(fs, killed).reclaim(3065, protectedRoots = setOf(40L)))
        assertTrue(killed.isEmpty())
    }

    /** 端口上没有本运行时的监听者（他应用占用），或读不到网络表：只报告 0。 */
    @Test fun foreignOrUnknownListenersAreNeverKilled() {
        val killed = mutableListOf<Long>()
        val fs = FakeProcFS(
            entries = mutableMapOf(42L to SandboxProcEntry(1L, guestBinary)),
            listening = mutableMapOf(3065 to setOf(900L)),
            sockets = emptyMap<Long, Set<Long>>(),
        )
        assertEquals(0, reclaimer(fs, killed).reclaim(3065))
        assertTrue(killed.isEmpty())

        val blind = FakeProcFS(
            entries = mutableMapOf(42L to SandboxProcEntry(1L, guestBinary)),
            listening = emptyMap<Int, Set<Long>>(),
            sockets = mutableMapOf(42L to setOf(900L)),
        )
        assertEquals(0, reclaimer(blind, killed).reclaim(3065))
        assertTrue(killed.isEmpty())
    }

    /** 监听者本身仍是宿主直接子进程（在册/竞态）：不当作遗弃。 */
    @Test fun listenerAttachedToTheHostProcessIsNotAbandoned() {
        val killed = mutableListOf<Long>()
        val fs = FakeProcFS(
            entries = mutableMapOf(
                7L to SandboxProcEntry(1L, hostBinary),
                42L to SandboxProcEntry(7L, guestBinary),
            ),
            listening = mutableMapOf(3065 to setOf(900L)),
            sockets = mutableMapOf(42L to setOf(900L)),
        )
        assertEquals(0, reclaimer(fs, killed).reclaim(3065))
        assertTrue(killed.isEmpty())
    }
}
