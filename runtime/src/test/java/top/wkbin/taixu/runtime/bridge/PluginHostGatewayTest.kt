package top.wkbin.taixu.runtime.bridge

import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

class PluginHostGatewayTest {
    private val scope = PluginHostScope("example.web", "1.0.0", 7, "https://document.plugin.taixu.invalid", "android:local")
    private val request = PluginHostRequest(scope, "host.app-info", "application")
    private fun body(value: PluginHostRequest = request) = Json.encodeToString(value)

    @Test fun clipboardTicketBindsExactTextAndCannotBeUsedForAppInfoOrAnotherTarget() {
        val copied = mutableListOf<String>()
        val gateway = PluginHostGateway({ error("wrong operation") }, clipboardWrite = { copied += it })
        val copy = PluginHostRequest(scope, "host.clipboard-write", "clipboard", "已确认文本")
        listOf(copy.copy(text = "替换内容"), copy.copy(target = "application"), request).forEach { changed ->
            val token = gateway.issue(copy) { true }
            assertEquals(401, gateway.handle(token, body(changed)).status)
            assertEquals(401, gateway.handle(token, body(copy)).status)
        }
        assertTrue(copied.isEmpty())
        val token = gateway.issue(copy) { true }
        val result = gateway.handle(token, body(copy))
        assertEquals(200, result.status)
        assertEquals("{\"copied\":true}", result.body)
        assertFalse(result.body.contains("已确认文本"))
        assertEquals(401, gateway.handle(token, body(copy)).status)
        assertEquals(listOf("已确认文本"), copied)
    }

    @Test fun clipboardRejectsUndeclaredTargetsReadAccessAndMalformedInputAtIssuance() {
        val gateway = PluginHostGateway({ "info" })
        listOf(PluginHostRequest(scope, "host.clipboard-read", "clipboard"),
            PluginHostRequest(scope, "host.clipboard-write", "other", "text"),
            PluginHostRequest(scope, "host.clipboard-write", "clipboard"),
            PluginHostRequest(scope, "host.clipboard-write", "clipboard", ""),
            PluginHostRequest(scope, "host.clipboard-write", "clipboard", "a".repeat(1025)),
            PluginHostRequest(scope, "host.clipboard-write", "clipboard", "a\u0000b"),
            request.copy(text = "smuggled"),
        ).forEach { assertTrue(runCatching { gateway.issue(it) { true } }.isFailure) }
    }

    @Test fun revokedAndExpiredClipboardTicketsNeverWriteAndFreshNativeCallsWriteOnce() {
        var time = 0L
        var active = true
        val copied = mutableListOf<String>()
        val gateway = PluginHostGateway({ "info" }, { time }, { copied += it })
        val copy = PluginHostRequest(scope, "host.clipboard-write", "clipboard", "text")
        val expired = gateway.issue(copy) { active }
        time = 10_000
        assertEquals(401, gateway.handle(expired, body(copy)).status)
        val stopped = gateway.issue(copy) { active }
        active = false
        assertEquals(401, gateway.handle(stopped, body(copy)).status)
        assertTrue(copied.isEmpty())
        active = true
        assertEquals("{\"copied\":true}", gateway.writeClipboard(scope, "text") { active })
        assertEquals(listOf("text"), copied)
    }

    @Test fun clipboardFailureDoesNotExposeTextAndCannotBeReplayed() {
        val gateway = PluginHostGateway({ "info" }, clipboardWrite = { error("secret copied text") })
        val copy = PluginHostRequest(scope, "host.clipboard-write", "clipboard", "secret copied text")
        val token = gateway.issue(copy) { true }
        val result = gateway.handle(token, body(copy))
        assertEquals(500, result.status)
        assertFalse(result.body.contains("secret"))
        assertEquals(401, gateway.handle(token, body(copy)).status)
    }

    @Test fun nativeCallAndHttpCallUseTheSameOneShotAuthority() {
        var calls = 0
        val gateway = PluginHostGateway({ calls++; "{\"package\":\"example\"}" })
        assertEquals("{\"package\":\"example\"}", gateway.readAppInfo(scope) { true })
        val token = gateway.issue(request) { true }
        assertEquals(200, gateway.handle(token, body()).status)
        assertEquals(401, gateway.handle(token, body()).status)
        assertEquals(2, calls)
    }

    @Test fun everyIdentityDimensionAndCapabilityTargetMustMatch() {
        val gateway = PluginHostGateway({ error("mismatched authority must not execute") })
        val changes = listOf(
            request.copy(scope = scope.copy(pluginId = "other.web")),
            request.copy(scope = scope.copy(version = "2.0.0")),
            request.copy(scope = scope.copy(generation = 8)),
            request.copy(scope = scope.copy(origin = "https://other.plugin.taixu.invalid")),
            request.copy(scope = scope.copy(environment = "remote:ssh")),
            request.copy(action = "host.shell"), request.copy(target = "other"),
        )
        changes.forEach {
            val token = gateway.issue(request) { true }
            assertEquals(401, gateway.handle(token, body(it)).status)
            assertEquals(401, gateway.handle(token, body()).status)
        }
    }

    @Test fun expirationIsExactAndCannotBeRenewedByReplay() {
        var time = 0L
        val gateway = PluginHostGateway({ "ok" }, { time })
        val token = gateway.issue(request) { true }
        time = 10_000
        assertEquals(401, gateway.handle(token, body()).status)
        assertEquals(401, gateway.handle(token, body()).status)
        assertEquals("ok", gateway.readAppInfo(scope) { true })
    }

