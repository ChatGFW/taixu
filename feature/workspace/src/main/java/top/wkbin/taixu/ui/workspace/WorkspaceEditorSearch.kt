package top.wkbin.taixu.ui.workspace

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import io.github.rosemoe.sora.event.PublishSearchResultEvent
import io.github.rosemoe.sora.event.SelectionChangeEvent
import io.github.rosemoe.sora.widget.CodeEditor
import io.github.rosemoe.sora.widget.EditorSearcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.wkbin.taixu.feature.workspace.R
import top.wkbin.taixu.runtime.WorkspaceManager
import top.wkbin.taixu.ui.components.RuntimeActionRow
import top.wkbin.taixu.ui.components.RuntimeFilledTextField
import top.wkbin.taixu.ui.components.RuntimeIcon
import top.wkbin.taixu.ui.components.RuntimeIconButton
import top.wkbin.taixu.ui.components.RuntimeIconName
import top.wkbin.taixu.ui.components.RuntimeTextButton
import top.wkbin.taixu.ui.miuix.RuntimeTheme
import top.yukonga.miuix.kmp.basic.Text

@Composable
internal fun WorkspaceEditorSearch(editor: CodeEditor?, enabled: Boolean, onClose: () -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    var replacement by rememberSaveable { mutableStateOf("") }
    var matchCase by rememberSaveable { mutableStateOf(false) }
    var showReplace by rememberSaveable { mutableStateOf(false) }
    var count by remember(editor) { mutableIntStateOf(0) }
    var position by remember(editor) { mutableIntStateOf(0) }
    var searching by remember(editor) { mutableStateOf(false) }
    var replacing by remember(editor) { mutableStateOf(false) }
    var message by remember(editor) { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }

    DisposableEffect(editor) {
        val searcher = editor?.searcher
        fun update() {
            count = if (searcher?.hasQuery() == true) searcher.matchedPositionCount else 0
            position = if (searcher?.hasQuery() == true) searcher.currentMatchedPositionIndex + 1 else 0
        }
        val results = editor?.subscribeEvent(PublishSearchResultEvent::class.java) { _, _ ->
            searching = false
            update()
        }
        val selection = editor?.subscribeEvent(SelectionChangeEvent::class.java) { _, _ -> update() }
        onDispose {
            results?.unsubscribe()
            selection?.unsubscribe()
            if (editor?.isReleased == false && searcher?.hasQuery() == true) searcher.stopSearch()
        }
    }
    LaunchedEffect(editor, query, matchCase) {
        val searcher = editor?.searcher ?: return@LaunchedEffect
        count = 0
        position = 0
        message = 0
        searching = query.isNotEmpty()
        if (searching) searcher.search(query, EditorSearcher.SearchOptions(!matchCase, false))
        else if (searcher.hasQuery()) searcher.stopSearch()
    }
    val hasMatches = !searching && count > 0
    val canReplace = enabled && hasMatches && !replacing

    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            RuntimeFilledTextField(
                value = query,
                onValueChange = {
                    val next = it.take(1_024)
                    if (next != query) {
                        query = next
                        searching = next.isNotEmpty()
                        count = 0
                    }
                },
                modifier = Modifier.weight(1f).focusRequester(focus),
                labelText = stringResource(R.string.workspace_editor_find),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = {
                    if (hasMatches) editor?.searcher?.takeIf { it.hasQuery() }?.gotoNext()
                }),
                singleLine = true,
            )
            SearchIcon(RuntimeIconName.ChevronUp, R.string.workspace_editor_prev_match, hasMatches) {
                editor?.searcher?.takeIf { it.hasQuery() }?.gotoPrevious()
            }
            SearchIcon(RuntimeIconName.ChevronDown, R.string.workspace_editor_next_match, hasMatches) {
                editor?.searcher?.takeIf { it.hasQuery() }?.gotoNext()
            }
            SearchIcon(RuntimeIconName.Close, R.string.workspace_editor_close_search, true, onClose)
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                when {
                    query.isEmpty() -> ""
                    searching -> stringResource(R.string.workspace_editor_searching)
                    position > 0 -> stringResource(R.string.workspace_editor_match_position, position, count)
                    else -> stringResource(R.string.workspace_editor_matches, count)
                },
                style = RuntimeTheme.typography.labelSmall,
                modifier = Modifier.weight(1f),
            )
            RuntimeTextButton(onClick = {
                matchCase = !matchCase
                searching = query.isNotEmpty()
            }) {
                Text(
                    stringResource(R.string.workspace_editor_match_case),
                    color = if (matchCase) RuntimeTheme.colorScheme.primary else RuntimeTheme.colorScheme.onSurfaceVariant,
                )
            }
            RuntimeTextButton(onClick = { showReplace = !showReplace }) {
                Text(stringResource(R.string.workspace_editor_replace))
            }
        }
        if (showReplace) {
            RuntimeFilledTextField(
                value = replacement,
                onValueChange = { replacement = it.take(1_024) },
                modifier = Modifier.fillMaxWidth(),
                labelText = stringResource(R.string.workspace_editor_replacement),
                singleLine = true,
            )
            RuntimeActionRow {
                RuntimeTextButton(
                    enabled = canReplace && position > 0,
                    onClick = {
                        editor?.searcher?.takeIf { it.hasQuery() }?.replaceCurrentMatch(replacement)
                    },
                ) { Text(stringResource(R.string.workspace_editor_replace)) }
                RuntimeTextButton(
                    enabled = canReplace,
                    onClick = {
                        val view = editor ?: return@RuntimeTextButton
                        val buffer = view.text
                        val original = buffer.toString()
                        val needle = query
                        val value = replacement
                        val exact = matchCase
                        replacing = true
                        message = 0
                        scope.launch {
                            try {
                                val edited = withContext(Dispatchers.Default) {
                                    replaceEditorMatches(original, needle, value, exact, WorkspaceManager.MAX_FILE_WRITE_CHARS)
                                }
                                when {
                                    edited == null -> message = R.string.workspace_editor_replace_limit
                                    view.isReleased || !view.isEditable || view.text !== buffer || buffer.toString() != original ->
                                        message = R.string.workspace_editor_replace_changed
                                    edited != original -> {
                                        val cursor = view.cursor.left()
                                        buffer.replace(0, buffer.length, edited)
                                        val line = cursor.line.coerceAtMost(buffer.lineCount - 1)
                                        view.setSelection(line, cursor.column.coerceAtMost(buffer.getColumnCount(line)))
                                    }
                                }
                            } finally {
                                replacing = false
                            }
                        }
                    },
                ) { Text(stringResource(R.string.workspace_editor_replace_all)) }
            }
            if (message != 0) Text(stringResource(message), color = RuntimeTheme.colorScheme.error)
        }
    }
}

@Composable
private fun SearchIcon(icon: RuntimeIconName, label: Int, enabled: Boolean, onClick: () -> Unit) {
    RuntimeIconButton(onClick = onClick, enabled = enabled, contentDescription = stringResource(label)) {
        RuntimeIcon(icon, Modifier.size(18.dp))
    }
}
