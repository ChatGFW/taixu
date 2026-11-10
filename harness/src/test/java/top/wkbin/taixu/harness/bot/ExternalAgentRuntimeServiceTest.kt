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

    @Test fun `issued token can carry absolute expiry`() = runBlocking {
        seed()
        val expiresAt = System.currentTimeMillis() + 60_000L
        val issued = runtime.issueToken("member", "laptop", expiresAt)
        assertEquals(expiresAt, issued.expiresAt)
        assertEquals(expiresAt, runtime.listTokens("member").single { it.id == issued.tokenId }.expiresAt)
        assertNotNull(runtime.authorize(issued.rawToken))
    }

    @Test fun `restore local requeues submitting claims for the member`() = runBlocking {
        seed()
        val issued = runtime.issueToken("member", "laptop")
        val principal = runtime.authorize(issued.rawToken)!!
        groups.stageMessage(
            GroupMessage("source", "group", "user", "我", GroupSenderType.USER, "hold me", 1),
            listOf("member"),
        )
        val event = runtime.claimNextDelivery(principal)!!
        assertEquals(GroupDelivery.SUBMITTING, groups.findDelivery(event.deliveryId)!!.status)
        assertTrue(runtime.restoreLocalRuntime("member"))
        assertEquals(BotRuntimeMode.LOCAL, bots.getBot("member")!!.runtimeMode)
        assertEquals(GroupDelivery.PENDING, groups.findDelivery(event.deliveryId)!!.status)
        assertNull(groups.findDelivery(event.deliveryId)!!.claimNonce)
        assertNull(runtime.authorize(issued.rawToken))
    }

    @Test fun `expired token cannot authorize and is purged on list`() = runBlocking {
        var clock = 1_000_000L
        val timed = ExternalAgentRuntimeService(bots, groups, tokens, now = { clock })
        seed()
        val issued = timed.issueToken("member", "laptop", expiresAt = clock + 5_000L)
        assertNotNull(timed.authorize(issued.rawToken))
        clock += 6_000L
        assertNull(timed.authorize(issued.rawToken))
        assertEquals(1, timed.revokeExpiredTokens("member"))
        assertTrue(timed.listTokens("member").none { it.id == issued.tokenId && it.revokedAt == null })
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
        assertEquals(3, runtime.acknowledgeAll(
            principal,
            event.leases.map { ExternalAgentDeliveryAck(it.deliveryId, it.claimNonce, GroupDelivery.COMPLETED) },
        ))
    }

    @Test fun `failed ack stores error and clears lease`() = runBlocking {
        seed()
        val issued = runtime.issueToken("member", "laptop")
        val principal = runtime.authorize(issued.rawToken)!!
        groups.stageMessage(
            GroupMessage("source", "group", "user", "我", GroupSenderType.USER, "please help", 1),
            listOf("member"),
        )
        val event = runtime.claimNextDelivery(principal)!!
        assertTrue(
            runtime.acknowledge(
                principal,
                ExternalAgentDeliveryAck(event.deliveryId, event.claimNonce, GroupDelivery.FAILED, "tool crashed"),
            ),
        )
        val receipt = groups.findDelivery(event.deliveryId)!!
        assertEquals(GroupDelivery.FAILED, receipt.status)
        assertEquals("tool crashed", receipt.error)
        assertNull(receipt.claimedAt)
    }

    @Test fun `duplicate ack on settled lease is idempotent`() = runBlocking {
        seed()
        val issued = runtime.issueToken("member", "laptop")
        val principal = runtime.authorize(issued.rawToken)!!
        groups.stageMessage(
            GroupMessage("source", "group", "user", "我", GroupSenderType.USER, "please help", 1),
            listOf("member"),
        )
        val event = runtime.claimNextDelivery(principal)!!
        val first = ExternalAgentDeliveryAck(event.deliveryId, event.claimNonce, GroupDelivery.COMPLETED)
        assertTrue(runtime.acknowledge(principal, first))
        assertTrue(runtime.acknowledge(principal, first))
        assertTrue(
            runtime.acknowledge(
                principal,
                ExternalAgentDeliveryAck(event.deliveryId, event.claimNonce, GroupDelivery.FAILED, "late retry"),
            ),
        )
        assertEquals(GroupDelivery.COMPLETED, groups.findDelivery(event.deliveryId)!!.status)
    }

    @Test fun `release returns claim to pending for another turn`() = runBlocking {
        seed()
        val issued = runtime.issueToken("member", "laptop")
        val principal = runtime.authorize(issued.rawToken)!!
        groups.stageMessage(
            GroupMessage("source", "group", "user", "我", GroupSenderType.USER, "please help", 1),
            listOf("member"),
        )
        val event = runtime.claimNextDelivery(principal)!!
        assertEquals(0, groups.pendingDeliveries().first().size)
        assertTrue(runtime.release(principal, event.deliveryId, event.claimNonce))
        assertEquals(1, groups.pendingDeliveries().first().size)
        assertFalse(runtime.release(principal, event.deliveryId, event.claimNonce))
        val again = runtime.claimNextDelivery(principal)!!
        assertEquals("please help", again.content)
        assertEquals(1, runtime.acknowledgeAll(
            principal,
            listOf(ExternalAgentDeliveryAck(again.deliveryId, again.claimNonce, GroupDelivery.COMPLETED)),
        ))
    }

    @Test fun `recover keeps external submitting lease instead of requeueing`() = runBlocking {
        seed()
        val issued = runtime.issueToken("member", "laptop")
        val principal = runtime.authorize(issued.rawToken)!!
        groups.stageMessage(
            GroupMessage("source", "group", "user", "我", GroupSenderType.USER, "recover me", 1),
            listOf("member"),
        )
        val event = runtime.claimNextDelivery(principal)!!
        groups.recoverDeliveries()
        val receipt = groups.findDelivery("source:member")!!
        assertEquals(GroupDelivery.SUBMITTING, receipt.status)
        assertEquals(event.claimNonce, receipt.claimNonce)
        assertNull(runtime.claimNextDelivery(principal))
        assertTrue(
            runtime.acknowledge(
                principal,
                ExternalAgentDeliveryAck(event.deliveryId, event.claimNonce, GroupDelivery.COMPLETED),
            ),
        )
    }

    @Test fun `lists member groups and recent context`() = runBlocking {
        seed()
        val issued = runtime.issueToken("member", "laptop")
        val principal = runtime.authorize(issued.rawToken)!!
        groups.postMessage(GroupMessage("a", "group", "user", "我", GroupSenderType.USER, "hello", 1))
        groups.postMessage(GroupMessage("b", "group", "member", "Member", GroupSenderType.BOT, "hi", 2))
        assertEquals(listOf("group"), runtime.listGroups(principal).map { it.id })
        val context = runtime.groupContext(principal, "group", limit = 10)
        assertEquals(listOf("a", "b"), context.map { it.id })
        assertEquals("USER", context.first().senderType)
        assertTrue(runCatching { runtime.groupContext(principal, "missing") }.isFailure)
    }

    @Test fun `session and pending peek leave deliveries unclaimed`() = runBlocking {
        seed()
        val issued = runtime.issueToken("member", "laptop")
        val principal = runtime.authorize(issued.rawToken)!!
        groups.stageMessage(
            GroupMessage("source", "group", "user", "我", GroupSenderType.USER, "please help", 1),
            listOf("member"),
        )
        val session = runtime.describeSession(principal)
        assertEquals("member", session.botId)
        assertEquals("laptop", session.label)
        assertEquals(1, session.pendingCount)
        assertEquals(ExternalAgentRuntimeService.CLAIM_LEASE_TTL_MS, session.claimLeaseTtlMs)
        assertEquals(listOf("group"), session.groups.map { it.id })
        val peek = runtime.peekPending(principal)
        assertEquals(1, peek.size)
        assertEquals("please help", peek.single().contentPreview)
        assertEquals(1, groups.pendingDeliveries().first().size)
        assertEquals(GroupDelivery.PENDING, groups.findDelivery(peek.single().deliveryId)!!.status)
    }

    @Test fun `stale claim is reclaimed and heartbeat renews lease`() = runBlocking {
        seed()
        val issued = runtime.issueToken("member", "laptop")
        val principal = runtime.authorize(issued.rawToken)!!
        groups.stageMessage(
            GroupMessage("source", "group", "user", "我", GroupSenderType.USER, "stale me", 1),
            listOf("member"),
        )
        val event = runtime.claimNextDelivery(principal)!!
        assertEquals(0, groups.pendingDeliveries().first().size)
        assertNotNull(groups.findDelivery(event.deliveryId)!!.claimedAt)
        assertEquals(1, runtime.heartbeat(principal, listOf(event.deliveryId to event.claimNonce)))
        val renewedAt = groups.findDelivery(event.deliveryId)!!.claimedAt!!
        // Age below TTL: reclaim should leave the heartbeated lease alone.
        db.openHelper.writableDatabase.execSQL(
            "UPDATE group_deliveries SET claimedAt = ? WHERE id = ?",
            arrayOf<Any>(
                renewedAt - (ExternalAgentRuntimeService.CLAIM_LEASE_TTL_MS - 5_000L),
                event.deliveryId,
            ),
        )
        assertEquals(0, runtime.reclaimStaleClaims())
        assertEquals(GroupDelivery.SUBMITTING, groups.findDelivery(event.deliveryId)!!.status)
        // Age past TTL: reclaim returns it to pending.
        db.openHelper.writableDatabase.execSQL(
            "UPDATE group_deliveries SET claimedAt = ? WHERE id = ?",
            arrayOf<Any>(
                System.currentTimeMillis() - ExternalAgentRuntimeService.CLAIM_LEASE_TTL_MS - 1_000L,
                event.deliveryId,
            ),
        )
        assertEquals(1, runtime.reclaimStaleClaims())
        assertEquals(1, groups.pendingDeliveries().first().size)
        assertEquals(GroupDelivery.PENDING, groups.findDelivery(event.deliveryId)!!.status)
        assertNull(groups.findDelivery(event.deliveryId)!!.claimNonce)
        assertNull(groups.findDelivery(event.deliveryId)!!.claimedAt)
    }
}
