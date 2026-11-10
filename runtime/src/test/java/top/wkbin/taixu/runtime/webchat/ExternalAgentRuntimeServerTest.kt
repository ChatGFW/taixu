package top.wkbin.taixu.runtime.webchat

import androidx.test.core.app.ApplicationProvider
import java.net.HttpURLConnection
import java.net.ServerSocket
import java.net.URL
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import top.wkbin.taixu.core.common.logging.AppLogger
import top.wkbin.taixu.core.common.logging.SensitiveDataRedactor

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExternalAgentRuntimeServerTest {
    private lateinit var server: ExternalAgentRuntimeServer
    private var port = 0
    private val gateway = FakeGateway()

    @Before
    fun setUp() {
        port = ServerSocket(0).use { it.localPort }
        val logger = AppLogger(ApplicationProvider.getApplicationContext(), SensitiveDataRedactor { it })
        server = ExternalAgentRuntimeServer(gateway, logger)
        assertTrue(server.start(port))
    }

    @After
    fun tearDown() {
        server.close()
    }

    @Test
    fun `health is unauthenticated`() {
        val connection = open("GET", "/runtime/v1/health")
        assertEquals(200, connection.responseCode)
        val body = connection.inputStream.bufferedReader().readText()
        assertTrue(body.contains("\"ok\":true"))
        assertTrue(body.contains("\"service\":\"external-agent-runtime\""))
    }

    @Test
    fun `meta lists routes without bearer token`() {
        val connection = open("GET", "/runtime/v1/meta")
        assertEquals(200, connection.responseCode)
        val body = connection.inputStream.bufferedReader().readText()
        assertTrue(body.contains("/runtime/v1/events/claim"))
        assertTrue(body.contains("\"ackStatuses\""))
        assertTrue(body.contains("\"claimLeaseTtlMs\":"))
    }

    @Test
    fun `rejects missing bearer token`() {
        val connection = open("GET", "/runtime/v1/me")
        assertEquals(401, connection.responseCode)
    }

    @Test
    fun `rejects webchat style pin tokens`() {
        val connection = open("GET", "/runtime/v1/me") {
            setRequestProperty("Authorization", "Bearer pin-1234")
        }
        assertEquals(401, connection.responseCode)
    }

    @Test
    fun `me returns identity for runtime token`() {
        gateway.identity = ExternalAgentRuntimeIdentity("bot-1", "laptop")
        gateway.pendingCount = 2
        val connection = open("GET", "/runtime/v1/me") {
            setRequestProperty("Authorization", "Bearer tx_rt_abc")
        }
        assertEquals(200, connection.responseCode)
        val body = connection.inputStream.bufferedReader().readText()
        assertTrue(body.contains("\"botId\":\"bot-1\""))
        assertTrue(body.contains("\"label\":\"laptop\""))
        assertTrue(body.contains("\"pendingCount\":2"))
        assertTrue(body.contains("\"claimLeaseTtlMs\":"))
        assertTrue(body.contains("\"id\":\"g1\""))
        assertTrue(body.contains("\"name\":\"Team\""))
    }

    @Test
    fun `pending peeks without claiming`() {
        gateway.identity = ExternalAgentRuntimeIdentity("bot-1", "laptop")
        gateway.pending = listOf(
            ExternalAgentRuntimePendingPreview("d1", "g1", "m1", "please help", 11L),
            ExternalAgentRuntimePendingPreview("d2", "g1", "m2", "follow up", 12L),
        )
        val connection = open("GET", "/runtime/v1/events/pending?limit=10") {
            setRequestProperty("Authorization", "Bearer tx_rt_abc")
        }
        assertEquals(200, connection.responseCode)
        val body = connection.inputStream.bufferedReader().readText()
        assertTrue(body.contains("\"count\":2"))
        assertTrue(body.contains("\"deliveryId\":\"d1\""))
        assertTrue(body.contains("\"contentPreview\":\"please help\""))
        assertEquals(0, gateway.claimAttempts)
    }

    @Test
    fun `claim returns 204 when queue empty`() {
        gateway.identity = ExternalAgentRuntimeIdentity("bot-1", "laptop")
        val connection = open("POST", "/runtime/v1/events/claim") {
            setRequestProperty("Authorization", "Bearer tx_rt_abc")
            setDoOutput(true)
            outputStream.write(ByteArray(0))
        }
        assertEquals(204, connection.responseCode)
    }

    @Test
    fun `claim returns event payload with leases`() {
        gateway.identity = ExternalAgentRuntimeIdentity("bot-1", "laptop")
        gateway.claim = ExternalAgentRuntimeClaim(
            deliveryId = "d1",
            claimNonce = "n1",
            groupId = "g1",
            botId = "bot-1",
            sourceMessageId = "m1",
            content = "hello\n\nextra",
            createdAt = 42L,
            leases = listOf(
                ExternalAgentRuntimeClaimLease("d1", "n1", "m1", "hello"),
                ExternalAgentRuntimeClaimLease("d2", "n2", "m2", "extra"),
            ),
            leaseExpiresAt = 99L,
        )
        val connection = open("POST", "/runtime/v1/events/claim") {
            setRequestProperty("Authorization", "Bearer tx_rt_abc")
            setDoOutput(true)
            outputStream.write(ByteArray(0))
        }
        assertEquals(200, connection.responseCode)
        val body = connection.inputStream.bufferedReader().readText()
        assertTrue(body.contains("\"deliveryId\":\"d1\""))
        assertTrue(body.contains("\"claimNonce\":\"n1\""))
        assertTrue(body.contains("\"leases\""))
        assertTrue(body.contains("\"deliveryId\":\"d2\""))
        assertTrue(body.contains("\"content\":\"extra\""))
        assertTrue(body.contains("\"leaseExpiresAt\":99"))
    }

    @Test
    fun `claim waitMs long-polls until event arrives`() {
        gateway.identity = ExternalAgentRuntimeIdentity("bot-1", "laptop")
        gateway.claimAfterAttempts = 3
        gateway.claim = ExternalAgentRuntimeClaim(
            deliveryId = "d9",
            claimNonce = "n9",
            groupId = "g1",
            botId = "bot-1",
            sourceMessageId = "m9",
            content = "late",
            createdAt = 9L,
            leases = listOf(ExternalAgentRuntimeClaimLease("d9", "n9", "m9", "late")),
        )
        val connection = open("POST", "/runtime/v1/events/claim?waitMs=2000") {
            setRequestProperty("Authorization", "Bearer tx_rt_abc")
            setDoOutput(true)
            outputStream.write(ByteArray(0))
        }
        assertEquals(200, connection.responseCode)
        assertTrue(gateway.claimAttempts >= 3)
        val body = connection.inputStream.bufferedReader().readText()
        assertTrue(body.contains("\"deliveryId\":\"d9\""))
    }

    @Test
    fun `claim rejects invalid token before long-poll`() {
        gateway.identity = null
        val started = System.currentTimeMillis()
        val connection = open("POST", "/runtime/v1/events/claim?waitMs=5000") {
            setRequestProperty("Authorization", "Bearer tx_rt_unknown")
            setDoOutput(true)
            outputStream.write(ByteArray(0))
        }
        assertEquals(401, connection.responseCode)
        assertEquals(0, gateway.claimAttempts)
        assertTrue(System.currentTimeMillis() - started < 2_000)
    }

    @Test
    fun `ack batch settles multiple leases`() {
        gateway.identity = ExternalAgentRuntimeIdentity("bot-1", "laptop")
        gateway.ackBatchOk = true
        val body = """{"status":"completed","leases":[{"deliveryId":"d1","claimNonce":"n1"},{"deliveryId":"d2","claimNonce":"n2"}]}"""
        val connection = open("POST", "/runtime/v1/events/ack") {
            setRequestProperty("Authorization", "Bearer tx_rt_abc")
            setRequestProperty("Content-Type", "application/json")
            setDoOutput(true)
            outputStream.write(body.toByteArray())
        }
        assertEquals(200, connection.responseCode)
        val text = connection.inputStream.bufferedReader().readText()
        assertTrue(text.contains("\"settled\":2"))
        assertEquals(2, gateway.ackBatchCount)
    }

    @Test
    fun `ack rejects unsupported status with 400`() {
        gateway.identity = ExternalAgentRuntimeIdentity("bot-1", "laptop")
        val body = """{"deliveryId":"d1","claimNonce":"n1","status":"done"}"""
        val connection = open("POST", "/runtime/v1/events/ack") {
            setRequestProperty("Authorization", "Bearer tx_rt_abc")
            setRequestProperty("Content-Type", "application/json")
            setDoOutput(true)
            outputStream.write(body.toByteArray())
        }
        assertEquals(400, connection.responseCode)
        val text = connection.errorStream.bufferedReader().readText()
        assertTrue(text.contains("不支持的回执状态"))
        assertTrue(text.contains("\"code\":\"invalid_ack_status\""))
    }

    @Test
    fun `release returns ok for matching lease`() {
        gateway.identity = ExternalAgentRuntimeIdentity("bot-1", "laptop")
        gateway.releaseOk = true
        val body = """{"deliveryId":"d1","claimNonce":"n1"}"""
        val connection = open("POST", "/runtime/v1/events/release") {
            setRequestProperty("Authorization", "Bearer tx_rt_abc")
            setRequestProperty("Content-Type", "application/json")
            setDoOutput(true)
            outputStream.write(body.toByteArray())
        }
        assertEquals(200, connection.responseCode)
        assertTrue(gateway.releaseCalled)
    }

    @Test
    fun `heartbeat renews matching leases`() {
        gateway.identity = ExternalAgentRuntimeIdentity("bot-1", "laptop")
        gateway.heartbeatOk = true
        val body = """{"leases":[{"deliveryId":"d1","claimNonce":"n1"},{"deliveryId":"d2","claimNonce":"n2"}]}"""
        val connection = open("POST", "/runtime/v1/events/heartbeat") {
            setRequestProperty("Authorization", "Bearer tx_rt_abc")
            setRequestProperty("Content-Type", "application/json")
            setDoOutput(true)
            outputStream.write(body.toByteArray())
        }
        assertEquals(200, connection.responseCode)
        val text = connection.inputStream.bufferedReader().readText()
        assertTrue(text.contains("\"renewed\":2"))
        assertEquals(2, gateway.heartbeatCount)
    }

    @Test
    fun `lists groups and context for runtime token`() {
        gateway.identity = ExternalAgentRuntimeIdentity("bot-1", "laptop")
        val groups = open("GET", "/runtime/v1/groups") {
            setRequestProperty("Authorization", "Bearer tx_rt_abc")
        }
        assertEquals(200, groups.responseCode)
        assertTrue(groups.inputStream.bufferedReader().readText().contains("\"id\":\"g1\""))
        val context = open("GET", "/runtime/v1/groups/g1/context?limit=10") {
            setRequestProperty("Authorization", "Bearer tx_rt_abc")
        }
        assertEquals(200, context.responseCode)
        val body = context.inputStream.bufferedReader().readText()
        assertTrue(body.contains("\"content\":\"hi\""))
        assertTrue(body.contains("\"senderType\":\"USER\""))
    }

    private fun open(method: String, path: String, configure: HttpURLConnection.() -> Unit = {}): HttpURLConnection {
        val connection = (URL("http://127.0.0.1:$port$path").openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 5_000
            readTimeout = 5_000
            configure()
        }
        // Force the request to run.
        runCatching { connection.responseCode }
        return connection
    }

    private class FakeGateway : ExternalAgentRuntimeGateway {
        var identity: ExternalAgentRuntimeIdentity? = null
        var pendingCount: Int = 0
        var pending: List<ExternalAgentRuntimePendingPreview> = emptyList()
        var claim: ExternalAgentRuntimeClaim? = null
        var claimAfterAttempts: Int = 1
        var claimAttempts: Int = 0
        var ackBatchOk: Boolean = false
        var ackBatchCount: Int = 0
        var releaseOk: Boolean = false
        var releaseCalled: Boolean = false
        var heartbeatOk: Boolean = false
        var heartbeatCount: Int = 0

        override suspend fun authorize(rawToken: String): ExternalAgentRuntimeIdentity? =
            identity?.takeIf { rawToken.startsWith("tx_rt_") }

        override suspend fun session(rawToken: String): ExternalAgentRuntimeSession? {
            val id = authorize(rawToken) ?: return null
            return ExternalAgentRuntimeSession(
                botId = id.botId,
                label = id.label,
                pendingCount = pendingCount,
                groups = listOf(ExternalAgentRuntimeGroupSummary("g1", "Team")),
                claimLeaseTtlMs = ExternalAgentRuntimeServiceDefaults.CLAIM_LEASE_TTL_MS,
            )
        }

        override suspend fun peekPending(rawToken: String, limit: Int): List<ExternalAgentRuntimePendingPreview> {
            authorize(rawToken) ?: return emptyList()
            return pending.take(limit.coerceAtLeast(1))
        }

        override suspend fun claimNext(rawToken: String): ExternalAgentRuntimeClaim? {
            authorize(rawToken) ?: return null
            claimAttempts += 1
            return if (claimAttempts >= claimAfterAttempts) claim else null
        }

        override suspend fun acknowledge(
            rawToken: String,
            deliveryId: String,
            claimNonce: String,
            status: String,
            error: String?,
        ): Boolean = authorize(rawToken) != null

        override suspend fun acknowledgeAll(
            rawToken: String,
            status: String,
            error: String?,
            leases: List<ExternalAgentRuntimeClaimLeaseRef>,
        ): Int {
            if (authorize(rawToken) == null || !ackBatchOk) return 0
            ackBatchCount = leases.size
            return leases.size
        }

        override suspend fun release(rawToken: String, deliveryId: String, claimNonce: String): Boolean {
            releaseCalled = true
            return authorize(rawToken) != null && releaseOk
        }

        override suspend fun heartbeat(rawToken: String, leases: List<ExternalAgentRuntimeClaimLeaseRef>): Int {
            if (authorize(rawToken) == null || !heartbeatOk) return 0
            heartbeatCount = leases.size
            return leases.size
        }

        override suspend fun postMessage(rawToken: String, groupId: String, content: String): String {
            authorize(rawToken) ?: throw SecurityException("令牌无效或已撤销")
            return "msg-1"
        }

        override suspend fun listGroups(rawToken: String): List<ExternalAgentRuntimeGroupSummary> {
            authorize(rawToken) ?: throw SecurityException("令牌无效或已撤销")
            return listOf(ExternalAgentRuntimeGroupSummary("g1", "Team"))
        }

        override suspend fun groupContext(
            rawToken: String,
            groupId: String,
            limit: Int,
        ): List<ExternalAgentRuntimeGroupMessage> {
            authorize(rawToken) ?: throw SecurityException("令牌无效或已撤销")
            return listOf(
                ExternalAgentRuntimeGroupMessage(
                    id = "m1",
                    senderId = "user",
                    senderName = "我",
                    senderType = "USER",
                    content = "hi",
                    timestamp = 1L,
                    seq = 1L,
                ),
            )
        }
    }
}
