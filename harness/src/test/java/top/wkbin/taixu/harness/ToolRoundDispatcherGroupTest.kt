package top.wkbin.taixu.harness

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 断组语义：并发组成员必须两两可并行。任一新成员与组内已有成员冲突时，
 * 它必须等前面整组落库后才启动——这是子智能体写路径隔离能安全放开并发的关键。
 */
class ToolRoundDispatcherGroupTest {

    private class Recorder {
        val active = AtomicInteger(0)
        val maxActive = AtomicInteger(0)
        val startedAt = ConcurrentHashMap<Int, Long>()
        val finishedAt = ConcurrentHashMap<Int, Long>()

        fun enter(item: Int) {
            startedAt[item] = System.currentTimeMillis()
            val now = active.incrementAndGet()
            synchronized(maxActive) { if (now > maxActive.get()) maxActive.set(now) }
        }

        fun exit(item: Int) {
            finishedAt[item] = System.currentTimeMillis()
            active.decrementAndGet()
        }
    }

    private fun runDispatch(
        items: List<Int>,
        parallelism: Int = ToolRoundDispatcher.DEFAULT_PARALLELISM,
        conflicts: (Int, Int) -> Boolean,
    ): Recorder {
        val recorder = Recorder()
        val dispatcher = ToolRoundDispatcher()
        runBlocking {
            dispatcher.dispatch(
                items = items,
                parallelism = parallelism,
                isParallelSafe = { true },
                conflicts = conflicts,
            ) { item, _ ->
                recorder.enter(item)
                try {
                    delay(150)
                } finally {
                    recorder.exit(item)
                }
            }
        }
        return recorder
    }

    @Test
    fun `member conflicting with the group waits for the whole group`() {
        val recorder = runDispatch(listOf(0, 1, 2)) { left, right -> left == 2 || right == 2 }

        assertEquals("0 与 1 应并发，2 必须断组", 2, recorder.maxActive.get())
        val groupFinished = maxOf(recorder.finishedAt.getValue(0), recorder.finishedAt.getValue(1))
        assertTrue(
            "断组成员必须在前一组全部结束后才启动",
            recorder.startedAt.getValue(2) >= groupFinished,
        )
    }

    @Test
    fun `members without conflicts share one group`() {
        val recorder = runDispatch(listOf(0, 1, 2)) { _, _ -> false }

        assertEquals(3, recorder.maxActive.get())
    }

    @Test
    fun `permit limit still caps a conflict free group`() {
        val recorder = runDispatch(listOf(0, 1, 2), parallelism = 1) { _, _ -> false }

        assertEquals(1, recorder.maxActive.get())
    }
}
