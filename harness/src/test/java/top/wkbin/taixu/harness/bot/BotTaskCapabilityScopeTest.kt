package top.wkbin.taixu.harness.bot

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import top.wkbin.taixu.core.database.*
import top.wkbin.taixu.core.database.repository.RoomGroupChatRepository
import top.wkbin.taixu.core.database.repository.RoomPersonaBotRepository
import top.wkbin.taixu.core.model.bot.*
import top.wkbin.taixu.core.security.SecretManager

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BotTaskCapabilityScopeTest {
    private val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java)
        .allowMainThreadQueries().build()
    private val bots = RoomPersonaBotRepository(db.personaBotDao())
    private val tasks = RoomBotAssignmentRepository(db.botAssignmentDao(), Json)
    private var now = 100L
    private fun scope() = RepositoryBotCapabilityScope(bots, McpServerRepository(db.mcpServerDao(), SecretManager()), tasks) { now }
    @After fun close() = db.close()

    private suspend fun seed() {
        listOf("issuer", "worker", "child").forEach { id ->
            bots.saveBot(Bot(id, id, "role", "persona", "seed", sessionId = "$id-dm", createdAt = 1, updatedAt = 1))
        }
        RoomGroupChatRepository(db.groupChatDao(), Json).saveGroup(GroupChat("group", "Group", "issuer", createdAt = 1))
    }

    private fun task(id: String = "task", bot: String = "worker", issuer: String = "issuer", parent: String? = null) =
        BotAssignment(id, "group", bot, bot, "Work", "Work", BotAssignmentStatus.RUNNING,
            createdAt = 1, startedAt = 1, sessionId = "$id-session", independent = true,
            reportToBotId = issuer, parentAssignmentId = parent)

    private suspend fun grant(task: BotAssignment, source: String = "issuer-dm"): BotAssignment {
        val granted = task.copy(capabilityGrant = scope().issueTaskGrant(task, source))
        tasks.save(granted)
        return granted
    }

    @Test fun `snapshot persists and later widening cannot widen an existing task`() = runBlocking {
        seed()
        val worker = bots.getBot("worker")!!
        bots.saveBot(worker.copy(capabilityToolNames = mapOf("remote" to listOf("read")), capabilityExpiresAt = 200))
        val granted = grant(task())
        assertEquals(granted.capabilityGrant, tasks.get(granted.id)!!.capabilityGrant)
        bots.saveBot(worker)
        assertTrue(scope().allowsTool("task-session", "remote", "read"))
        assertFalse(scope().allowsTool("task-session", "remote", "write"))
        now = 200
        assertFalse(scope().allowsTool("task-session", "remote", "read"))
        assertEquals(emptySet<String>(), scope().allowedMcpServers("task-session"))
    }

    @Test fun `child inherits parent cap and current issuer revocation across reloads`() = runBlocking {
        seed()
        val issuer = bots.getBot("issuer")!!
        bots.saveBot(issuer.copy(capabilityToolNames = mapOf("remote" to listOf("read"))))
        val parent = grant(task())
        bots.saveBot(issuer)
        grant(task("child-task", "child", "worker", parent.id), "task-session")
        assertTrue(scope().allowsTool("child-task-session", "remote", "read"))
        assertFalse(scope().allowsTool("child-task-session", "remote", "write"))
        bots.saveBot(issuer.copy(capabilityToolNames = mapOf("remote" to emptyList())))
        assertFalse(scope().allowsTool("child-task-session", "remote", "read"))
        bots.saveBot(issuer)
        tasks.save(parent.copy(status = BotAssignmentStatus.CANCELLED))
        assertFalse(scope().allowsTool("child-task-session", "remote", "read"))
    }

    @Test fun `inactive wrong-bound corrupt and cyclic grants deny execution`() = runBlocking {
        seed()
        val original = grant(task())
        listOf(BotAssignmentStatus.COMPLETED, BotAssignmentStatus.BLOCKED, BotAssignmentStatus.INTERRUPTED).forEach { status ->
            tasks.save(original.copy(status = status))
            assertFalse(scope().allowsTool("task-session", "remote", "read"))
        }
        tasks.save(original.copy(capabilityGrant = original.capabilityGrant!!.copy(taskId = "another")))
        assertFalse(scope().allowsTool("task-session", "remote", "read"))
        tasks.save(original)
        db.openHelper.writableDatabase.execSQL("UPDATE bot_assignments SET capabilityGrantJson='{' WHERE id='task'")
        assertFalse(scope().allowsTool("task-session", "remote", "read"))
        val cycle = task().copy(parentAssignmentId = "task", reportToBotId = "worker")
        tasks.save(cycle.copy(capabilityGrant = BotTaskCapabilityGrant(cycle.id, cycle.botId, cycle.groupId,
            "worker", cycle.id, BotCapabilityPolicy(null, emptyMap()))))
        assertFalse(scope().allowsTool("task-session", "remote", "read"))
    }

    @Test fun `legacy tasks follow current policy and direct sessions honor absolute expiry`() = runBlocking {
        seed()
        tasks.save(task())
        val worker = bots.getBot("worker")!!
        bots.saveBot(worker.copy(capabilityExpiresAt = 101))
        assertTrue(scope().allowsTool("task-session", "remote", "read"))
        now = 101
        assertFalse(scope().allowsTool("task-session", "remote", "read"))
        assertFalse(scope().allowsTool("worker-dm", "remote", "read"))
        bots.saveBot(worker)
        assertTrue(scope().allowsTool("task-session", "remote", "read"))
    }
}
