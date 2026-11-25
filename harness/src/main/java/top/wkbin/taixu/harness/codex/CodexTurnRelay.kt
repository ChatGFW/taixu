package top.wkbin.taixu.harness.codex

import java.util.UUID
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.selects.select
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** One receiver for each turn so a request can only be answered by its owning stream. */
internal class CodexTurnRelay(
    private val wire: CodexWire,
    private val threadId: String,
    private val redact: (String) -> String,
    private val emit: (CodexEvent) -> Unit,
) {
    private val pending = linkedMapOf<String, Pending>()
    private val deltas = linkedMapOf<String, String>()
    private var stopped = false

    suspend fun run(
        input: JsonObject,
        control: ReceiveChannel<CodexInstruction>,
        onAccepted: suspend () -> Unit = {},
    ) {
        val early = ArrayDeque<JsonObject>()
        val result = wire.request("turn/start", input) { frame ->
            val method = frame.str("method").orEmpty()
            if (frame.obj("params")?.str("threadId") == threadId && method in LIVE_METHODS) {
                if (early.size >= MAX_EARLY_FRAMES) throw CodexException(CodexFailure.PROTOCOL)
                early.addLast(frame)
            } else {
                wire.reject(frame)
            }
        }
        val turnId = result.obj("turn")?.str("id")?.takeIf(::isCodexThreadId)
            ?: throw CodexException(CodexFailure.PROTOCOL)
        emit(CodexEvent.Started(turnId))
        try {
            onAccepted()
            while (early.isNotEmpty()) {
                if (onFrame(turnId, early.removeFirst())) return
            }
            while (true) {
                val finished = select {
                    control.onReceive { instruction ->
                        onInstruction(turnId, instruction)
                        false
                    }
                    wire.frames.onReceive { frame -> onFrame(turnId, frame) }
                }
                if (finished) return
            }
        } finally {
            pending.clear()
            publishApprovals()
        }
    }

    private suspend fun onInstruction(turnId: String, instruction: CodexInstruction) {
        when (instruction) {
            is CodexInstruction.Decline -> {
                val request = pending.remove(instruction.token) ?: return
                wire.decision(request.id, "decline")
                publishApprovals()
            }
            CodexInstruction.Interrupt -> {
                if (stopped) return
                stopped = true
                for (request in pending.values) wire.decision(request.id, "cancel")
                pending.clear()
                publishApprovals()
                emit(CodexEvent.Interrupting)
                wire.sendRequest("turn/interrupt", buildJsonObject {
                    put("threadId", threadId)
                    put("turnId", turnId)
                })
            }
        }
    }

    private suspend fun onFrame(turnId: String, frame: JsonObject): Boolean {
        val method = frame.str("method") ?: return false
        val params = frame.obj("params")
        val id = (frame["id"] as? JsonPrimitive)?.takeIf { it.content.length <= 128 && frame.has("id") }
        if (method == "serverRequest/resolved" && params?.str("threadId") == threadId && id == null) {
            if (pending.entries.removeAll { it.value.id == params["requestId"] }) publishApprovals()
            return false
        }
        if (params == null || params.str("threadId") != threadId ||
            (params.str("turnId") ?: params.obj("turn")?.str("id")) != turnId
        ) {
            wire.reject(frame)
            return false
        }
        if (id != null) {
            if (method == COMMAND_APPROVAL || method == FILE_APPROVAL) {
                presentApproval(id, method, params)
            } else {
                wire.reject(frame)
            }
            return false
        }
        when (method) {
            "item/agentMessage/delta" -> append(params)
            "item/completed" -> {
                val line = params.obj("item")?.toCodexLine(redact) ?: return false
                if (!line.user) {
                    deltas.remove(line.id)
                    emit(CodexEvent.Message(line))
                }
            }
            "turn/completed" -> {
                when (params.obj("turn")?.str("status")) {
                    "completed" -> emit(CodexEvent.Finished(CodexEnd.COMPLETED))
                    "interrupted" -> emit(CodexEvent.Finished(CodexEnd.INTERRUPTED))
                    "failed" -> emit(CodexEvent.Finished(CodexEnd.FAILED))
                    else -> throw CodexException(CodexFailure.PROTOCOL)
                }
                return true
            }
        }
        return false
    }

    private suspend fun presentApproval(id: JsonPrimitive, method: String, params: JsonObject) {
        if (pending.values.any { it.id == id }) return
        if (stopped || pending.size >= 6 || params.str("itemId").isNullOrBlank()) {
            wire.decision(id, if (stopped) "cancel" else "decline")
            return
        }
        val token = UUID.randomUUID().toString()
        val summary = listOfNotNull(params.str("command"), params.str("reason"))
            .joinToString("\n").take(2_048).let(redact)
        val request = CodexApproval(token, method == FILE_APPROVAL, summary)
        pending[token] = Pending(id, request)
        publishApprovals()
    }

    private fun append(params: JsonObject) {
        val itemId = params.str("itemId")?.takeIf(::isCodexThreadId) ?: return
        val delta = params.str("delta") ?: return
        val previous = deltas[itemId].orEmpty()
        if (previous.length >= CODEX_MAX_TEXT || (itemId !in deltas && deltas.size >= 64)) return
        val raw = previous + delta.take(CODEX_MAX_TEXT - previous.length)
        deltas[itemId] = raw
        emit(CodexEvent.Message(CodexLine(itemId, user = false, redact(raw).take(CODEX_MAX_TEXT))))
    }

    private fun publishApprovals() = emit(CodexEvent.Approvals(pending.values.map { it.request }))

    private data class Pending(val id: JsonPrimitive, val request: CodexApproval)

    private companion object {
        const val MAX_EARLY_FRAMES = 16
        val LIVE_METHODS = setOf("item/agentMessage/delta", "item/completed", "turn/completed", COMMAND_APPROVAL, FILE_APPROVAL)
    }
}
