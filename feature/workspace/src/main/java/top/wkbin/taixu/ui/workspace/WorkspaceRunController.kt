package top.wkbin.taixu.ui.workspace

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import top.wkbin.taixu.runtime.LinuxRuntime
import top.wkbin.taixu.runtime.WorkspaceManager
import top.wkbin.taixu.runtime.shell.ManagedProcess
import top.wkbin.taixu.runtime.shell.ProcessType

internal data class WorkspaceRun(val config: WorkspaceRunConfig, val process: ManagedProcess)

/** The process registry keeps the command and logs alive when the run screen is closed. */
internal class WorkspaceRunController(
    private val workspaceManager: WorkspaceManager,
    private val runtime: LinuxRuntime,
) {
    private val mutex = Mutex()
    private val runs = mutableMapOf<String, WorkspaceRun>()

    suspend fun current(project: String): WorkspaceRun? = mutex.withLock { runs[project] }

    suspend fun start(project: String, config: WorkspaceRunConfig): WorkspaceRun = mutex.withLock {
        check(runs[project]?.process?.session?.isAlive != true) { "请先停止此项目正在运行的命令" }
        val distro = runtime.activeDistroId.value
        val directory = workspaceManager.linuxWorkingDirectory(project)
        val id = "workspace-run:$project"
        runs[project]?.let { runtime.stopBackgroundInstance(it.process) }
        runtime.clearBackgroundLogs(id)
        val process = runtime.startBackground(
            id = id,
            command = config.shellCommand(directory),
            type = ProcessType.COMMAND,
            distroId = distro,
        )
        WorkspaceRun(config, process).also { runs[project] = it }
    }

    suspend fun stop(project: String, run: WorkspaceRun) = mutex.withLock {
        if (runs[project] === run) {
            check(runtime.stopBackgroundInstance(run.process) || !run.process.session.isAlive) { "未能停止运行命令" }
        }
    }

    fun logs(run: WorkspaceRun) = runtime.observeBackgroundLogs(run.process.id)
}
