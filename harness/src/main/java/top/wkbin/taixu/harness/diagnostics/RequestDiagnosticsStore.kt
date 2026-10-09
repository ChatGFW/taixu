package top.wkbin.taixu.harness.diagnostics

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import top.wkbin.taixu.core.common.logging.SensitiveDataRedactor

data class RequestContextSection(val label: String, val preview: String)

/** A redacted view of a constructed request; this is not proof of successful delivery. */
data class RequestContextSnapshot(
    val sessionId: String,
    val operationId: String,
    val round: Int,
    val attempt: Int,
    val capturedAt: Long,
    val protocol: String,
    val bodyBytes: Long,
    val sections: List<RequestContextSection>,
    val previewTruncated: Boolean,
)

/** Bounded, process-local diagnostics. No raw requests, headers or credentials are retained. */
class RequestDiagnosticsStore(private val redactor: SensitiveDataRedactor) {
    private val json = Json { prettyPrint = true }
    private val mutableSnapshots = MutableStateFlow<Map<String, List<RequestContextSnapshot>>>(emptyMap())
    val snapshots = mutableSnapshots.asStateFlow()

    fun removeSession(sessionId: String) = mutableSnapshots.update { it - sessionId }

    fun record(
        sessionId: String,
        operationId: String,
        round: Int,
        attempt: Int,
        protocol: String,
        body: String,
        bodyBytes: Long,
        secrets: Collection<String> = emptyList(),
    ) {
        // Remove known credentials before parsing, and redact the entire entry before clipping.
        // This also removes secret echoes in a different message in the same request.
        var safeBody = body
        secrets.filter { it.isNotEmpty() }.distinct().sortedByDescending { it.length }.forEach {
            safeBody = safeBody.replace(it, "[REDACTED]")
        }
        val root = json.parseToJsonElement(safeBody) as? JsonObject ?: return
        val cleaned = stripMedia(root) as JsonObject
        val allSections = buildList {
            cleaned.forEach { (key, value) ->
                if ((key == "messages" || key == "input") && value is JsonArray) {
                    value.forEachIndexed { index, item ->
                        val role = (item as? JsonObject)?.let {
                            (it["role"] as? JsonPrimitive)?.contentOrNull
                                ?: (it["type"] as? JsonPrimitive)?.contentOrNull
                        }.orEmpty().takeIf { it in SECTION_ROLES }.orEmpty()
                        add(RequestContextSection("$key[${index + 1}] $role", json.encodeToString(JsonElement.serializer(), item)))
                    }
                } else {
                    add(RequestContextSection(key, json.encodeToString(JsonElement.serializer(), value)))
                }
            }
        }
        // Redaction may make a numeric JSON value non-JSON; previews intentionally remain text.
        val previews = redactor.redact(allSections.joinToString("\u0000") { it.preview }).split('\u0000')
        var remaining = MAX_PREVIEW_CHARS
        var truncated = allSections.size > MAX_SECTIONS
        val sections = allSections.zip(previews).take(MAX_SECTIONS).mapNotNull { (section, preview) ->
            if (remaining <= 0) { truncated = true; return@mapNotNull null }
            val length = minOf(preview.length, MAX_SECTION_CHARS, remaining)
            if (length < preview.length) truncated = true
            remaining -= length
            section.copy(preview = preview.take(length))
        }
        val snapshot = RequestContextSnapshot(
            sessionId, operationId, round, attempt, System.currentTimeMillis(),
            protocol, bodyBytes, sections, truncated,
        )
        mutableSnapshots.update { existing ->
            val history = (existing[sessionId].orEmpty().filter { it.operationId == operationId } + snapshot)
                .takeLast(MAX_REQUESTS_PER_SESSION)
            // Keep the recently recorded sessions, including when an existing key is updated.
            ((existing - sessionId) + (sessionId to history)).entries.toList().takeLast(MAX_SESSIONS)
                .associate { it.key to it.value }
        }
    }

    private fun stripMedia(element: JsonElement, key: String = ""): JsonElement = when {
        key in setOf("data", "encrypted_content", "signature") -> JsonPrimitive("[BINARY_OR_OPAQUE_OMITTED]")
        element is JsonObject -> JsonObject(element.mapValues { (name, value) -> stripMedia(value, name) })
        element is JsonArray -> JsonArray(element.map { stripMedia(it) })
        element is JsonPrimitive && element.isString && element.content.startsWith("data:", true) ->
            JsonPrimitive("[MEDIA_OMITTED]")
        else -> element
    }

    companion object {
        private val SECTION_ROLES = setOf("system", "user", "assistant", "tool", "function_call", "function_call_output", "message", "reasoning")
        const val MAX_PREVIEW_CHARS = 64_000
        const val MAX_SECTION_CHARS = 8_000
        const val MAX_SECTIONS = 128
        const val MAX_SESSIONS = 8
        const val MAX_REQUESTS_PER_SESSION = 2
    }
}
