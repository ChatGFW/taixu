package top.wkbin.taixu.ui.workspace

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import top.wkbin.taixu.core.common.result.AppResult
import top.wkbin.taixu.runtime.WorkspaceManager
import top.wkbin.taixu.runtime.inspectFile

internal data class WorkspaceRunState(
    val configs: List<WorkspaceRunConfig> = emptyList(),
    val hasFile: Boolean = false,
    val loaded: Boolean = false,
    val busy: Boolean = false,
    val run: WorkspaceRun? = null,
    val running: Boolean = false,
    val logs: List<String> = emptyList(),
    val error: String? = null,
)

internal class WorkspaceRunViewModel(
    private val workspace: WorkspaceManager,
    private val runner: WorkspaceRunController,
) : ViewModel() {
    private val mutableState = MutableStateFlow(WorkspaceRunState())
    val state = mutableState.asStateFlow()
    private var monitor: Job? = null

    fun load(project: String) = action {
        monitor(runner.current(project))
        val entry = workspace.listFiles(project).value().firstOrNull { it.name == WORKSPACE_RUN_FILE }
        mutableState.update { it.copy(loaded = true, hasFile = entry != null, configs = emptyList()) }
        if (entry != null) {
            val inspection = workspace.inspectFile(project, WORKSPACE_RUN_FILE).value()
            require(!inspection.binary && inspection.sizeBytes <= WORKSPACE_RUN_MAX_BYTES) { "运行配置必须是 64 KiB 以内的文本文件" }
            val configs = parseWorkspaceRunConfigs(workspace.readFile(project, WORKSPACE_RUN_FILE).value())
            mutableState.update { it.copy(configs = configs) }
        }
    }

    fun createConfig(project: String, onOpen: () -> Unit) = action {
        workspace.createFile(project, WORKSPACE_RUN_FILE).value()
        mutableState.update { it.copy(hasFile = true) }
        workspace.writeFile(project, WORKSPACE_RUN_FILE, workspaceRunSample, expectedContent = "").value()
        onOpen()
    }

    fun start(project: String, config: WorkspaceRunConfig) = action {
        monitor(runner.start(project, config))
    }

    fun stop(project: String) = action {
        mutableState.value.run?.let { run ->
            runner.stop(project, run)
            monitor(runner.current(project))
        }
    }

    private fun monitor(run: WorkspaceRun?) {
        monitor?.cancel()
        mutableState.update {
            it.copy(run = run, running = run?.process?.session?.isAlive == true, logs = emptyList())
        }
        if (run == null) return
        monitor = viewModelScope.launch {
            launch {
                runner.logs(run).collect { logs -> mutableState.update { it.copy(logs = logs.takeLast(120)) } }
            }
            while (run.process.session.isAlive) delay(500)
            mutableState.update { it.copy(running = false) }
        }
    }

    private fun action(block: suspend () -> Unit) {
        if (mutableState.value.busy) return
        mutableState.update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            try {
                block()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                mutableState.update { it.copy(error = failure.message ?: "运行操作失败") }
            } finally {
                mutableState.update { it.copy(busy = false) }
            }
        }
    }

    private fun <T> AppResult<T>.value(): T = when (this) {
        is AppResult.Success -> data
        is AppResult.Failure -> error(error.message)
    }
}
