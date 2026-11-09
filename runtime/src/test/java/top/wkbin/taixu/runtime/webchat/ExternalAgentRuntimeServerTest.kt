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
        val connection = open("GET", "/runtime/v1/me") {
            setRequestProperty("Authorization", "Bearer tx_rt_abc")
        }
        assertEquals(200, connection.responseCode)
        val body = connection.inputStream.bufferedReader().readText()
        assertTrue(body.contains("\"botId\":\"bot-1\""))
        assertTrue(body.contains("\"label\":\"laptop\""))
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
        var claim: ExternalAgentRuntimeClaim? = null
        var claimAfterAttempts: Int = 1
        var claimAttempts: Int = 0

        override suspend fun authorize(rawToken: String): ExternalAgentRuntimeIdentity? =
            identity?.takeIf { rawToken.startsWith("tx_rt_") }

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

        override suspend fun postMessage(rawToken: String, groupId: String, content: String): String {
            requireNotNull(authorize(rawToken))
            return "msg-1"
        }
    }
}
