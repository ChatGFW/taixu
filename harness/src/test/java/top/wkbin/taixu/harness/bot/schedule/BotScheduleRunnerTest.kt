package top.wkbin.taixu.harness.bot.schedule

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.TimeZone
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLog
import top.wkbin.taixu.core.common.logging.AppLogger
import top.wkbin.taixu.core.database.AppDatabase
import top.wkbin.taixu.core.database.BotEntity
import top.wkbin.taixu.core.database.BotScheduleDeliveryEntity
import top.wkbin.taixu.core.database.BotScheduleEntity
import top.wkbin.taixu.core.database.repository.RoomBotInboxRepository
import top.wkbin.taixu.core.database.repository.RoomBotScheduleRepository
import top.wkbin.taixu.core.database.repository.RoomPersonaBotRepository
import top.wkbin.taixu.core.model.bot.Bot
import top.wkbin.taixu.harness.bot.inbox.BotInboxProjection

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BotScheduleRunnerTest {
    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
    private val schedules = RoomBotScheduleRepository(db.botScheduleDao(), db.botScheduleDeliveryDao())
    private val bots = RoomPersonaBotRepository(db.personaBotDao())
    private val log = AppLogger(context) { it }
    private val inbox = BotInboxProjection(RoomBotInboxRepository(db.botInboxDao()), log)

    @After fun close() = db.close()

    private suspend fun seed() {
        db.personaBotDao().upsert(BotEntity("bot", "name", "role", "persona", "seed", null, null, "dm", true, "#123456", 1, 1))
        db.botScheduleDao().upsert(BotScheduleEntity("schedule", "bot", null, "* * * * *", "work", true, 100, null, 1))
    }

    private fun runner(submit: suspend (Bot, String, String?, String) -> Boolean) =
        BotScheduleRunner(schedules, bots, log, BotScheduledSubmission(submit), inbox)

    private fun status(): String = db.openHelper.readableDatabase.query("SELECT status FROM bot_schedule_deliveries").use {
        assertTrue(it.moveToFirst()); it.getString(0)
    }

    private fun deliveryCount(): Int = db.openHelper.readableDatabase.query("SELECT COUNT(*) FROM bot_schedule_deliveries").use {
        assertTrue(it.moveToFirst()); it.getInt(0)
    }

    @Test fun `pending occurrence coalesces later firings instead of stacking`() = runBlocking {
        seed()
        db.botScheduleDeliveryDao().stage("schedule", 100, 101, 200)
        db.botScheduleDeliveryDao().stage("schedule", 200, 202, 300)

        val pending = db.botScheduleDeliveryDao().pending(10)
        assertEquals(1, pending.size)
        assertEquals(200, pending.single().scheduledAt)
        assertEquals("work", pending.single().instruction)
        assertEquals(1, deliveryCount())
    }

    @Test fun `coalesced occurrence admits once and leaves no backlog`() = runBlocking {
        seed()
        db.botScheduleDeliveryDao().stage("schedule", 100, 101, 200)
        db.botScheduleDeliveryDao().stage("schedule", 200, 202, 300)

        var submissions = 0
        assertEquals(1, runner { _, _, _, _ -> submissions++; true }.runDue(250))
        assertEquals(1, submissions)
        assertTrue(db.botScheduleDeliveryDao().pending(10).isEmpty())
    }

    @Test fun `legacy pending backlog collapses to one`() = runBlocking {
        seed()
        db.botScheduleDeliveryDao().insert(BotScheduleDeliveryEntity("schedule:90", "schedule", "bot", null, "work", 90, 91, "pending", null))
        db.botScheduleDeliveryDao().insert(BotScheduleDeliveryEntity("schedule:95", "schedule", "bot", null, "work", 95, 96, "pending", null))

        db.botScheduleDeliveryDao().stage("schedule", 100, 101, 200)

        val pending = db.botScheduleDeliveryDao().pending(10)
        assertEquals(1, pending.size)
        assertEquals(100, pending.single().scheduledAt)
        assertEquals(1, deliveryCount())
    }

    @Test fun `restart delivers only the latest missed occurrence`() = runBlocking {
        seed()
        var submissions = 0
        assertEquals(1, runner { _, _, _, _ -> submissions++; true }.runDue(1_000))
        assertEquals(1, submissions)
        assertEquals(1, deliveryCount())
        assertTrue(db.botScheduleDeliveryDao().pending(10).isEmpty())
    }

    @Test fun `rejection is not counted as accepted`() = runBlocking {
        seed()
        assertEquals(0, runner { _, _, _, _ -> false }.runDue(101))
        assertEquals("rejected", status())
    }

    @Test fun `accepted occurrence is not submitted a second time`() = runBlocking {
        seed()
        var submissions = 0
        val runner = runner { _, _, _, _ -> submissions++; true }
        assertEquals(1, runner.runDue(101))
        assertEquals(0, runner.runDue(102))
        assertEquals(1, submissions)
        assertEquals("admitted", status())
    }

    @Test fun `pending occurrence survives restart after the schedule advanced`() = runBlocking {
        seed()
        db.botScheduleDeliveryDao().stage("schedule", 100, 101, 60_000)
        var submissions = 0
        assertEquals(1, runner { _, text, _, scheduleId ->
            assertEquals("work", text); assertEquals("schedule", scheduleId); submissions++; true
        }.runDue(102))
        assertEquals(1, submissions)
    }

    @Test fun `interrupted admission is recorded and never blindly replayed`() = runBlocking {
        seed()
        try { runner { _, _, _, _ -> throw CancellationException("worker stopped") }.runDue(101); fail() }
        catch (_: CancellationException) { }
        assertEquals("unknown", status())
        assertEquals(0, runner { _, _, _, _ -> fail("must not replay"); true }.runDue(102))
    }

    @Test fun `exception after possible acceptance remains unknown`() = runBlocking {
        seed()
        assertEquals(0, runner { _, _, _, _ -> error("lost receipt") }.runDue(101))
        assertEquals("unknown", status())
    }

    @Test fun `accepted occurrence is projected as observed`() = runBlocking {
        seed()
        assertEquals(1, runner { _, _, _, _ -> true }.runDue(101))
        val item = db.botInboxDao().findBySource("bot", "schedule", "schedule:100")
        assertNotNull(item)
        assertEquals("observed", item!!.state)
        assertNotNull(item.observedAt)
    }

    @Test fun `rejected occurrence is projected as ignored`() = runBlocking {
        seed()
        assertEquals(0, runner { _, _, _, _ -> false }.runDue(101))
        val item = db.botInboxDao().findBySource("bot", "schedule", "schedule:100")
        assertEquals("ignored", item!!.state)
        assertNull(item.observedAt)
    }

    @Test fun `null nextAfter delivers once then parks at max and disables`() = runBlocking {
        val previous = TimeZone.getDefault()
        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Shanghai"))
        try {
            val dueAt = LocalDateTime.parse("2028-02-29T00:00:00").atZone(ZoneId.of("Asia/Shanghai")).toInstant().toEpochMilli()
            assertNull(CronSchedule.parse("0 0 29 2 *").nextAfter(dueAt))
            db.personaBotDao().upsert(BotEntity("bot", "name", "role", "persona", "seed", null, null, "dm", true, "#123456", 1, 1))
            db.botScheduleDao().upsert(BotScheduleEntity("leap", "bot", null, "0 0 29 2 *", "leap-day", true, dueAt, null, 1))
            ShadowLog.clear()
            var submissions = 0
            val runner = runner { _, text, _, _ -> assertEquals("leap-day", text); submissions++; true }
            assertEquals(1, runner.runDue(dueAt))
            assertEquals(1, submissions)
            val row = db.botScheduleDao().findById("leap")!!
            assertEquals(Long.MAX_VALUE, row.nextRunAt)
            assertFalse(row.enabled)
            assertTrue(schedules.listDue(dueAt).isEmpty())
            assertEquals("admitted", status())
            assertTrue(ShadowLog.getLogsForTag("TaiXu").any { it.msg.contains("leap") && it.msg.contains("0 0 29 2 *") })
            assertEquals(0, runner.runDue(dueAt + 60_000))
            assertEquals(1, submissions)
        } finally {
            TimeZone.setDefault(previous)
        }
    }
}
