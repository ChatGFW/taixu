package top.wkbin.taixu.runtime

import top.wkbin.taixu.runtime.backend.BackendSelector
import top.wkbin.taixu.runtime.shell.CommandResult
import top.wkbin.taixu.runtime.shell.ShellCommand
import top.wkbin.taixu.runtime.shell.ShellExecutor
import top.wkbin.taixu.runtime.scripts.EnvironmentSelfHeal
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class RuntimeHealthChecker(
    private val pathManager: RuntimePathManager,
    private val backendSelector: BackendSelector,
    private val shellExecutor: ShellExecutor,
) {
    private val environmentSelfHeal = EnvironmentSelfHeal(
        pathManager,
        backendSelector,
        shellExecutor,
    )
    private val healScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    suspend fun check(): RuntimeHealth {
        if (!pathManager.isProotInstalled()) {
            return RuntimeHealth(
                status = RuntimeHealthStatus.UNHEALTHY,
                detail = "PRoot 运行组件不完整：缺少或无法读取 APK 内的 ARM64 tracee loader",
            )
        }
        if (!pathManager.isRootfsInstalled()) {
            return RuntimeHealth(
                status = RuntimeHealthStatus.UNHEALTHY,
                detail = "Linux RootFS 校验失败：Bash、/bin/sh 或其 ARM64 ELF 解释器不完整",
            )
        }
        // RootFS 更新会整体替换发行版，旧环境里 apt 装的包与 profile.d 会一起消失：
        // 启动时异步补一次（幂等、缺什么补什么），不阻塞健康检查与冷启动。
        healScope.launch { environmentSelfHeal.heal() }

        val healthMarker = ".taixu-health"
        // 三条 PRoot 命令合并为单次探针：每次冷启动都要完整拉起 proot 进程，
        // 串行 3 次是启动路径上的主要延迟之一。拆分符保证输出仍可精确归位。
        val probeSplit = "__TAIXU_HEALTH_SPLIT__"
        val probeResult = runCatching {
            runCommand(
                ShellCommand(
                    "cat /etc/os-release; echo $probeSplit; uname -m; echo $probeSplit; " +
                        "mkdir -p /workspace && echo taixu-health-ok > /workspace/$healthMarker",
                ),
            )
        }.getOrElse { failedCommandResult(it) }
        val probeParts = probeResult.stdout.split(probeSplit)
        val osRelease = probeParts.getOrNull(0)?.trim().orEmpty()
        val architecture = probeParts.getOrNull(1)?.trim().orEmpty()
        val workspaceFile = File(pathManager.workspaceDir, healthMarker)
        val workspaceWritable = workspaceFile.isFile &&
            workspaceFile.readText().contains("taixu-health-ok")

        val healthy = osRelease.isNotBlank() &&
            architecture.isNotBlank() &&
            workspaceWritable

        return RuntimeHealth(
            status = if (healthy) RuntimeHealthStatus.HEALTHY else RuntimeHealthStatus.UNHEALTHY,
            osRelease = osRelease.ifBlank { null },
            architecture = architecture.ifBlank { null },
            workspaceWritable = workspaceWritable,
            detail = if (healthy) null else buildFailureDetail(probeResult, workspaceWritable),
        )
    }

    private fun buildFailureDetail(
        probe: CommandResult,
        workspaceWritable: Boolean,
    ): String {
        val details = buildList {
            if (probe.isBlankOrFailed()) add(probe.describeFailure("health-probe"))
            if (!workspaceWritable) add("workspace marker missing or unreadable")
        }
        return details.ifEmpty { listOf("Health probe returned empty output") }.joinToString("; ")
    }

    private fun CommandResult.isBlankOrFailed(): Boolean =
        !isSuccess || stdout.isBlank()

    private fun CommandResult.describeFailure(
        label: String,
    ): String {
        val output = (stderr.ifBlank { stdout }).trim().replace(Regex("\\s+"), " ")
        if (output.contains("execve(") && output.contains("No such file or directory")) {
            return "$label exit=$exitCode: PRoot tracee loader 或 Guest ELF 解释器未能启动"
        }
        if (output.contains("the loader was not found", ignoreCase = true)) {
            return "$label exit=$exitCode: PRoot 外置 loader 缺失或不可执行"
        }
        val detail = output.take(240).ifBlank { "no diagnostic output" }
        return "$label exit=$exitCode: $detail"
    }

    private suspend fun runCommand(command: ShellCommand) = shellExecutor.execute(
        command = backendSelector.active().build(
            prootBinary = pathManager.activeProotFile(),
            rootfsDir = pathManager.rootfsDir,
            workspaceDir = pathManager.workspaceDir,
            tmpDir = pathManager.tmpDir,
            // 探针没有会话上下文，只挂 shared，避免把 sessions/ 暴露给这次 guest 进程。
            attachmentsDir = pathManager.sharedAttachmentsDir,
            command = command,
        ),
        timeoutMs = command.timeoutMs,
    )

    private fun failedCommandResult(throwable: Throwable) =
        CommandResult(
            exitCode = 126,
            stdout = "",
            stderr = throwable.message ?: throwable.javaClass.simpleName,
            durationMs = 0L,
        )
}
