package top.wkbin.taixu.ui.workspace

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.rosemoe.sora.widget.CodeEditor
import top.wkbin.taixu.feature.workspace.R
import top.wkbin.taixu.ui.components.RuntimeIcon
import top.wkbin.taixu.ui.components.RuntimeIconButton
import top.wkbin.taixu.ui.components.RuntimeIconMenu
import top.wkbin.taixu.ui.components.RuntimeIconName
import top.wkbin.taixu.ui.components.RuntimeTextButton
import top.wkbin.taixu.ui.miuix.RuntimeTheme
import top.wkbin.taixu.ui.miuix.Surface
import top.yukonga.miuix.kmp.basic.DropdownEntry
import top.yukonga.miuix.kmp.basic.DropdownItem
import top.yukonga.miuix.kmp.basic.Text

@Composable
internal fun EditorToolbar(
    editor: CodeEditor?,
    enabled: Boolean,
    dirty: Boolean,
    onFind: () -> Unit,
    onSave: () -> Unit,
    onReset: () -> Unit,
) {
    EditorIcon(RuntimeIconName.Search, R.string.workspace_editor_find, enabled && editor != null, onFind)
    EditorIcon(RuntimeIconName.Save, R.string.workspace_cd_save, enabled && dirty, onSave)
    val context = LocalContext.current
    val copyLabel = stringResource(R.string.workspace_cd_copy)
    val resetLabel = stringResource(R.string.workspace_cd_discard)
    val undoLabel = stringResource(R.string.workspace_editor_undo)
    val redoLabel = stringResource(R.string.workspace_editor_redo)
    val menu = buildList {
        if (editor != null) {
            add(DropdownItem(text = copyLabel, onClick = {
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText(copyLabel, editor.text.toString()))
                Toast.makeText(context, R.string.workspace_code_copied, Toast.LENGTH_SHORT).show()
            }))
            if (editor.canUndo()) add(DropdownItem(text = undoLabel, onClick = editor::undo))
            if (editor.canRedo()) add(DropdownItem(text = redoLabel, onClick = editor::redo))
        }
        if (dirty) add(DropdownItem(text = resetLabel, onClick = onReset))
    }
    val actionsLabel = stringResource(R.string.workspace_editor_actions)
    RuntimeIconMenu(
        entries = listOf(DropdownEntry(items = menu)),
        enabled = enabled && menu.isNotEmpty(),
        modifier = Modifier.semantics { contentDescription = actionsLabel },
    ) { RuntimeIcon(RuntimeIconName.More, Modifier.size(20.dp)) }
}

@Composable
internal fun EditorTabStrip(
    tabs: WorkspaceEditorState,
    onAdd: () -> Unit,
    onSelect: (EditorSessionController) -> Unit,
    onClose: (EditorSessionController) -> Unit,
) {
    val list = rememberLazyListState()
    LaunchedEffect(tabs.active, tabs.files.size) {
        val position = tabs.files.indexOf(tabs.active)
        if (position >= 0 && list.layoutInfo.visibleItemsInfo.none { it.index == position }) {
            list.scrollToItem(position)
        }
    }
    Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        LazyRow(
            modifier = Modifier.weight(1f),
            state = list,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            items(tabs.files, key = { it.path }) { file ->
                val selected = file === tabs.active
                val state by file.state.collectAsStateWithLifecycle()
                val label = if (state.dirty) stringResource(R.string.workspace_editor_tab_dirty, file.name) else file.name
                Surface(
                    color = if (selected) RuntimeTheme.colorScheme.secondaryContainer else RuntimeTheme.colorScheme.surfaceContainerLow,
                    shape = RuntimeTheme.shapes.small,
                ) {
                    Row(
                        modifier = Modifier.widthIn(max = 208.dp)
                            .selectable(selected, enabled = !tabs.busy, role = Role.Tab, onClick = { onSelect(file) })
                            .padding(start = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                        EditorIcon(RuntimeIconName.Close, R.string.workspace_editor_close_tab, !tabs.busy) { onClose(file) }
                    }
                }
            }
        }
        EditorIcon(RuntimeIconName.Plus, R.string.workspace_editor_open_tab, !tabs.busy, onAdd)
    }
}

@Composable
internal fun EditorStatus(extension: String, state: EditorFileState, wordWrap: Boolean, onWrap: () -> Unit) {
    Surface(color = RuntimeTheme.colorScheme.surfaceContainerLow, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                extension.uppercase(),
                style = RuntimeTheme.typography.labelSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(0.35f),
            )
            Text(
                stringResource(R.string.workspace_editor_stats, state.lines, state.characters),
                style = RuntimeTheme.typography.labelSmall,
                fontFamily = FontFamily.Monospace,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            RuntimeTextButton(onClick = onWrap) {
                Text(
                    stringResource(if (wordWrap) R.string.workspace_word_wrap_on else R.string.workspace_word_wrap_off),
                    style = RuntimeTheme.typography.labelSmall,
                    maxLines = 1,
                )
            }
            Text(
                stringResource(if (state.dirty) R.string.workspace_unsaved else R.string.workspace_saved),
                style = RuntimeTheme.typography.labelSmall,
                color = if (state.dirty) RuntimeTheme.colorScheme.primary else RuntimeTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun EditorIcon(icon: RuntimeIconName, label: Int, enabled: Boolean, onClick: () -> Unit) {
    RuntimeIconButton(onClick = onClick, enabled = enabled, contentDescription = stringResource(label)) {
        RuntimeIcon(icon, Modifier.size(18.dp))
    }
}
