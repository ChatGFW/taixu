package top.wkbin.taixu.harness.text

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.atomic.AtomicReference

class HashlineEditStatsTest {
    @Before
    fun setUp() {
        HashlineEditStats.resetForTests()
    }

    @After
    fun tearDown() {
        HashlineEditStats.resetForTests()
    }

    @Test
    fun codecRoundTrip() {
        val snap = mapOf(
            "ops_success" to 3,
            "ops_stale" to 1,
            "ops_other_failure" to 0,
            "ops_disabled" to 2,
            "legacy_success" to 4,
            "legacy_failure" to 5,
            "legacy_disabled" to 0,
        )
        assertEquals(snap, HashlineEditStatsCodec.decode(HashlineEditStatsCodec.encode(snap)))
    }

    @Test
    fun codecBlankAndCorruptYieldZeros() {
        val zeros = HashlineEditStatsCodec.decode("")
        assertEquals(0, zeros["ops_success"])
        assertEquals(0, HashlineEditStatsCodec.decode("{not-json")["legacy_failure"])
    }

    @Test
    fun bindHydratesOnceAndFlushPersists() {
        val last = AtomicReference<Map<String, Int>?>(null)
        HashlineEditStats.bind(
            mapOf("ops_success" to 7, "legacy_failure" to 2),
        ) { last.set(it) }
        // 二次 bind 不覆盖已水合计数
        HashlineEditStats.bind(mapOf("ops_success" to 99)) { last.set(it) }
        assertEquals(7, HashlineEditStats.snapshot()["ops_success"])
        assertEquals(2, HashlineEditStats.snapshot()["legacy_failure"])

        HashlineEditStats.recordOpsSuccess()
        // 未满 10 次不自动 flush；手动 flush
        assertEquals(null, last.get())
        HashlineEditStats.flush()
        assertEquals(8, last.get()?.get("ops_success"))
    }

    @Test
    fun autoFlushEveryTenBumps() {
        val last = AtomicReference<Map<String, Int>?>(null)
        HashlineEditStats.bind(emptyMap()) { last.set(it) }
        repeat(9) { HashlineEditStats.recordOpsSuccess() }
        assertEquals(null, last.get())
        HashlineEditStats.recordOpsSuccess()
        assertEquals(10, last.get()?.get("ops_success"))
    }

    @Test
    fun suggestDisableLegacyHeuristic() {
        assertFalse(HashlineEditStats.shouldSuggestDisableLegacy())
        repeat(20) { HashlineEditStats.recordOpsSuccess() }
        repeat(5) { HashlineEditStats.recordLegacyFailure() }
        assertTrue(HashlineEditStats.shouldSuggestDisableLegacy())
        // 过期过多则不建议
        repeat(10) { HashlineEditStats.recordOpsStale() }
        assertFalse(HashlineEditStats.shouldSuggestDisableLegacy())
    }

    @Test
    fun suggestEnableLegacyHeuristic() {
        assertFalse(HashlineEditStats.shouldSuggestEnableLegacy())
        repeat(8) { HashlineEditStats.recordLegacyDisabled() }
        assertTrue(HashlineEditStats.shouldSuggestEnableLegacy())
        // ops 已明显跑通则不再提示开 legacy
        repeat(11) { HashlineEditStats.recordOpsSuccess() }
        assertFalse(HashlineEditStats.shouldSuggestEnableLegacy())
    }
}
