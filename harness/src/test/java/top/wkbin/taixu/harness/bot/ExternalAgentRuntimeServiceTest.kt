package top.wkbin.taixu.harness.bot

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import top.wkbin.taixu.core.database.AppDatabase
import top.wkbin.taixu.core.database.BotEntity
import top.wkbin.taixu.core.database.GroupChatEntity
import top.wkbin.taixu.core.database.GroupMemberEntity
import top.wkbin.taixu.core.database.repository.RoomExternalAgentRuntimeTokenRepository
import top.wkbin.taixu.core.database.repository.RoomGroupChatRepository
import top.wkbin.taixu.core.database.repository.RoomPersonaBotRepository
import top.wkbin.taixu.core.model.bot.BotRuntimeMode
import top.wkbin.taixu.core.model.bot.ExternalAgentDeliveryAck
import top.wkbin.taixu.core.model.bot.GroupDelivery
import top.wkbin.taixu.core.model.bot.GroupMessage
import top.wkbin.taixu.core.model.bot.GroupSenderType

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExternalAgentRuntimeServiceTest {
    private val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java)
        .allowMainThreadQueries().build()
    private val bots = RoomPersonaBotRepository(db.personaBotDao())
    private val groups = RoomGroupChatRepository(db.groupChatDao(), Json)
    private val tokens = RoomExternalAgentRuntimeTokenRepository(db.externalAgentRuntimeTokenDao())
    private val runtime = ExternalAgentRuntimeService(bots, groups, tokens)

    @After fun close() = db.close()

    private suspend fun seed() {
        val chief = BotEntity("chief", "Chief", "lead", "persona", "seed", null, null, "dm", true, "#123456", 1, 1)
        db.personaBotDao().upsert(chief)
        db.personaBotDao().upsert(chief.copy(id = "member", name = "Member", sessionId = "member-dm", isMainBot = false))
        db.groupChatDao().upsertGroup(GroupChatEntity("group", "Team", "chief", "", 1))
        db.groupChatDao().addMember(GroupMemberEntity("group", "chief", 0, 1))
        db.groupChatDao().addMember(GroupMemberEntity("group", "member", 1, 1))
    }

    @Test fun `issued token claims ack and posts while local pump would skip`() = runBlocking {
        seed()
        val issued = runtime.issueToken("member", "laptop")
        assertEquals(BotRuntimeMode.EXTERNAL, bots.getBot("member")!!.runtimeMode)
        val principal = runtime.authorize(issued.rawToken)!!
        groups.stageMessage(
            GroupMessage("source", "group", "user", "我", GroupSenderType.USER, "please help", 1),
            listOf("member"),
        )
        assertEquals(1, groups.pendingDeliveries().first().size)
        val event = runtime.claimNextDelivery(principal)!!
        assertEquals("please help", event.content)
        assertTrue(runtime.acknowledge(principal, ExternalAgentDeliveryAck(event.deliveryId, event.claimNonce, GroupDelivery.COMPLETED)))
        assertEquals(GroupDelivery.COMPLETED, groups.findDelivery(event.deliveryId)!!.status)
        val messageId = runtime.postMessage(principal, "group", "done on laptop")
        assertTrue(groups.observeMessages("group").first().any { it.id == messageId && it.content == "done on laptop" })
        assertNull(runtime.authorize("tx_rt_not-real"))
        assertFalse(runtime.acknowledge(principal, ExternalAgentDeliveryAck(event.deliveryId, "wrong", GroupDelivery.FAILED)))
    }

    @Test fun `revoke and restore local reject old plaintext`() = runBlocking {
        seed()
        val issued = runtime.issueToken("member", "box")
        assertTrue(runtime.revokeToken(issued.tokenId))
        assertNull(runtime.authorize(issued.rawToken))
        val again = runtime.issueToken("member", "box-2")
        assertTrue(runtime.restoreLocalRuntime("member"))
        assertEquals(BotRuntimeMode.LOCAL, bots.getBot("member")!!.runtimeMode)
        assertNull(runtime.authorize(again.rawToken))
    }

    @Test fun `main bot cannot become external`() = runBlocking {
        seed()
        assertTrue(runCatching { runtime.issueToken("chief", "nope") }.isFailure)
    }

    @Test fun `claim coalesces consecutive same-group pending deliveries`() = runBlocking {
        seed()
        val issued = runtime.issueToken("member", "laptop")
        val principal = runtime.authorize(issued.rawToken)!!
        groups.stageMessage(
            GroupMessage("m1", "group", "user", "我", GroupSenderType.USER, "first", 1),
            listOf("member"),
        )
        groups.stageMessage(
            GroupMessage("m2", "group", "user", "我", GroupSenderType.USER, "second", 2),
            listOf("member"),
        )
        groups.stageMessage(
            GroupMessage("m3", "group", "user", "我", GroupSenderType.USER, "third", 3),
            listOf("member"),
        )
        val event = runtime.claimNextDelivery(principal)!!
        assertEquals(3, event.leases.size)
        assertEquals("first", event.leases[0].content)
        assertEquals("second", event.leases[1].content)
        assertEquals("third", event.leases[2].content)
        assertTrue(event.content.contains("first"))
        assertTrue(event.content.contains("second"))
        assertTrue(event.content.contains("third"))
        assertTrue(event.content.contains("另有 2 条"))
        assertEquals(0, groups.pendingDeliveries().first().size)
        event.leases.forEach { lease ->
            assertTrue(
                runtime.acknowledge(
                    principal,
                    ExternalAgentDeliveryAck(lease.deliveryId, lease.claimNonce, GroupDelivery.COMPLETED),
                ),
            )
        }
    }
}
