package top.wkbin.taixu.harness.codex

import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import top.wkbin.taixu.runtime.shell.LinuxSession
import top.wkbin.taixu.runtime.shell.TerminalOutput
import top.wkbin.taixu.runtime.shell.TerminalStream

class CodexTurnRelayTest {
    @Test
    fun streamsTextAndOnlyAnswersTheVisibleRequestOnce() = runTest {
        val session = FakeProtocolSession()
        val wire = CodexWire(backgroundScope, session)
        val control = Channel<CodexInstruction>(8)
        val events = Channel<CodexEvent>(32)
        val relay = CodexTurnRelay(wire, "t1", { it.replace("secret", "***") }) { events.trySend(it) }
        val turn = async { relay.run(codexTurnParams("t1", "/workspace/demo", "readOnly", "review"), control) }
        val start = session.sent.receive()
        val policy = start.obj("params")?.obj("sandboxPolicy")
        assertEquals("readOnly", policy?.str("type"))
        assertEquals(JsonPrimitive(false), policy?.get("networkAccess"))

        session.feed("""{"method":"item/agentMessage/delta","params":{"threadId":"t1","turnId":"r1","itemId":"m1","delta":"sec"}}""")
        session.feed("""{"id":${start["id"]},"result":{"turn":{"id":"r1"}}}""")
        assertEquals(CodexEvent.Started("r1"), events.receive())
        assertEquals("sec", (events.receive() as CodexEvent.Message).line.text)
        session.feed("""{"method":"item/agentMessage/delta","params":{"threadId":"t1","turnId":"r1","itemId":"m1","delta":"ret"}}""")
        assertEquals("***", (events.receive() as CodexEvent.Message).line.text)

        session.feed("""{"id":"foreign","method":"$COMMAND_APPROVAL","params":{"threadId":"t1","turnId":"old","itemId":"c0"}}""")
        val foreign = session.sent.receive()
        assertEquals(JsonPrimitive("foreign"), foreign["id"])
        assertEquals("decline", foreign.obj("result")?.str("decision"))
        session.feed("""{"id":"permission","method":"$COMMAND_APPROVAL","params":{"threadId":"t1","turnId":"r1","itemId":"c1","command":"pwd"}}""")
        val approval = (events.receive() as CodexEvent.Approvals).requests.single()
        assertEquals("pwd", approval.summary)
        control.send(CodexInstruction.Decline("wrong-token"))
        control.send(CodexInstruction.Decline(approval.token))
        control.send(CodexInstruction.Decline(approval.token))
        val rejected = session.sent.receive()
        assertEquals(JsonPrimitive("permission"), rejected["id"])
        assertEquals("decline", rejected.obj("result")?.str("decision"))
        assertEquals(emptyList<CodexApproval>(), (events.receive() as CodexEvent.Approvals).requests)

        session.feed("""{"method":"turn/completed","params":{"threadId":"t1","turn":{"id":"r1","status":"completed"}}}""")
        assertEquals(CodexEvent.Finished(CodexEnd.COMPLETED), events.receive())
        turn.await()
        assertNull(session.sent.tryReceive().getOrNull())
        wire.stopReader()
    }

    @Test
    fun stopCancelsOutstandingRequestAndTargetsTheActiveTurn() = runTest {
        val session = FakeProtocolSession()
        val wire = CodexWire(backgroundScope, session)
        val control = Channel<CodexInstruction>(8)
        val events = Channel<CodexEvent>(32)
        val relay = CodexTurnRelay(wire, "t1", { it }) { events.trySend(it) }
        val turn = async { relay.run(codexTurnParams("t1", "/workspace/demo", "workspaceWrite", "work"), control) }
        val start = session.sent.receive()
        session.feed("""{"id":${start["id"]},"result":{"turn":{"id":"r1"}}}""")
        assertEquals(CodexEvent.Started("r1"), events.receive())
        session.feed("""{"id":19,"method":"$FILE_APPROVAL","params":{"threadId":"t1","turnId":"r1","itemId":"c1"}}""")
        assertTrue((events.receive() as CodexEvent.Approvals).requests.single().fileChange)

        control.send(CodexInstruction.Interrupt)
        val cancelled = session.sent.receive()
        assertEquals(JsonPrimitive(19), cancelled["id"])
        assertEquals("cancel", cancelled.obj("result")?.str("decision"))
        val interrupt = session.sent.receive()
        assertEquals("turn/interrupt", interrupt.str("method"))
        assertEquals("t1", interrupt.obj("params")?.str("threadId"))
        assertEquals("r1", interrupt.obj("params")?.str("turnId"))
        assertFalse(turn.isCompleted)
        session.feed("""{"method":"turn/completed","params":{"threadId":"t1","turn":{"id":"r1","status":"interrupted"}}}""")
        turn.await()
        wire.stopReader()
    }

    private class FakeProtocolSession : LinuxSession {
        private val incoming = Channel<TerminalOutput>(16)
        val sent = Channel<JsonObject>(16)
        override val output = incoming.receiveAsFlow()
        override val isAlive = true

        suspend fun feed(json: String) {
            val midpoint = json.length / 2
            incoming.send(TerminalOutput(TerminalStream.STDOUT, json.take(midpoint)))
            incoming.send(TerminalOutput(TerminalStream.STDOUT, json.drop(midpoint) + "\n"))
        }

        override suspend fun write(data: ByteArray) {
            sent.send(Json.parseToJsonElement(data.toString(Charsets.UTF_8)) as JsonObject)
        }

        override suspend fun resize(columns: Int, rows: Int) = Unit
        override suspend fun interrupt() = Unit
        override suspend fun close() { incoming.close() }
    }
}
