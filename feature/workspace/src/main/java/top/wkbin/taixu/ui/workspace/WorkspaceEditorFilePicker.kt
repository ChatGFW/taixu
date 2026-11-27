package top.wkbin.taixu.ui.workspace

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import top.wkbin.taixu.core.common.result.AppResult
import top.wkbin.taixu.feature.workspace.R
import top.wkbin.taixu.runtime.WorkspaceFileItem
import top.wkbin.taixu.ui.components.NoticeBanner
import top.wkbin.taixu.ui.components.RuntimeCard
import top.wkbin.taixu.ui.components.RuntimeCircularProgressIndicator
import top.wkbin.taixu.ui.components.RuntimeIcon
import top.wkbin.taixu.ui.components.RuntimeIconButton
import top.wkbin.taixu.ui.components.RuntimeIconName
import top.wkbin.taixu.ui.components.RuntimeModalBottomSheet
import top.wkbin.taixu.ui.components.runtimeContentPadding
import top.wkbin.taixu.ui.miuix.RuntimeTheme
import top.yukonga.miuix.kmp.basic.Text

@Composable
internal fun WorkspaceEditorFilePicker(
    project: String,
    initialDirectory: String,
    listFiles: suspend (String, String) -> AppResult<List<WorkspaceFileItem>>,
    onOpen: (String) -> Boolean,
    onDismiss: () -> Unit,
) {
    var directory by rememberSaveable { mutableStateOf(initialDirectory) }
    var atLimit by rememberSaveable { mutableStateOf(false) }
    val result by produceState<AppResult<List<WorkspaceFileItem>>?>(null, project, directory) {
        value = null
        value = listFiles(project, directory)
    }
    RuntimeModalBottomSheet(onDismissRequest = onDismiss) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (directory.isNotEmpty()) {
                RuntimeIconButton(
                    onClick = { directory = directory.substringBeforeLast('/', "") },
                    contentDescription = stringResource(R.string.workspace_editor_parent_folder),
                ) { RuntimeIcon(RuntimeIconName.Back, Modifier.size(18.dp)) }
            }
            Text(
                text = directory.ifEmpty { project },
                style = RuntimeTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (atLimit) {
            NoticeBanner(
                text = stringResource(R.string.workspace_editor_tab_limit, MAX_EDITOR_TABS),
                isError = true,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            )
        }
        LazyColumn(
            modifier = Modifier.fillMaxWidth().heightIn(max = 450.dp),
            contentPadding = runtimeContentPadding(top = 8.dp, bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            when (val files = result) {
                null -> item { RuntimeCircularProgressIndicator() }
                is AppResult.Failure -> item { Text(files.error.message, color = RuntimeTheme.colorScheme.error) }
                is AppResult.Success -> {
                    if (files.data.isEmpty()) item { Text(stringResource(R.string.workspace_empty_directory)) }
                    items(files.data, key = { it.relativePath }) { file ->
                        RuntimeCard(
                            modifier = Modifier.fillMaxWidth(),
                            onClick = {
                                if (file.isDirectory) directory = file.relativePath
                                else atLimit = !onOpen(file.relativePath)
                            },
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                RuntimeIcon(if (file.isDirectory) RuntimeIconName.Folder else RuntimeIconName.File, Modifier.size(18.dp))
                                Text(file.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
            }
        }
    }
}
