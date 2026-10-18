package top.wkbin.taixu.runtime.bridge

import java.util.UUID
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject

/** Native-supplied identity; never inferred from a package's HTTP claims. */
@Serializable
data class PluginHostScope(
    val pluginId: String, val version: String, val generation: Long,
    val origin: String, val environment: String,
)

@Serializable
internal data class PluginHostRequest(val scope: PluginHostScope, val action: String, val target: String, val text: String? = null)

/**
 * Separate authority from the legacy shared bridge key. Only trusted native code can issue tickets
 * after approval. Tickets are one-shot, live at most ten seconds, and never reach Web content/disk.
 * This application-layer boundary does not isolate same-UID PRoot processes.
 */
class PluginHostGateway(
    private val appInfo: () -> String,
    private val now: () -> Long = { System.nanoTime() / 1_000_000 },
    private val clipboardWrite: (String) -> Unit = { error("Clipboard unavailable") },
) {
    internal data class Response(val status: Int, val body: String)
    private class Ticket(val request: PluginHostRequest, val deadline: Long, val epoch: Long, val available: () -> Boolean)
    private val tickets = mutableMapOf<String, Ticket>()
    private val json = Json
    private val monitor = Any()
    private var epoch = 0L

    /** Called inside the approved native executor, using the document identity supplied by its host. */
    fun readAppInfo(scope: PluginHostScope, available: () -> Boolean): String {
        return invoke(PluginHostRequest(scope, "host.app-info", "application"), available)
    }

    /** Exact text is frozen into the one-shot ticket after the native user confirmation. */
    fun writeClipboard(scope: PluginHostScope, text: String, available: () -> Boolean): String =
        invoke(PluginHostRequest(scope, "host.clipboard-write", "clipboard", text), available)

    private fun invoke(request: PluginHostRequest, available: () -> Boolean): String {
        val token = issue(request, available)
        try {
            val response = handle(token, json.encodeToString(request))
            check(response.status == 200) { "Plugin host call denied" }
            return response.body
        } finally { revoke(token) }
    }

    internal fun issue(request: PluginHostRequest, available: () -> Boolean): String = synchronized(monitor) {
        require(supported(request))
        val scope = request.scope
        require(listOf(scope.pluginId, scope.version, scope.origin, scope.environment).all { it.length in 1..256 })
        require(scope.generation >= 0 && scope.origin.startsWith("https://") && scope.environment == "android:local")
        tickets.entries.removeAll { now() >= it.value.deadline }
        check(tickets.size < 256 && available()) { "Plugin host call unavailable" }
        val token = UUID.randomUUID().toString() + UUID.randomUUID().toString()
        tickets[token] = Ticket(request, now() + 10_000, epoch, available)
        token
    }

    /** An HTTP caller cannot mint, renew or widen a ticket, including with the legacy bridge key. */
    internal fun handle(token: String?, body: String): Response {
        val ticket = synchronized(monitor) { tickets.remove(token) } ?: return denied()
        if (!valid(ticket)) return denied()
        if (!boundedRequest(body)) return Response(400, "{\"error\":\"invalid_request\"}")
        val request = runCatching { json.decodeFromString<PluginHostRequest>(body) }.getOrNull()
            ?: return Response(400, "{\"error\":\"invalid_request\"}")
        if (request != ticket.request) return denied()
        if (!valid(ticket)) return denied()
        // No shell command, path or arbitrary executor is accepted. Text was bound at issuance.
        val result = runCatching {
            when (request.action) {
                "host.app-info" -> appInfo()
                "host.clipboard-write" -> { clipboardWrite(requireNotNull(request.text)); "{\"copied\":true}" }
                else -> error("Unsupported operation")
            }
        }.getOrElse { return Response(500, "{\"error\":\"execution_failed\"}") }
        if (!valid(ticket)) return denied()
        if (result.length > 32768) return Response(500, "{\"error\":\"result_too_large\"}")
        return Response(200, result)
    }

    internal fun revoke(token: String) { synchronized(monitor) { tickets.remove(token) } }
    internal fun route(request: HostBridgeHttp.HttpRequest): Response? {
        if (request.path != "/api/plugin/call") return null
        if (request.method != "POST") return Response(404, "{\"error\":\"not_found\"}")
        val auth = request.headers["authorization"].orEmpty()
        return handle(if (auth.startsWith("Bearer ")) auth.removePrefix("Bearer ") else null, request.body)
    }
    fun revokeAll() { synchronized(monitor) { epoch++; tickets.clear() } }
    private fun valid(ticket: Ticket) = synchronized(monitor) {
        ticket.epoch == epoch && now() < ticket.deadline && ticket.available()
    }
    private fun supported(request: PluginHostRequest): Boolean = when (request.action) {
        "host.app-info" -> request.target == "application" && request.text == null
        "host.clipboard-write" -> request.target == "clipboard" && request.text != null &&
            request.text.length in 1..1024 && '\u0000' !in request.text
        else -> false
    }

    /** The envelope has eight fields, optionally text, and two object levels. Bound before decoding. */
    private fun boundedRequest(body: String): Boolean {
        if (body.length > 4096) return false
        var depth = 0
        var fields = 0
        var quoted = false
        var escaped = false
        for (char in body) {
            if (quoted) {
                if (escaped) escaped = false
                else if (char == '\\') escaped = true
                else if (char == '"') quoted = false
            } else when (char) {
                '"' -> quoted = true
                '{', '[' -> if (++depth > 2) return false
                '}', ']' -> if (--depth < 0) return false
                ':' -> if (++fields > 9) return false
            }
        }
        if (quoted || depth != 0 || fields !in 8..9) return false
        return runCatching {
            val root = json.parseToJsonElement(body).jsonObject
            fields == root.size + root.getValue("scope").jsonObject.size
        }.getOrDefault(false)
    }
    private fun denied() = Response(401, "{\"error\":\"plugin_authorization_denied\"}")
}
