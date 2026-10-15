package top.wkbin.taixu.ui.workspace

import android.net.Uri
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.res.stringResource
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.compose.koinInject
import top.wkbin.taixu.core.tools.LocalPluginImportState
import top.wkbin.taixu.core.tools.PluginDebugPackager
import top.wkbin.taixu.core.tools.ToolManager
import top.wkbin.taixu.feature.workspace.R
import top.wkbin.taixu.runtime.WorkspaceProject
import top.wkbin.taixu.ui.components.RuntimeAlertDialog
import top.wkbin.taixu.ui.components.RuntimeTextButton

internal class WorkspacePluginDebugAction(private val manager: ToolManager, private val scope: CoroutineScope) {
    private var job: Job? = null
    var busy by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
    var ready by mutableStateOf(false)

    fun start(project: WorkspaceProject) {
        if (busy) return
        busy = true
        job = scope.launch {
            var archive: File? = null
            var handedOff = false
            try {
                requireAvailableImport()
                archive = withContext(Dispatchers.IO) {
                    val operation = currentCoroutineContext()
                    PluginDebugPackager.pack(File(project.path)) { operation.ensureActive() }
                }
                requireAvailableImport()
                manager.startLocalPluginImport(Uri.fromFile(archive), archive.name)
                handedOff = true
                ready = true
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                error = failure.message ?: "插件打包失败"
            } finally {
                if (!handedOff) archive?.delete()
                busy = false
            }
        }
    }

    fun cancel() { job?.cancel() }

    private fun requireAvailableImport() {
        val state = manager.localPluginImportState.value
        check(state !is LocalPluginImportState.Reading && state !is LocalPluginImportState.Importing &&
            state !is LocalPluginImportState.PendingConfirmation) { "请先在插件中心完成或取消当前导入" }
    }
}

@Composable
internal fun rememberWorkspacePluginDebugAction(onOpenToolCenter: () -> Unit): WorkspacePluginDebugAction {
    val manager = koinInject<ToolManager>()
    val scope = rememberCoroutineScope()
    val action = remember(manager, scope) { WorkspacePluginDebugAction(manager, scope) }
    LaunchedEffect(action.ready) {
        if (action.ready) {
            action.ready = false
            onOpenToolCenter()
        }
    }
    if (action.busy) {
        RuntimeAlertDialog(
            onDismissRequest = action::cancel,
            title = { Text(stringResource(R.string.workspace_plugin_debug_import)) },
            text = { Text(stringResource(R.string.workspace_plugin_debug_preparing)) },
            confirmButton = {
                RuntimeTextButton(onClick = action::cancel) {
                    Text(stringResource(R.string.workspace_plugin_debug_cancel))
                }
            },
        )
    }
    action.error?.let { message ->
        RuntimeAlertDialog(
            onDismissRequest = { action.error = null },
            title = { Text(stringResource(R.string.workspace_plugin_debug_failed)) },
            text = { Text(message) },
            confirmButton = {
                RuntimeTextButton(onClick = { action.error = null }) {
                    Text(stringResource(R.string.workspace_plugin_debug_dismiss))
                }
            },
        )
    }
    return action
}
