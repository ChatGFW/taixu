package top.wkbin.taixu.ui.workspace

import android.content.Context
import io.github.rosemoe.sora.text.Content
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.wkbin.taixu.core.common.result.ErrorCode
import top.wkbin.taixu.feature.workspace.R
import top.wkbin.taixu.runtime.HostFileActions
import top.wkbin.taixu.runtime.WorkspaceFileInspection
import top.wkbin.taixu.runtime.WorkspaceManager
import top.wkbin.taixu.runtime.hostFile
import top.wkbin.taixu.runtime.inspectFile

internal data class EditorFileState(
    val loading: Boolean = false,
    val ready: Boolean = false,
    val binary: WorkspaceFileInspection? = null,
    val revision: Long = 0,
    val dirty: Boolean = false,
    val conflict: Boolean = false,
    val notice: String? = null,
    val isError: Boolean = false,
    val lines: Int = 1,
    val characters: Int = 0,
)

/** One buffer and one disk baseline per tab. The native view is owned by the composition. */
internal class EditorSessionController(
    private val scope: CoroutineScope,
    private val workspace: WorkspaceManager,
    private val context: Context,
    val project: String,
    val path: String,
) {
    private val draft = EditorDraft()
    private val mutableState = MutableStateFlow(EditorFileState())
    val state = mutableState.asStateFlow()
    private var loadJob: Job? = null
    var content = Content()
        private set
    var scrollX = 0
    var scrollY = 0
    var textSizeSp = 13f
    val name = path.substringAfterLast('/')
    val extension = name.substringAfterLast('.', "")

    fun load() {
        if (mutableState.value.loading) return
        mutableState.update { it.copy(loading = true, conflict = false, notice = null) }
        loadJob = scope.launch {
            try {
                val info = workspace.inspectFile(project, path)
                val inspection = info.getOrNull()
                if (inspection == null) {
                    showError(info.errorOrNull()?.message, R.string.workspace_open_file_failed)
                    return@launch
                }
                if (inspection.binary) {
                    draft.clear()
                    content = Content()
                    mutableState.update {
                        it.copy(ready = false, binary = inspection, dirty = false, lines = 1, characters = 0)
                    }
                    return@launch
                }
                val read = workspace.readFile(project, path)
                val value = read.getOrNull()
                if (value == null) {
                    showError(read.errorOrNull()?.message, R.string.workspace_open_file_failed)
                    return@launch
                }
                val newContent = withContext(Dispatchers.Default) { Content(value) }
                draft.load(project, path, value)
                replaceContent(newContent)
            } finally {
                mutableState.update { it.copy(loading = false) }
            }
        }
    }

    fun onContentChanged(buffer: Content) {
        if (!mutableState.value.ready || mutableState.value.loading) return
        content = buffer
        updateTextState()
    }

    private fun updateTextState() {
        val saved = draft.savedContent
        val dirty = content.length != saved.length || content.toString() != saved
        mutableState.update { it.copy(dirty = dirty, lines = content.lineCount, characters = content.length) }
    }

    fun resetContent() {
        draft.reset()
        replaceContent(Content(draft.savedContent))
    }

    private fun replaceContent(buffer: Content) {
        content = buffer
        scrollX = 0
        scrollY = 0
        mutableState.update {
            it.copy(
                ready = true, binary = null, revision = it.revision + 1,
                dirty = false, conflict = false, lines = buffer.lineCount, characters = buffer.length,
            )
        }
    }

    suspend fun save(overwrite: Boolean = false): Boolean {
        val file = mutableState.value
        if (!file.ready || file.loading) return false
        if (!file.dirty && !file.conflict) return true
        draft.edit(content.toString())
        val snapshot = draft.snapshot()
        val expected = if (overwrite) null else draft.savedContent
        val write = workspace.writeFile(project, path, snapshot.text, expected)
        if (write.isSuccess) {
            draft.markSaved(snapshot)
            updateTextState()
            mutableState.update {
                it.copy(conflict = false, notice = context.getString(R.string.workspace_file_saved), isError = false)
            }
            return !mutableState.value.dirty
        }
        if (write.errorOrNull()?.code == ErrorCode.CONFLICT) {
            mutableState.update { it.copy(conflict = true) }
        } else {
            mutableState.update { it.copy(conflict = false) }
            showError(write.errorOrNull()?.message, R.string.workspace_save_failed)
        }
        return false
    }

    fun dismissConflict() {
        mutableState.update { it.copy(conflict = false) }
    }

    fun openExternally(share: Boolean) {
        scope.launch {
            val file = workspace.hostFile(project, path).getOrNull()
            val launched = file != null &&
                if (share) HostFileActions.share(context, file, subject = file.name) else HostFileActions.open(context, file)
            if (!launched) showError(null, R.string.workspace_open_external_failed)
        }
    }

    fun close() { loadJob?.cancel() }

    private fun showError(detail: String?, fallback: Int) {
        mutableState.update { it.copy(notice = detail ?: context.getString(fallback), isError = true) }
    }
}
