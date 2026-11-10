package top.wkbin.taixu.harness.groupchat

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import top.wkbin.taixu.core.common.logging.AppLogger
import top.wkbin.taixu.core.database.AppDatabase
import top.wkbin.taixu.core.database.BotEntity
import top.wkbin.taixu.core.database.GroupChatEntity
import top.wkbin.taixu.core.database.GroupMemberEntity
import top.wkbin.taixu.core.database.repository.RoomGroupChatRepository
import top.wkbin.taixu.core.model.bot.GroupDelivery
import top.wkbin.taixu.core.model.bot.GroupMessage
import top.wkbin.taixu.core.model.bot.GroupSenderType

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class GroupDeliveryExternalSkipTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
    private val groups = RoomGroupChatRepository(db.groupChatDao(), Json)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val replies = GroupReplyQueue(scope)
    private val logger = AppLogger(context) { it }

    @After fun close() = runBlocking {
        scope.coroutineContext[Job]!!.cancelAndJoin()
        db.close()
    }

    @Test fun `pump leaves external bot receipts pending for the runtime`() = runBlocking {
        db.personaBotDao().upsert(BotEntity("bot", "Bot", "lead", "", "seed", null, null, "dm", false, "#123456", 1, 1))
        db.personaBotDao().upsert(BotEntity("admin", "Admin", "lead", "", "seed", null, null, "dm-admin", true, "#123456", 1, 1))
        db.groupChatDao().upsertGroup(GroupChatEntity("group", "Team", "admin", "", 1))
        db.groupChatDao().addMember(GroupMemberEntity("group", "bot", 0, 1))
        db.groupChatDao().addMember(GroupMemberEntity("group", "admin", 1, 1))
        groups.stageMessage(GroupMessage("source", "group", "user", "我", GroupSenderType.USER, "hi", 1), listOf("bot"))
        var executed = 0
        val pump = GroupDeliveryPump(
            groups, replies, scope, logger,
            isExternalRuntime = { it == "bot" },
        ) {
            executed++
            GroupTurnOutcome("done", false)
        }
        pump.start()
        delay(300)
        assertEquals(0, executed)
        assertEquals(GroupDelivery.PENDING, db.groupChatDao().findGroupDelivery("source:bot")!!.status)
        assertEquals(1, groups.pendingDeliveries().first().size)
        withTimeout(1_000) { /* pump stays idle */ }
    }
}
