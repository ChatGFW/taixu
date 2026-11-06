package top.wkbin.taixu.harness

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SubagentTimeBudgetTest {

    private val deadline = 1_000_000L

    @Test
    fun `wraps up only inside the closing window`() {
        assertFalse(SubagentTimeBudget.shouldWrapUp(null, 0))
        assertFalse(SubagentTimeBudget.shouldWrapUp(deadline, deadline - 5 * 60_000))
        assertTrue(SubagentTimeBudget.shouldWrapUp(deadline, deadline - 60_000))
        assertTrue(SubagentTimeBudget.shouldWrapUp(deadline, deadline))
    }

    @Test
    fun `is exhausted only at or after the deadline`() {
        assertFalse(SubagentTimeBudget.exhausted(null, Long.MAX_VALUE))
        assertFalse(SubagentTimeBudget.exhausted(deadline, deadline - 1))
        assertTrue(SubagentTimeBudget.exhausted(deadline, deadline))
    }

    @Test
    fun `exhausted summary keeps the last stage output`() {
        val summary = SubagentTimeBudget.exhaustedSummary("已改完 3 个文件")

        assertTrue(summary.contains("时间预算已用尽"))
        assertTrue(summary.contains("已改完 3 个文件"))
    }

    @Test
    fun `exhausted summary tolerates an empty last output`() {
        val summary = SubagentTimeBudget.exhaustedSummary("")

        assertTrue(summary.contains("时间预算已用尽"))
        assertFalse(summary.contains("最后阶段输出"))
    }
}
