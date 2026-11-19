package top.wkbin.taixu.runtime.shell

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 前台命令的收尾链：SIGTERM → SIGQUIT（tracer 自己收掉 tracee）→ 强杀。
 *
 * 只用假进程与假 reaper，不触碰 /proc 与 android.os.Process。
 */
class ShellProcessOwnerTest {

    @Test fun sigquitPrecedesForcedKillWhenSigtermIsIgnored() {
        // PRoot tracer 不响应 SIGTERM：SIGQUIT 必须在 SIGKILL 之前投递。
        val process = FakeSessionProcess().apply { gracefulExit = false }
        val reaper = RecordingReaper()
        val owned = ShellProcessOwner(process, reaper, childPid = ROOT_PID, forget = {})
        process.beforeForce = { assertEquals(listOf(ROOT_PID), reaper.quits) }

        owned.stopProcess()

        assertEquals(listOf(ROOT_PID), reaper.quits)
        assertEquals(1, process.forces.get())
        assertFalse(process.isAlive)
    }

    @Test fun gracefulExitSendsNoSigquitAndNoForcedKill() {
        val process = FakeSessionProcess()
        val reaper = RecordingReaper()
        val owned = ShellProcessOwner(process, reaper, childPid = ROOT_PID, forget = {})

        owned.stopProcess()

        assertTrue(reaper.quits.isEmpty())
        assertEquals(0, process.forces.get())
        assertFalse(process.isAlive)
    }

    @Test fun repeatedStopIsIdempotent() {
        val process = FakeSessionProcess()
        val reaper = RecordingReaper()
        val owned = ShellProcessOwner(process, reaper, childPid = ROOT_PID, forget = {})

        owned.stopProcess()
        owned.stopProcess()

        assertEquals(1, process.destroys.get())
        assertTrue(reaper.quits.isEmpty())
    }

    private class RecordingReaper : ProcessTreeReaper {
        val quits = mutableListOf<Long>()
        override fun forkTid(): Int? = null
        override fun childOfThread(tid: Int): Long? = null
        override fun descendantsOf(rootPid: Long): List<Long> = emptyList()
        override fun signalQuit(pid: Long): Boolean { quits += pid; return true }
        override fun reap(rootPid: Long): Int = 1
        override fun reapDetached(descendants: List<Long>): Int = 0
    }

    private companion object {
        const val ROOT_PID = 4242L
    }
}
