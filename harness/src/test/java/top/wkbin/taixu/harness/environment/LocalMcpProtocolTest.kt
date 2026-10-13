package top.wkbin.taixu.harness.environment

import java.lang.reflect.Proxy
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test
import top.wkbin.taixu.core.model.*
import top.wkbin.taixu.harness.mcp.*
import top.wkbin.taixu.runtime.LinuxRuntime
import top.wkbin.taixu.runtime.environment.*
import top.wkbin.taixu.runtime.shell.*

class LocalMcpProtocolTest {
    @Test fun `all bundled local STDIO servers initialize and discover over pipes`() = runBlocking {
        val json = Json { ignoreUnknownKeys = true }
        val runtime = Proxy.newProxyInstance(LinuxRuntime::class.java.classLoader, arrayOf(LinuxRuntime::class.java)) { _, _, _ ->
            error("unbound runtime fallback")
        } as LinuxRuntime
        for (server in BuiltinMcpPresets.presets.filter { it.transportType == McpTransportType.STDIO }) {
            val fake = FakeWorld(ExecutionEnvironmentId("local-proot", "ubuntu", "/workspace", "test"))
            val outputs = Channel<TerminalOutput>(Channel.UNLIMITED)
            val process = object : LinuxSession {
                override var isAlive = true
                override val output = outputs.receiveAsFlow()
                override suspend fun write(data: ByteArray) {
                    val request = json.parseToJsonElement(data.toString(Charsets.UTF_8)).jsonObject
                    val id = request["id"] ?: return
                    val result = if (request["method"]?.jsonPrimitive?.content == "initialize")
                        """{"protocolVersion":"2025-06-18","capabilities":{}}""" else """{"tools":[]}"""
                    outputs.send(TerminalOutput(TerminalStream.STDOUT, """{"jsonrpc":"2.0","id":$id,"result":$result}""" + "\n"))
                }
                override suspend fun resize(columns: Int, rows: Int) = Unit
                override suspend fun interrupt() = Unit
                override suspend fun close() { isAlive = false; outputs.close() }
            }
            val world = object : ExecutionEnvironment by fake {
                override suspend fun openSession(config: SessionConfig): LinuxSession {
                    assertFalse(config.usePty)
                    assertFalse(config.allowSttyResize)
                    assertFalse(config.showBanner)
                    assertFalse(config.commandLine.contains("stty"))
                    assertTrue(config.commandLine.contains("'python3'"))
                    assertTrue(config.commandLine.contains(server.args[1]))
                    return process
                }
            }
            val transport = McpStdioTransport(json, McpCommandBuilder(), LinuxMcpStdioChannelFactory(runtime,
                McpCommandBuilder(), ExecutionEnvironmentFactory { _, _, _ -> world }))
            try {
                assertTrue(transport.check(server).online)
                assertEquals(emptyList<McpToolInfo>(), transport.discover(server))
                assertTrue(process.isAlive)
            } finally { transport.closeConnection(server.id) }
            assertFalse(process.isAlive)
        }
    }
}
