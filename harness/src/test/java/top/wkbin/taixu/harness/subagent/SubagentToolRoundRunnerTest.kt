package top.wkbin.taixu.harness.subagent

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import top.wkbin.taixu.core.database.AppDatabase
import top.wkbin.taixu.core.database.HarnessEntryEntity
import top.wkbin.taixu.core.database.HarnessLaneEntity
import top.wkbin.taixu.core.database.HarnessOperationEntity
import top.wkbin.taixu.core.database.HarnessRuntimeRepository
import top.wkbin.taixu.core.database.HarnessUsageEntity
import top.wkbin.taixu.core.database.RoomHarnessRuntimeRepository
import top.wkbin.taixu.harness.ApiToolCallSpec
import top.wkbin.taixu.harness.HarnessMessage
import top.wkbin.taixu.harness.ModelConfig
import top.wkbin.taixu.harness.ToolCall
import top.wkbin.taixu.harness.ToolResult
import top.wkbin.taixu.harness.UserMessage
import top.wkbin.taixu.harness.events.HarnessEventBus
import top.wkbin.taixu.harness.operation.OperationCoordinator
import top.wkbin.taixu.harness.operation.OperationPhase

/** Real Room transactions verify lane evidence and commit failures at the production adapter. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SubagentToolRoundRunnerTest {
    private lateinit var database: AppDatabase
    private lateinit var repository: HarnessRuntimeRepository
    private val model = ModelConfig("test", "test", "test", "https://example.test", null)

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext<Context>(), AppDatabase::class.java,
        ).allowMainThreadQueries().build()
        repository = RoomHarnessRuntimeRepository(database.harnessRuntimeDao())
    }

    @After
    fun tearDown() { database.close() }

    private fun runner(
        operations: OperationCoordinator,
        execute: suspend (ToolCall) -> ToolResult,
    ) = SubagentToolRoundRunner(operations, Json) { call, _, _, _ -> execute(call) }

    private suspend fun SubagentToolRoundRunner.round(operationId: String, writePaths: List<String>? = null) {
        execute(listOf(ApiToolCallSpec("call", "write", """{"path":"a.txt","content":"hello"}""")),
            "s", "/workspace", model, operationId, 0, null, writePaths)
    }

    private suspend fun operations(repo: HarnessRuntimeRepository = repository): Pair<OperationCoordinator, String> {
        val operations = OperationCoordinator(repo, Json, HarnessEventBus())
        return operations to operations.acceptRun("s", UserMessage("user", 1, "write a file"), "child")
    }

    private suspend fun results() = repository.listEntries("s").mapNotNull {
        Json.decodeFromString<HarnessMessage>(it.payloadJson) as? ToolResult
    }

    private fun failCommit(phase: OperationPhase, failure: Throwable) = object : HarnessRuntimeRepository by repository {
        override suspend fun settleEffect(
            entry: HarnessEntryEntity?, usage: HarnessUsageEntity?,
            operation: HarnessOperationEntity, lane: HarnessLaneEntity,
        ) {
            if (operation.phase == phase.id) throw failure
            repository.settleEffect(entry, usage, operation, lane)
        }
    }

    @Test
    fun `write lease rejection commits failure and never invokes execution`() = runBlocking {
        val (operations, id) = operations()
        val runner = runner(operations) { error("must not write without lease") }
        runner.round(id, writePaths = emptyList())
        assertEquals(1, runner.toolCallCount)
        assertEquals(1, runner.blockedWrites.size)
        assertTrue(runner.failedWrites.isEmpty())
        assertFalse(results().single().success)
    }

    @Test
    fun `approval deferral survives result commit as parent handoff`() = runBlocking {
        val (operations, id) = operations()
        val runner = runner(operations) { call ->
            ToolResult("result", 2, call.id, false, "主会话需要审批", approvalDeferred = true)
        }
        runner.round(id)
        assertEquals("write", runner.pendingApprovals.single().toolName)
        assertTrue(runner.failedWrites.isEmpty())
        assertTrue(results().single().approvalDeferred)
    }

    @Test
    fun `execution exception is recorded as unresolved write failure`() = runBlocking {
        val (operations, id) = operations()
        val runner = runner(operations) { throw IllegalStateException("disk unavailable") }
        runner.round(id)
        assertEquals(1, runner.failedWrites.size)
        assertFalse(results().single().success)
        assertTrue(results().single().output.contains("disk unavailable"))
    }

    @Test
    fun `failed intent commit cannot execute or manufacture a tool result`() = runBlocking {
        val failure = IllegalStateException("intent commit failed")
        val (operations, id) = operations(failCommit(OperationPhase.TOOL_INTENT, failure))
        val runner = runner(operations) { error("must not execute") }
        assertSame(failure, runCatching { runner.round(id) }.exceptionOrNull())
        assertTrue(results().isEmpty())
        assertTrue(runner.failedWrites.isEmpty())
    }

    @Test
    fun `failed result commit cannot report completion evidence`() = runBlocking {
        val failure = IllegalStateException("result commit failed")
        val (operations, id) = operations(failCommit(OperationPhase.TOOL_SETTLED, failure))
        var executions = 0
        val runner = runner(operations) { call ->
            executions++
            ToolResult("result", 2, call.id, false, "needs approval", approvalDeferred = true)
        }
        assertSame(failure, runCatching { runner.round(id) }.exceptionOrNull())
        assertEquals(1, executions)
        assertTrue(results().isEmpty())
        assertTrue(runner.pendingApprovals.isEmpty())
        assertEquals(OperationPhase.TOOL_INTENT.id, repository.findOperation(id)!!.phase)
    }

    @Test
    fun `subsequent successful write clears only its own failure`() = runBlocking {
        val (operations, id) = operations()
        var attempts = 0
        val runner = runner(operations) { call ->
            attempts++
            ToolResult("result-$attempts", attempts.toLong(), call.id, attempts > 1, "attempt $attempts")
        }
        runner.round(id)
        assertEquals(1, runner.failedWrites.size)
        runner.round(id)
        assertTrue(runner.failedWrites.isEmpty())
        assertEquals(2, results().size)
    }
}
