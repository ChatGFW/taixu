package top.wkbin.taixu.harness.mcp

import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.job
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import top.wkbin.taixu.core.common.shell.ShellQuote
import top.wkbin.taixu.core.model.McpServerConfig
import top.wkbin.taixu.runtime.LinuxRuntime
import top.wkbin.taixu.runtime.environment.ExecutionEnvironment
import top.wkbin.taixu.runtime.environment.ExecutionEnvironmentContext
import top.wkbin.taixu.runtime.environment.ExecutionEnvironmentFactory
import top.wkbin.taixu.runtime.environment.ExecutionEnvironmentId
import top.wkbin.taixu.runtime.shell.SessionConfig
import top.wkbin.taixu.runtime.shell.ShellCommand

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
        commandBuilder.commandLine(server) // Validate before acquiring resources.
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
                        val prepared = mcpExecutionConfig(server, environment)
                        val config = SessionConfig(workingDirectory = environment?.workingDirectory() ?: "/root", environment = prepared.env,
                            commandLine = commandBuilder.commandLine(prepared, configureTty = false), allowSttyResize = false, usePty = false)
                        requireMcpExecutable(environment, prepared)
                        checkCreation(owned)
                        owned.session = environment?.openSession(config) ?: linuxRuntime.startSession(config)
                        checkCreation(owned)
                        val channel = LinuxMcpStdioChannel(server.id, owned.session!!, environment = owned.environment, secretValues = prepared.env.values)
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

    /**
     * 远程执行环境下 STDIO MCP 的可执行文件在服务器上可能根本不存在：直接拉起会以 shell 退出码 127
     * 静默失败（initialize 无响应，最终只表现为健康检查超时）。先探测一次，给出可读原因。
     */
    private suspend fun requireMcpExecutable(environment: ExecutionEnvironment?, server: McpServerConfig) {
        if (environment == null || environment.id.backend != REMOTE_BACKEND) return
        val probe = environment.execute(
            ShellCommand(commandLine = "command -v " + ShellQuote.of(server.command), workingDirectory = environment.workingDirectory(), environment = server.env),
        )
        if (probe.exitCode != 0) {
            error(
                "该 MCP 的可执行文件 `${server.command}` 在远程 ${environment.label} 上不存在，" +
                    "请在服务器安装后重试，或为该连接改用本地环境。",
            )
        }
    }

    override suspend fun closePending(serverId: String) {
        val records = acquisitions.values.filter { it.serverId == serverId.mcpServerKey() }
        closeMcpActions(records.map { owned -> suspend { owned.close() } })
    }

    private companion object {
        /** 远程执行环境的 backend 标识（与 SelectedExecutionEnvironmentFactory 同源）。 */
        const val REMOTE_BACKEND = "ssh"
    }
}
