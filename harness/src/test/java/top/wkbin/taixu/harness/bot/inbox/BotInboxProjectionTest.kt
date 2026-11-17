package top.wkbin.taixu.harness.bot.inbox

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import top.wkbin.taixu.core.common.logging.AppLogger
import top.wkbin.taixu.core.database.AppDatabase
import top.wkbin.taixu.core.database.repository.BotInboxRepository
import top.wkbin.taixu.core.database.repository.RoomBotInboxRepository
import top.wkbin.taixu.core.model.bot.BotInboxItem
import top.wkbin.taixu.core.model.bot.BotInboxSource
import top.wkbin.taixu.core.model.bot.BotInboxState

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BotInboxProjectionTest {
    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
    private val repository = RoomBotInboxRepository(db.botInboxDao())
    private val log = AppLogger(context) { it }
    private val projection = BotInboxProjection(repository, log)

    @After fun close() = db.close()

    @Test fun `admission is idempotent per source and bot`() = runBlocking {
        assertTrue(projection.admit("bot", BotInboxSource.SCHEDULE, "s1", "排期触发", now = 10))
        assertFalse(projection.admit("bot", BotInboxSource.SCHEDULE, "s1", "重复准入", now = 11))

        assertEquals(1, repository.countForBot("bot", BotInboxState.PENDING))
        assertEquals(10, repository.findBySource("bot", BotInboxSource.SCHEDULE, "s1")!!.createdAt)
        assertTrue(projection.admit("other", BotInboxSource.SCHEDULE, "s1", "另一个 Bot", now = 12))
    }

    @Test fun `duplicate admission never resets a decided item`() = runBlocking {
        projection.admit("bot", BotInboxSource.SCHEDULE, "s1", "排期触发")
        projection.observe("bot", BotInboxSource.SCHEDULE, "s1", now = 20)
        projection.ignore("bot", BotInboxSource.SCHEDULE, "s1", now = 21)

        assertFalse(projection.admit("bot", BotInboxSource.SCHEDULE, "s1", "追溯准入"))
        assertEquals(BotInboxState.IGNORED, repository.findBySource("bot", BotInboxSource.SCHEDULE, "s1")!!.state)
    }

    @Test fun `observe records when the item entered a turn`() = runBlocking {
        projection.admit("bot", BotInboxSource.GROUP_MESSAGE, "m1", "被点名")
        assertNull(repository.findBySource("bot", BotInboxSource.GROUP_MESSAGE, "m1")!!.observedAt)

        projection.observe("bot", BotInboxSource.GROUP_MESSAGE, "m1", now = 20)
        val item = repository.findBySource("bot", BotInboxSource.GROUP_MESSAGE, "m1")!!
        assertEquals(BotInboxState.OBSERVED, item.state)
        assertEquals(20L, item.observedAt)
        assertEquals(20L, item.updatedAt)
    }

    @Test fun `defer and ignore stay undecided-by-observation`() = runBlocking {
        projection.admit("bot", BotInboxSource.ASSIGNMENT, "a1", "被派工")
        projection.defer("bot", BotInboxSource.ASSIGNMENT, "a1", now = 30)
        val deferred = repository.findBySource("bot", BotInboxSource.ASSIGNMENT, "a1")!!
        assertEquals(BotInboxState.DEFERRED, deferred.state)
        assertNull(deferred.observedAt)

        projection.ignore("bot", BotInboxSource.ASSIGNMENT, "a1", now = 31)
        assertEquals(BotInboxState.IGNORED, repository.findBySource("bot", BotInboxSource.ASSIGNMENT, "a1")!!.state)
    }

    @Test fun `defer preserves the first observation across repeated uncertain outcomes`() = runBlocking {
        projection.admit("bot", BotInboxSource.ASSIGNMENT, "observed", "被派工", now = 10)
        projection.observe("bot", BotInboxSource.ASSIGNMENT, "observed", now = 20)
        projection.defer("bot", BotInboxSource.ASSIGNMENT, "observed", now = 30)
        projection.defer("bot", BotInboxSource.ASSIGNMENT, "observed", now = 40, observedAt = 99)
        val item = repository.findBySource("bot", BotInboxSource.ASSIGNMENT, "observed")!!
        assertEquals(BotInboxState.DEFERRED, item.state)
        assertEquals(20L, item.observedAt)
    }

    @Test fun `uncertain admitted receipt records its observation time`() = runBlocking {
        projection.admit("bot", BotInboxSource.GROUP_MESSAGE, "uncertain", "群消息", now = 10)
        projection.defer("bot", BotInboxSource.GROUP_MESSAGE, "uncertain", now = 30, observedAt = 20)
        val item = repository.findBySource("bot", BotInboxSource.GROUP_MESSAGE, "uncertain")!!
        assertEquals(BotInboxState.DEFERRED, item.state)
        assertEquals(20L, item.observedAt)
    }

    @Test fun `cancellation is never converted into a projection failure`() = runBlocking {
        val cancelled = BotInboxProjection(object : BotInboxRepository by repository {
            override suspend fun findBySource(botId: String, sourceKind: String, sourceId: String): BotInboxItem? =
                throw CancellationException("caller cancelled")
        }, log)
        val failure = runCatching {
            cancelled.observe("bot", BotInboxSource.SYSTEM, "cancelled")
        }.exceptionOrNull()
        assertTrue(failure is CancellationException)
    }

    @Test fun `projection failures are contained instead of blocking the caller`() = runBlocking {
        val broken = BotInboxProjection(object : BotInboxRepository {
            override suspend fun admit(item: BotInboxItem): Boolean = error("db down")
            override suspend fun decide(id: String, state: String, observedAt: Long?, now: Long): Boolean = error("db down")
            override suspend fun listForBot(botId: String, state: String, limit: Int): List<BotInboxItem> = emptyList()
            override suspend fun findBySource(botId: String, sourceKind: String, sourceId: String): BotInboxItem? = null
            override suspend fun countForBot(botId: String, state: String): Int = 0
            override suspend fun deleteForBot(botId: String) = Unit
        }, log)

        assertFalse(broken.admit("bot", BotInboxSource.SYSTEM, "x", "系统事件"))
        assertFalse(broken.observe("bot", BotInboxSource.SYSTEM, "x"))
    }
}
