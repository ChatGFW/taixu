package top.wkbin.taixu.harness.codex

import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put
import top.wkbin.taixu.runtime.shell.LinuxSession
import top.wkbin.taixu.runtime.shell.TerminalStream

internal fun JsonObject.obj(key: String) = this[key] as? JsonObject
internal fun JsonObject.arr(key: String) = (this[key] as? JsonArray).orEmpty()
internal fun JsonObject.str(key: String) = (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.contentOrNull
internal fun JsonObject.has(key: String) = this[key]?.let { it != JsonNull } == true

enum class CodexFailure { PROTOCOL, RPC, TIMEOUT, EXITED, WORKSPACE, RUNTIME, SANDBOX, AUTH, HISTORY, STORAGE }

class CodexException(val failure: CodexFailure) : IOException(failure.name)

/** Owns framing only. The workflow owns the process and consumes exactly one ordered stream. */
internal class CodexWire(scope: CoroutineScope, private val session: LinuxSession) {
    val frames = Channel<JsonObject>(capacity = 3)
    private var nextId = 0
    private val reader = scope.launch {
        val pending = StringBuilder()
        try {
            session.output.collect { output ->
                if (output.stream != TerminalStream.STDOUT) return@collect
                val chunk = output.text
                var from = 0
                while (from < chunk.length) {
                    val newline = chunk.indexOf('\n', from)
                    val until = if (newline < 0) chunk.length else newline
                    if (pending.length + until - from > MAX_FRAME_CHARS) throw CodexException(CodexFailure.PROTOCOL)
                    pending.append(chunk, from, until)
                    if (newline < 0) break
                    val line = pending.toString().trim()
                    pending.clear()
                    if (line.startsWith('{')) {
                        val frame = runCatching { Json.parseToJsonElement(line) as? JsonObject }.getOrNull()
                            ?: throw CodexException(CodexFailure.PROTOCOL)
                        frames.send(frame)
                    }
                    from = newline + 1
                }
            }
            frames.close(CodexException(CodexFailure.EXITED))
        } catch (cancelled: CancellationException) {
            frames.cancel(cancelled)
            throw cancelled
        } catch (failure: Exception) {
            frames.close(failure)
        }
    }

    suspend fun request(
        method: String,
        params: JsonObject = JsonObject(emptyMap()),
        onOtherFrame: suspend (JsonObject) -> Unit = {},
    ): JsonObject = withTimeoutOrNull(RPC_TIMEOUT_MS) {
        awaitResponse(sendRequest(method, params), onOtherFrame)
    } ?: throw CodexException(CodexFailure.TIMEOUT)

    private suspend fun awaitResponse(id: Int, onOtherFrame: suspend (JsonObject) -> Unit): JsonObject {
        while (true) {
            val frame = frames.receive()
            if (frame.str("method") == null && frame["id"] == JsonPrimitive(id)) {
                return frame.obj("result") ?: throw CodexException(CodexFailure.RPC)
            }
            onOtherFrame(frame)
        }
    }

    suspend fun sendRequest(method: String, params: JsonObject): Int {
        val id = ++nextId
        send(buildJsonObject {
            put("id", id)
            put("method", method)
            put("params", params)
        })
        return id
    }

    suspend fun initialize() {
        request("initialize", buildJsonObject {
            put("clientInfo", buildJsonObject {
                put("name", "taixu")
                put("title", "TaiXu")
                put("version", "1.0.0")
            })
        }, ::reject)
        send(buildJsonObject { put("method", "initialized") })
    }

    suspend fun decision(id: JsonElement, choice: String) = send(buildJsonObject {
        put("id", id)
        put("result", buildJsonObject { put("decision", choice) })
    })

    suspend fun reject(frame: JsonObject) {
        val method = frame.str("method") ?: return
        val id = frame["id"]?.takeIf { it is JsonPrimitive && it != JsonNull } ?: return
        if (method == COMMAND_APPROVAL || method == FILE_APPROVAL) {
            decision(id, "decline")
        } else {
            send(buildJsonObject {
                put("id", id)
                put("error", buildJsonObject {
                    put("code", -32601)
                    put("message", "Unsupported server request")
                })
            })
        }
    }

    fun stopReader() {
        reader.cancel()
        frames.cancel()
    }

    private suspend fun send(frame: JsonObject) {
        session.write((frame.toString() + "\n").toByteArray(Charsets.UTF_8))
    }
}

private const val MAX_FRAME_CHARS = 1024 * 1024
private const val RPC_TIMEOUT_MS = 20_000L
internal const val COMMAND_APPROVAL = "item/commandExecution/requestApproval"
internal const val FILE_APPROVAL = "item/fileChange/requestApproval"
