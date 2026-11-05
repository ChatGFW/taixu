package top.wkbin.taixu.harness.queue

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import top.wkbin.taixu.core.common.logging.AppLogger
import top.wkbin.taixu.core.common.logging.SensitiveDataRedactor
import top.wkbin.taixu.core.database.*
import top.wkbin.taixu.core.database.task.AgentTaskEntity
import top.wkbin.taixu.harness.PendingMessage
import top.wkbin.taixu.harness.session.SessionTreeStore

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class QueuedPromptEditingTest {
    private lateinit var db: AppDatabase
    private lateinit var repo: HarnessRuntimeRepository
    private lateinit var store: SessionTreeStore
    private lateinit var queues: PromptQueueManager

    @Before fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        repo = RoomHarnessRuntimeRepository(db.harnessRuntimeDao())
        store = SessionTreeStore(repo, Json, AppLogger(context, SensitiveDataRedactor { it }))
        queues = PromptQueueManager(repo, Json, store)
    }
    @After fun close() = db.close()
    private suspend fun enqueue(queue: PromptQueue = PromptQueue.NEXT_RUN, task: Boolean = true, at: Long = 1): String {
        if (task) db.agentTaskDao().upsertTask(AgentTaskEntity("task-$at", "s", "original", "original", "QUEUED", at, at))
        return queues.enqueue("s", queue, PendingMessage("original-$at", listOf("image-$at"), at, if (task) "task-$at" else null, initiatorId = "owner"))
    }
    private suspend fun activeOperation() {
        val lane = repo.ensureLane("s", "main")
        db.harnessRuntimeDao().upsertLane(lane.copy(currentOperationId = "op"))
    }
    private suspend fun finishOperation() {
        val lane = repo.findLane("s", "main")!!
        repo.finishOperation(HarnessLaneResultEntity("s", "main", "op", "completed", lane.leafId, completedAt = 10), lane.copy(currentOperationId = null))
    }

    @Test fun `save preserves order identity attachments provenance and queued task`() = runBlocking {
        val first = enqueue()
        val second = enqueue(at = 2)
        val before = queues.listAll("s").first().message
        assertNotNull(queues.beginEdit("s", first, "lease"))
        assertNull(queues.nextRunOrPromotedSteer("s"))
        assertTrue(queues.saveEdit("s", first, "lease", "  changed\nsecond line  "))
        val items = queues.listAll("s")
        assertEquals(listOf(first, second), items.map { it.id })
        assertEquals(before.copy(text = "changed\nsecond line"), items.first().message)
        assertFalse(items.first().isEditing)
        val task = db.agentTaskDao().getTaskById("task-1")!!
        assertEquals("changed", task.title)
        assertEquals("changed\nsecond line", task.description)
        assertEquals("QUEUED", task.status)
        assertEquals(1L, task.createdAt)
    }

    @Test fun `cancel and process restart restore original committed message`() = runBlocking {
        val id = enqueue()
        queues.beginEdit("s", id, "one")
        queues.cancelEdit("s", id, "wrong")
        assertTrue(queues.listAll("s").single().isEditing)
        queues.cancelEdit("s", id, "one")
        assertEquals("original-1", queues.first("s", PromptQueue.NEXT_RUN)!!.second.text)
        queues.beginEdit("s", id, "two")
        val restarted = PromptQueueManager(repo, Json, store)
        assertFalse(restarted.listAll("s").single().isEditing)
        assertFalse(queues.saveEdit("s", id, "two", "stale draft"))
        assertEquals("original-1", restarted.first("s", PromptQueue.NEXT_RUN)!!.second.text)
    }

    @Test fun `edited head blocks later messages until released`() = runBlocking {
        activeOperation()
        val first = enqueue(PromptQueue.STEER, false)
        enqueue(PromptQueue.STEER, false, 2)
        queues.beginEdit("s", first, "hold")
        assertTrue(queues.consume("s", PromptQueue.STEER).isEmpty())
        assertTrue(repo.listEntries("s").isEmpty())
        queues.cancelEdit("s", first, "hold")
        assertEquals(listOf("original-1", "original-2"), queues.consume("s", PromptQueue.STEER).map { it.text })
    }

    @Test fun `operation finishing retains held steer and follow up as next run`() = runBlocking {
        for (queue in listOf(PromptQueue.STEER, PromptQueue.FOLLOW_UP)) {
            activeOperation()
            val id = enqueue(queue, false)
            val later = enqueue(queue, false, 2)
            queues.beginEdit("s", id, "hold")
            finishOperation()
            assertEquals(listOf(id, later), queues.listAll("s").map { it.id })
            assertTrue(queues.listAll("s").first().isEditing)
            assertNull(queues.nextRunOrPromotedSteer("s"))
            queues.cancelEdit("s", id, "hold")
            assertEquals(id, queues.nextRunOrPromotedSteer("s")!!.first)
            queues.clear("s", PromptQueue.NEXT_RUN)
        }
    }

    @Test fun `deleted item cannot be resurrected and wrong session cannot edit`() = runBlocking {
        val id = enqueue()
        assertNull(queues.beginEdit("other", id, "bad"))
        queues.beginEdit("s", id, "held")
        assertNull(queues.beginEdit("s", id, "second"))
        assertFalse(queues.saveEdit("other", id, "held", "wrong session"))
        queues.clear("s", PromptQueue.NEXT_RUN)
        assertFalse(queues.saveEdit("s", id, "held", "resurrect"))
        queues.cancelEdit("s", id, "held")
        assertTrue(queues.listAll("s").isEmpty())
    }

    @Test fun `changed task state rejects edit without changing queue or task`() = runBlocking {
        val id = enqueue()
        queues.beginEdit("s", id, "hold")
        val task = db.agentTaskDao().getTaskById("task-1")!!
        db.agentTaskDao().upsertTask(task.copy(status = "RUNNING"))
        try { queues.saveEdit("s", id, "hold", "bad update"); fail("must reject running task") }
        catch (_: IllegalStateException) { }
        assertEquals("original-1", queues.listAll("s").single().message.text)
        assertEquals("original", db.agentTaskDao().getTaskById("task-1")!!.description)
        assertTrue(queues.listAll("s").single().isEditing)
    }

    @Test fun `begin competing with consume admits exactly one owner`() = runBlocking {
        repeat(8) { index ->
            activeOperation()
            val id = enqueue(PromptQueue.STEER, false, index.toLong())
            val edit = async(Dispatchers.Default) { queues.beginEdit("s", id, "lease") }
            val consume = async(Dispatchers.Default) { queues.consume("s", PromptQueue.STEER) }
            val held = edit.await()
            val messages = consume.await()
            if (held == null) assertEquals(1, messages.size)
            else { assertTrue(messages.isEmpty()); queues.cancelEdit("s", id, "lease"); queues.consume("s", PromptQueue.STEER) }
        }
        assertEquals(8, repo.listEntries("s").size)
    }

    @Test fun `steer conversion is atomic keeps queue id and settles prior queued task`() = runBlocking {
        activeOperation()
        val id = enqueue()
        assertTrue(queues.convertToSteer("s", id))
        val prompt = queues.listAll("s").single()
        assertEquals(id, prompt.id)
        assertEquals(PromptQueue.STEER, prompt.queue)
        assertEquals(listOf("image-1"), prompt.message.imageUrls)
        assertNull(prompt.message.taskId)
        assertEquals("CANCELLED", db.agentTaskDao().getTaskById("task-1")!!.status)
        assertEquals("original-1", queues.consume("s", PromptQueue.STEER).single().text)
    }

    @Test fun `steering cannot take an edited or already admitted item`() = runBlocking {
        activeOperation()
        val id = enqueue()
        queues.beginEdit("s", id, "hold")
        assertFalse(queues.convertToSteer("s", id))
        assertEquals("QUEUED", db.agentTaskDao().getTaskById("task-1")!!.status)
        queues.cancelEdit("s", id, "hold")
        queues.removeById("s", id)
        assertFalse(queues.convertToSteer("s", id))
        assertTrue(queues.listAll("s").isEmpty())
    }

    @Test fun `queue write failure rolls task description back in same transaction`() = runBlocking {
        val id = enqueue()
        queues.beginEdit("s", id, "held")
        db.openHelper.writableDatabase.execSQL("CREATE TRIGGER fail_queue_update BEFORE UPDATE OF payloadJson ON harness_queue_items BEGIN SELECT RAISE(ABORT, 'test failure'); END")
        try { queues.saveEdit("s", id, "held", "must roll back"); fail("trigger must abort") }
        catch (_: android.database.sqlite.SQLiteException) { }
        assertEquals("original", db.agentTaskDao().getTaskById("task-1")!!.description)
        assertEquals("original-1", queues.listAll("s").single().message.text)
        assertTrue(queues.listAll("s").single().isEditing)
    }

    @Test fun `held item rejects direct task admission and rolls task status back`() = runBlocking {
        val id = enqueue()
        queues.beginEdit("s", id, "held")
        val lane = repo.ensureLane("s", "main")
        val entry = HarnessEntryEntity(id = "input", sessionId = "s", parentId = lane.leafId,
            createdAt = 2, entryType = "message", customType = "user", payloadJson = "{}")
        val op = HarnessOperationEntity("op", "s", "main", "run", "running", "model", 2, 2, lane.leafId, "{}")
        try {
            repo.acceptTaskOperation("task-1", entry, lane.copy(leafId = entry.id, currentOperationId = "op"), op, id)
            fail("held prompt must not be admitted")
        } catch (_: IllegalStateException) { }
        assertEquals("QUEUED", db.agentTaskDao().getTaskById("task-1")!!.status)
        assertTrue(repo.listEntries("s").isEmpty())
        assertNull(repo.findOperation("op"))
        assertTrue(queues.listAll("s").single().isEditing)
    }

    @Test fun `committed save remains accepted when subsequent scheduling fails`() = runBlocking {
        val id = enqueue()
        val failures = mutableListOf<Exception>()
        val actions = QueuedPromptActions(queues,
            top.wkbin.taixu.harness.session.SessionTurnCoordinatorImpl(), this,
            busy = { false }, startNext = { error("scheduler unavailable") }, refresh = {}, cancelTask = {},
            failure = { _, error -> failures += error })
        assertNotNull(actions.beginQueuedPromptEdit("s", id, "lease"))
        assertTrue(actions.saveQueuedPromptEdit("s", id, "lease", "committed"))
        assertEquals("committed", queues.listAll("s").single().message.text)
        assertFalse(queues.listAll("s").single().isEditing)
        assertEquals("committed", db.agentTaskDao().getTaskById("task-1")!!.description)
        assertEquals(1, failures.size)
    }

    @Test fun `legacy payload missing createdAt uses stable queue timestamp when edited`() = runBlocking {
        repo.enqueue(HarnessQueueItemEntity("legacy", "s", "main", null, "next_run", 42, "{\"text\":\"legacy\"}"))
        val held = queues.beginEdit("s", "legacy", "lease")!!
        assertEquals(42L, held.message.createdAt)
        assertTrue(queues.saveEdit("s", "legacy", "lease", "updated"))
        assertEquals(42L, queues.listAll("s").single().message.createdAt)
        assertEquals(42L, repo.listQueue("s", "main", "next_run").single().createdAt)
    }

    @Test fun `governed inputs cannot be rewritten by foreground editor`() = runBlocking {
        val id = queues.enqueue("s", PromptQueue.NEXT_RUN, PendingMessage("scheduled", initiatorKind = "schedule", initiatorId = "schedule-1"))
        assertNull(queues.beginEdit("s", id, "hold"))
        assertFalse(queues.listAll("s").single().isEditing)
    }
}
