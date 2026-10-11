package top.wkbin.taixu.harness.mcp

import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.job
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import top.wkbin.taixu.core.model.McpServerConfig
import top.wkbin.taixu.runtime.LinuxRuntime
import top.wkbin.taixu.runtime.environment.ExecutionEnvironmentContext
import top.wkbin.taixu.runtime.environment.ExecutionEnvironmentFactory
import top.wkbin.taixu.runtime.environment.ExecutionEnvironmentId
import top.wkbin.taixu.runtime.shell.SessionConfig

/** Failed startup remains owned until disposal, invalidation or successful pre-reconnect cleanup. */
class LinuxMcpStdioChannelFactory(
    private val linuxRuntime: LinuxRuntime,
    private val commandBuilder: McpCommandBuilder,
    private val environments: ExecutionEnvironmentFactory? = null,
) : McpStdioChannelFactory {
    private data class Key(val server: String, val environment: ExecutionEnvironmentId?)
    private val locks = ConcurrentHashMap<Key, Mutex>()
    private val acquisitions = ConcurrentHashMap<Key, McpChannelAcquisition>()

    override suspend fun open(server: McpServerConfig): McpStdioChannel {
        val config = SessionConfig(workingDirectory = "/root", environment = server.env,
            commandLine = commandBuilder.commandLine(server), allowSttyResize = false)
        val binding = currentCoroutineContext()[ExecutionEnvironmentContext]
        val key = Key(server.id, binding?.environment?.id)
        return locks.getOrPut(key) { Mutex() }.withLock {
            acquisitions[key]?.close()
            if (binding == null && environments == null && !awaitLinuxRuntimeReady(linuxRuntime.state)) {
                error("Linux runtime is not ready. Call initialize() first.")
            }
            var acquired: McpChannelAcquisition? = null
            try {
                coroutineScope {
                    val owned = McpChannelAcquisition(server.id.mcpServerKey(), currentCoroutineContext().job) {
                        acquisitions.remove(key, it)
                    }
                    acquired = owned
                    acquisitions[key] = owned
                    withContext(ActiveMcpAcquisition(owned)) {
                        owned.ownership = binding?.own { owned.close() }
                        checkCreation(owned)
                        val environment = binding?.environment ?: environments?.open("mcp:${server.id}", "", null)
                        if (binding == null) owned.environment = environment
                        checkCreation(owned)
                        owned.session = environment?.openSession(config) ?: linuxRuntime.startSession(config)
                        checkCreation(owned)
                        val channel = LinuxMcpStdioChannel(server.id, owned.session!!, environment = owned.environment)
                        owned.channel = channel
                        channel.ownership = AutoCloseable { owned.detached() }
                        checkCreation(owned)
                        channel
                    }
                }
            } catch (t: Throwable) { rollbackMcpFailure(t) { acquired?.close(drainCreation = false) } }
        }
    }

    private suspend fun checkCreation(owned: McpChannelAcquisition) {
        currentCoroutineContext().ensureActive()
        check(!owned.closing) { "MCP startup was disposed" }
    }

    override suspend fun closePending(serverId: String) {
        val records = acquisitions.values.filter { it.serverId == serverId.mcpServerKey() }
        closeMcpActions(records.map { owned -> suspend { owned.close() } })
    }
}
