package top.wkbin.taixu.ui.workspace

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import top.wkbin.taixu.runtime.WorkspaceManager

internal const val MAX_EDITOR_TABS = 8

internal sealed interface EditorCloseRequest {
    data object All : EditorCloseRequest
    data class Tab(val file: EditorSessionController) : EditorCloseRequest
}

internal data class WorkspaceEditorState(
    val files: List<EditorSessionController> = emptyList(),
    val active: EditorSessionController? = null,
    val prompt: EditorCloseRequest? = null,
    val busy: Boolean = false,
    val exit: Boolean = false,
)

internal class WorkspaceEditorViewModel(
    private val workspace: WorkspaceManager,
    private val context: Context,
) : ViewModel() {
    private val mutableState = MutableStateFlow(WorkspaceEditorState())
    val state = mutableState.asStateFlow()
    private var initialOpened = false
    private var pendingClose: EditorCloseRequest? = null

    fun openInitial(project: String, path: String) {
        if (initialOpened) return
        initialOpened = true
        open(project, path)
    }

    fun open(project: String, path: String): Boolean {
        val current = mutableState.value
        if (current.busy) return false
        val existing = current.files.firstOrNull { it.project == project && it.path == path }
        if (existing != null) {
            select(existing)
            return true
        }
        if (current.files.size >= MAX_EDITOR_TABS) return false
        val file = EditorSessionController(viewModelScope, workspace, context, project, path)
        mutableState.update { it.copy(files = it.files + file, active = file) }
        file.load()
        return true
    }

    fun select(file: EditorSessionController) {
        val current = mutableState.value
        if (!current.busy && file in current.files) mutableState.update { it.copy(active = file) }
    }

    suspend fun listFiles(project: String, path: String) = workspace.listFiles(project, path)

    fun requestClose(request: EditorCloseRequest) {
        if (mutableState.value.busy) return
        if (filesToClose(request).any { it.state.value.dirty }) {
            mutableState.update { it.copy(prompt = request) }
        } else {
            completeClose(request)
        }
    }

    fun dismissClose() { mutableState.update { it.copy(prompt = null) } }

    fun discardAndClose() {
        val request = mutableState.value.prompt ?: return
        completeClose(request)
    }

    fun saveAndClose() {
        val request = mutableState.value.prompt ?: return
        dismissClose()
        editingOperation { saveBeforeClosing(request) }
    }

    fun save(file: EditorSessionController) = editingOperation { file.save() }

    fun overwrite(file: EditorSessionController) = editingOperation {
        val request = pendingClose
        pendingClose = null
        if (file.save(overwrite = true) && request != null) {
            saveBeforeClosing(request)
        } else if (file.state.value.conflict) {
            pendingClose = request
        }
    }

    fun dismissConflict(file: EditorSessionController) {
        pendingClose = null
        file.dismissConflict()
    }

    fun reloadConflict(file: EditorSessionController) {
        dismissConflict(file)
        file.load()
    }

    private suspend fun saveBeforeClosing(request: EditorCloseRequest) {
        for (file in filesToClose(request)) {
            if (!file.state.value.dirty) continue
            if (!file.save()) {
                pendingClose = request.takeIf { file.state.value.conflict }
                mutableState.update { it.copy(active = file) }
                return
            }
        }
        completeClose(request)
    }

    private fun filesToClose(request: EditorCloseRequest) = when (request) {
        EditorCloseRequest.All -> mutableState.value.files
        is EditorCloseRequest.Tab -> listOf(request.file)
    }

    private fun completeClose(request: EditorCloseRequest) {
        val closing = filesToClose(request)
        closing.forEach { it.close() }
        mutableState.update { current ->
            val remaining = current.files - closing.toSet()
            val index = current.files.indexOf(current.active).coerceAtLeast(0)
            val active = current.active?.takeIf { it in remaining }
                ?: remaining.getOrNull(index.coerceAtMost(remaining.lastIndex))
            current.copy(files = remaining, active = active, prompt = null, exit = remaining.isEmpty())
        }
    }

    private fun editingOperation(block: suspend () -> Unit) {
        if (mutableState.value.busy) return
        mutableState.update { it.copy(busy = true) }
        viewModelScope.launch {
            try {
                block()
            } finally {
                mutableState.update { it.copy(busy = false) }
            }
        }
    }
}
