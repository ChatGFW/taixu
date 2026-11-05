package top.wkbin.taixu.harness

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class CurrentTurnToolResultBudgetTest {

    private fun result(id: String, createdAt: Long, output: String) =
        ToolResult(id, createdAt, "call-$id", true, output)

    private val big = "line\n".repeat(4_000) // 20k chars, above the head/tail keep window

    @Test
    fun `leaves the turn untouched while it fits the budget`() {
        val messages = listOf(
            UserMessage("u", 1, "go"),
            ToolCall("c1", 2, HarnessTool.BASE, buildJsonObject {}),
            result("r1", 3, big),
            ToolCall("c2", 4, HarnessTool.BASE, buildJsonObject {}),
            result("r2", 5, big),
        )

        val out = CurrentTurnToolResultBudget.apply(messages, emptyMap(), budgetTokens = 1_000_000)

        assertSame(messages, out)
    }

    @Test
    fun `compacts the oldest oversized current turn results and keeps the recent ones intact`() {
        val messages = buildList {
            add(UserMessage("u", 1, "go"))
            repeat(6) { i ->
                add(ToolCall("c$i", 2L + i * 2, HarnessTool.BASE, buildJsonObject {}))
                add(result("r$i", 3L + i * 2, big))
            }
        }

        val out = CurrentTurnToolResultBudget.apply(messages, emptyMap(), budgetTokens = 2_000)

        val results = out.filterIsInstance<ToolResult>()
        assertEquals(6, results.size)
        assertTrue("oldest result should be compacted", results[0].output.contains("history_read(message_id=\"r0\")"))
        assertTrue("next oldest should be compacted", results[1].output.contains("history_read(message_id=\"r1\")"))
        assertEquals("recent results stay verbatim", big, results[4].output)
        assertEquals("recent results stay verbatim", big, results[5].output)
    }

    @Test
    fun `never touches results from earlier turns`() {
        val messages = listOf(
            UserMessage("u1", 1, "first"),
            ToolCall("old-call", 2, HarnessTool.BASE, buildJsonObject {}),
            result("old", 3, big),
            UserMessage("u2", 4, "second"),
            ToolCall("c1", 5, HarnessTool.BASE, buildJsonObject {}),
            result("r1", 6, big),
            ToolCall("c2", 7, HarnessTool.BASE, buildJsonObject {}),
            result("r2", 8, big),
        )

        val out = CurrentTurnToolResultBudget.apply(messages, emptyMap(), budgetTokens = 10)

        assertEquals(big, out.filterIsInstance<ToolResult>().first { it.id == "old" }.output)
    }

    @Test
    fun `uses the tool call details to pick a tool aware compaction`() {
        val messages = buildList {
            add(UserMessage("u", 1, "go"))
            repeat(5) { i ->
                add(ToolCall("c$i", 2L + i * 2, HarnessTool.READ, buildJsonObject {}))
                add(result("r$i", 3L + i * 2, big))
            }
        }
        val details: Map<String, Pair<String, JsonObject>> =
            (0 until 5).associate { "c$it" to ("read" to buildJsonObject {}) }

        val out = CurrentTurnToolResultBudget.apply(messages, details, budgetTokens = 10)

        assertTrue(out.filterIsInstance<ToolResult>().first { it.id == "r0" }.output.contains("【当前轮执行结果"))
    }
}
