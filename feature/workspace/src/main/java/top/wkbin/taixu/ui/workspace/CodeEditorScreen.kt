package top.wkbin.taixu.ui.workspace

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.rosemoe.sora.widget.CodeEditor
import org.koin.compose.viewmodel.koinViewModel
import top.wkbin.taixu.feature.workspace.R
import top.wkbin.taixu.ui.components.NoticeBanner
import top.wkbin.taixu.ui.components.RuntimeAlertDialog
import top.wkbin.taixu.ui.components.RuntimeCircularProgressIndicator
import top.wkbin.taixu.ui.components.RuntimeTextButton
import top.wkbin.taixu.ui.components.RuntimeTopBar
import top.wkbin.taixu.ui.components.previewOrNull
import top.wkbin.taixu.ui.components.previewStateOf
import top.wkbin.taixu.ui.components.runtimeScrollViewport
import top.wkbin.taixu.ui.miuix.RuntimeTheme
import top.wkbin.taixu.ui.miuix.Scaffold
import top.yukonga.miuix.kmp.basic.Text

@Composable
fun CodeEditorScreen(projectName: String, relativePath: String, onBack: () -> Unit) {
    val model = previewOrNull { koinViewModel<WorkspaceEditorViewModel>() }
    val tabs by model?.state?.collectAsStateWithLifecycle() ?: previewStateOf(WorkspaceEditorState())
    val file = tabs.active
    val state by file?.state?.collectAsStateWithLifecycle() ?: previewStateOf(EditorFileState())
    var editor by remember(file) { mutableStateOf<CodeEditor?>(null) }
    var wordWrap by rememberSaveable { mutableStateOf(false) }
    var showPicker by rememberSaveable { mutableStateOf(false) }
    var showSearch by rememberSaveable { mutableStateOf(false) }
    var showReset by rememberSaveable(file?.path) { mutableStateOf(false) }
    val searchVisible = showSearch && state.ready && !state.loading

    LaunchedEffect(projectName, relativePath) { model?.openInitial(projectName, relativePath) }
    LaunchedEffect(tabs.exit) { if (tabs.exit) onBack() }
    val back: () -> Unit = {
        when {
            searchVisible -> showSearch = false
            model == null -> onBack()
            else -> model.requestClose(EditorCloseRequest.All)
        }
    }
    BackHandler(onBack = back)

    Scaffold(
        containerColor = RuntimeTheme.colorScheme.background,
        topBar = {
            RuntimeTopBar(
                title = file?.name ?: relativePath.substringAfterLast('/'),
                statusText = file?.path ?: relativePath,
                onBack = back,
            ) {
                EditorToolbar(
                    editor = editor?.takeUnless { it.isReleased },
                    enabled = state.ready && !state.loading && !tabs.busy,
                    dirty = state.dirty,
                    onFind = { showSearch = !showSearch },
                    onSave = { file?.let { model?.save(it) } },
                    onReset = { showReset = true },
                )
            }
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().then(runtimeScrollViewport(padding))
                .padding(top = padding.calculateTopPadding(), bottom = padding.calculateBottomPadding())
                .imePadding(),
        ) {
            EditorTabStrip(
                tabs = tabs,
                onAdd = { showPicker = true },
                onSelect = { model?.select(it) },
                onClose = { model?.requestClose(EditorCloseRequest.Tab(it)) },
            )
            state.notice?.let { NoticeBanner(it, isError = state.isError, modifier = Modifier.padding(horizontal = 8.dp)) }
            if (file == null || state.loading) {
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    RuntimeCircularProgressIndicator()
                }
            } else {
                val binary = state.binary
                when {
                    binary != null -> BinaryFileNotice(
                        inspection = binary,
                        onOpen = { file.openExternally(share = false) },
                        onShare = { file.openExternally(share = true) },
                    )
                    state.ready -> {
                        if (searchVisible) {
                            key(file) {
                                WorkspaceEditorSearch(editor?.takeUnless { it.isReleased }, !tabs.busy) { showSearch = false }
                            }
                        }
                        key(file) {
                            NativeWorkspaceEditor(
                                file = file,
                                state = state,
                                wordWrap = wordWrap,
                                editable = !tabs.busy,
                                modifier = Modifier.weight(1f).fillMaxWidth(),
                                onMounted = { editor = it },
                            )
                        }
                        EditorStatus(file.extension, state, wordWrap) { wordWrap = !wordWrap }
                    }
                    else -> RuntimeTextButton(onClick = file::load) { Text(stringResource(R.string.workspace_editor_retry)) }
                }
            }
        }
    }
    if (showPicker && model != null) {
        WorkspaceEditorFilePicker(
            project = projectName,
            initialDirectory = file?.path?.substringBeforeLast('/', "").orEmpty(),
            listFiles = model::listFiles,
            onOpen = { path -> model.open(projectName, path).also { if (it) showPicker = false } },
            onDismiss = { showPicker = false },
        )
    }
    val prompt = tabs.prompt
    if (prompt != null && model != null) {
        EditorCloseDialog(prompt, model::saveAndClose, model::discardAndClose, model::dismissClose)
    }
    if (showReset && file != null) {
        DiscardEditsDialog(
            onConfirm = {
                showReset = false
                file.resetContent()
            },
            onDismiss = { showReset = false },
        )
    }
    if (state.conflict && file != null && model != null) {
        SaveConflictDialog(
            isSaving = tabs.busy,
            isLoading = state.loading,
            onOverwrite = { model.overwrite(file) },
            onReload = { model.reloadConflict(file) },
            onDismiss = { if (!tabs.busy) model.dismissConflict(file) },
        )
    }
}

@Composable
private fun EditorCloseDialog(
    request: EditorCloseRequest,
    onSave: () -> Unit,
    onDiscard: () -> Unit,
    onDismiss: () -> Unit,
) {
    val wholeEditor = request == EditorCloseRequest.All
    RuntimeAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.workspace_unsaved_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (request is EditorCloseRequest.Tab) Text(request.file.path)
                Text(stringResource(if (wholeEditor) R.string.workspace_editor_unsaved_tabs else R.string.workspace_unsaved_message))
            }
        },
        confirmButton = {
            RuntimeTextButton(onClick = onSave) {
                Text(stringResource(if (wholeEditor) R.string.workspace_editor_save_all_exit else R.string.workspace_editor_save_close))
            }
        },
        dismissButton = {
            RuntimeTextButton(onClick = onDiscard) {
                Text(stringResource(R.string.workspace_discard_changes), color = RuntimeTheme.colorScheme.error)
            }
        },
    )
}
