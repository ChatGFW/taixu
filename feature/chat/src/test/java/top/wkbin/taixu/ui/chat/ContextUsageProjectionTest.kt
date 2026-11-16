package top.wkbin.taixu.ui.chat

import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.*
import org.junit.Test
import top.wkbin.taixu.harness.AssistantText
import top.wkbin.taixu.harness.ContextWindowPolicy
import top.wkbin.taixu.harness.compaction.CompactedContext

class ContextUsageProjectionTest {
    private fun message(id: String, text: String = "", reasoning: String? = null) =
        AssistantText(id = id, createdAt = 1L, text = text, reasoning = reasoning)

    @Test fun `subscriber restarts retain summary and session switches clear it`() = runBlocking {
        val session = MutableStateFlow("first")
        val context = CompactedContext(summary = "folded", messages = listOf(message("retained")))
        val projection = retainUsageProjection(session) { flowOf(context) }
        assertEquals(listOf(null, context), projection.take(2).toList())
        assertEquals(context, projection.take(1).toList().single())
        session.value = "second"
        assertNull(projection.take(1).toList().single())
    }

    @Test fun `final assistant stays present until its persisted projection catches up`() {
        val previous = message("previous")
        val final = message("live", "complete answer").copy(totalMs = 100L)
        val context = CompactedContext(messages = listOf(previous))
        assertEquals(listOf(previous, final), contextUsageMessages(context, listOf(previous, final), true))
        assertEquals(listOf(final), contextUsageMessages(CompactedContext(messages = listOf(message("live"))), listOf(final), false))
    }

    @Test fun `commits and branch switches refresh without compaction`() = runBlocking {
        val leaves = MutableStateFlow<String?>("first")
        val revisions = MutableStateFlow(0L)
        var stored = CompactedContext(messages = listOf(message("first")))
        var reads = 0
        val values = mutableListOf<CompactedContext>()
        val job = launch(start = CoroutineStart.UNDISPATCHED) {
            observeUsageHistory(leaves, revisions) { reads++; stored }.collect { values += it }
        }
        suspend fun drain() { repeat(10) { yield() } }
        drain()
        stored = CompactedContext(messages = listOf(message("first"), message("second")))
        leaves.value = "second"
        drain()
        assertEquals(listOf("first", "second"), values.last().messages.map { it.id })
        stored = CompactedContext(messages = listOf(message("other-branch")))
        leaves.value = "other-branch"
        drain()
        assertEquals(listOf("other-branch"), values.last().messages.map { it.id })
        stored = CompactedContext(summary = "folded", messages = emptyList())
        revisions.value++
        drain()
        assertEquals("folded", values.last().summary)
        assertEquals(4, reads)
        job.cancel()
    }

    @Test fun `reasoning only stream changes increase usage without restoring folded history`() {
        val retained = message("retained", "retained")
        val context = CompactedContext(summary = "summary", messages = listOf(retained))
        val first = listOf(message("folded", "old".repeat(1000)), retained, message("live"))
        val next = first.dropLast(1) + message("live", reasoning = "thinking ".repeat(100))
        val before = contextUsageMessages(context, first, true)
        val after = contextUsageMessages(context, next, true)
        assertEquals(listOf("retained", "live"), after.map { it.id })
        assertTrue(ContextWindowPolicy.estimateEffectiveUsage(after, 128000, 0, false).totalTokens >
            ContextWindowPolicy.estimateEffectiveUsage(before, 128000, 0, false).totalTokens)
    }

    @Test fun `persisted assistant is replaced not counted twice and retry can shrink`() {
        val context = CompactedContext(messages = listOf(message("live", "long text")))
        val result = contextUsageMessages(context, listOf(message("live", "x")), true)
        assertEquals(1, result.size)
        assertEquals("x", (result.single() as AssistantText).text)
    }

    @Test fun `idle and unrelated branch transcripts cannot extend projection`() {
        val context = CompactedContext(messages = listOf(message("current")))
        assertEquals(context.messages, contextUsageMessages(context,
            listOf(message("other"), message("live")), true))
        assertEquals(context.messages, contextUsageMessages(context,
            listOf(message("current"), message("live")), false))
    }
}