    @Test fun revocationAndUnavailableOwnerDenyOutstandingTickets() {
        var available = true
        val gateway = PluginHostGateway({ error("revoked ticket executed") })
        val token = gateway.issue(request) { available }
        available = false
        assertEquals(401, gateway.handle(token, body()).status)
        assertTrue(runCatching { gateway.issue(request) { available } }.isFailure)
        available = true
        val removed = gateway.issue(request) { true }
        gateway.revoke(removed)
        assertEquals(401, gateway.handle(removed, body()).status)
        val stopped = gateway.issue(request) { true }
        gateway.revokeAll()
        assertEquals(401, gateway.handle(stopped, body()).status)
    }

    @Test fun stopAndOwnerChangesDuringExecutionSuppressResults() {
        lateinit var gateway: PluginHostGateway
        gateway = PluginHostGateway({ gateway.revokeAll(); "private result" })
        assertEquals(401, gateway.handle(gateway.issue(request) { true }, body()).status)
        var active = true
        val other = PluginHostGateway({ active = false; "private result" })
        assertEquals(401, other.handle(other.issue(request) { active }, body()).status)
    }

    @Test fun concurrentReplayExecutesAtMostOnce() {
        val calls = java.util.concurrent.atomic.AtomicInteger()
        val gateway = PluginHostGateway({ calls.incrementAndGet(); "ok" })
        val token = gateway.issue(request) { true }
        val pool = Executors.newFixedThreadPool(8)
        try {
            val results = pool.invokeAll(List(16) { Callable { gateway.handle(token, body()).status } })
            assertEquals(1, results.count { it.get() == 200 })
            assertEquals(1, calls.get())
        } finally { pool.shutdownNow() }
    }

    @Test fun malformedUnknownAndOversizedRequestsConsumeTheirTicket() {
        val gateway = PluginHostGateway({ error("invalid request executed") })
        listOf("{", body().dropLast(1) + ",\"command\":\"id\"}", " ".repeat(4097),
            "[".repeat(1000) + "]".repeat(1000), body().dropLast(1) + ",\"target\":\"application\"}").forEach {
            val token = gateway.issue(request) { true }
            assertEquals(400, gateway.handle(token, it).status)
            assertEquals(401, gateway.handle(token, body()).status)
        }
    }

    @Test fun nativeIssuerRejectsUndeclaredOperationsAndBoundsLiveTickets() {
        var time = 0L
        val gateway = PluginHostGateway({ "ok" }, { time })
        assertTrue(runCatching { gateway.issue(request.copy(action = "host.shell")) { true } }.isFailure)
        assertTrue(runCatching { gateway.issue(request.copy(target = "/workspace")) { true } }.isFailure)
        repeat(256) { gateway.issue(request) { true } }
        assertTrue(runCatching { gateway.issue(request) { true } }.isFailure)
        time = 10_000
        assertEquals("ok", gateway.readAppInfo(scope) { true })
    }

    @Test fun executorErrorsAndOversizedResultsAreBoundedAndRedacted() {
        val gateway = PluginHostGateway({ error("secret credential") })
        val failure = gateway.handle(gateway.issue(request) { true }, body())
        assertEquals(500, failure.status)
        assertFalse(failure.body.contains("secret"))
        val large = PluginHostGateway({ "a".repeat(32769) })
        assertEquals(500, large.handle(large.issue(request) { true }, body()).status)
    }

    @Test fun pluginRouteCannotUseLegacyKeyOrMintTicketsAndMatchesExactPath() {
        val gateway = PluginHostGateway({ error("legacy key must not execute") })
        fun route(path: String, method: String = "POST", auth: String = "Bearer legacy-shared-key") =
            gateway.route(HostBridgeHttp.HttpRequest(method, path, mapOf("authorization" to auth), body()))
        assertEquals(401, route("/api/plugin/call")!!.status)
        assertEquals(404, route("/api/plugin/call", "GET")!!.status)
        assertNull(route("/api/plugin/issue"))
        assertNull(route("/api/plugin/call/extra"))
        assertNull(route("/api/shell"))
    }

    @Test fun loopbackTransportReadsUtf8BytesAndNeverReturnsCredentials() {
        val gateway = PluginHostGateway({ "{\"package\":\"太墟\"}" })
        val unicode = request.copy(scope = scope.copy(pluginId = "示例.web"))
        val token = gateway.issue(unicode) { true }
        val server = ServerSocket(0, 1, InetAddress.getLoopbackAddress())
        val pool = Executors.newSingleThreadExecutor()
        try {
            val handled = pool.submit {
                server.accept().use { socket ->
                    socket.soTimeout = 2000
                    val parsed = HostBridgeHttp.parseHttpRequest(socket.getInputStream())!!
                    val result = gateway.route(parsed)!!
                    HostBridgeHttp.writeResponse(socket, result.status, result.body)
                }
            }
            Socket(InetAddress.getLoopbackAddress(), server.localPort).use { client ->
                client.soTimeout = 2000
                val bytes = body(unicode).toByteArray(Charsets.UTF_8)
                val head = "POST /api/plugin/call HTTP/1.1\r\nAuthorization: Bearer $token\r\nContent-Length: ${bytes.size}\r\n\r\n"
                client.getOutputStream().write(head.toByteArray(Charsets.US_ASCII) + bytes)
                val result = client.getInputStream().readBytes().toString(Charsets.UTF_8)
                assertTrue(result.startsWith("HTTP/1.1 200"))
                assertTrue(result.endsWith("{\"package\":\"太墟\"}"))
                assertFalse(result.contains(token))
            }
            handled.get(3, TimeUnit.SECONDS)
        } finally { server.close(); pool.shutdownNow() }
    }
}
