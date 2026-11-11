package top.wkbin.taixu.ui.chat

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

import top.wkbin.taixu.ui.components.RuntimeAlertDialog
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import top.wkbin.taixu.ui.components.RuntimeButton as Button
import top.wkbin.taixu.ui.components.RuntimeCircularProgressIndicator as CircularProgressIndicator
import top.wkbin.taixu.ui.miuix.HorizontalDivider
import top.wkbin.taixu.ui.miuix.RuntimeTheme
import top.wkbin.taixu.ui.components.RuntimeDropdownField
import top.wkbin.taixu.ui.components.RuntimeFilledTextField
import top.wkbin.taixu.ui.miuix.Surface
import top.yukonga.miuix.kmp.basic.Text
import top.wkbin.taixu.ui.components.RuntimeTextButton as TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import top.wkbin.taixu.feature.chat.R
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import top.wkbin.taixu.runtime.WorkspaceProject
import top.wkbin.taixu.ui.components.RuntimeIcon
import top.wkbin.taixu.ui.components.RuntimeIconName
import top.wkbin.taixu.runtime.ProjectType
import top.wkbin.taixu.runtime.SessionWorkspaceCatalogState

@Composable
internal fun NewSessionDialog(
    workspaces: List<WorkspaceProject>,
    catalog: SessionWorkspaceCatalogState,
    onDismiss: () -> Unit,
    onCreate: (title: String, workspace: String, projectType: ProjectType) -> Unit,
) {
    val defaultTitle = stringResource(R.string.chat_new_session)
    // 表单状态旋转后保留；枚举以 name 字符串保存
    var title by rememberSaveable { mutableStateOf(defaultTitle) }
    var selected by rememberSaveable(catalog.selectionId, catalog.server) { mutableStateOf("") }
    var selectedTypeName by rememberSaveable(catalog.selectionId, catalog.server) { mutableStateOf(ProjectType.GENERAL.name) }
    val selectedType = ProjectType.valueOf(selectedTypeName)
    val quickTags = listOf(
        defaultTitle,
        stringResource(R.string.chat_quick_bug),
        stringResource(R.string.chat_quick_feature),
        stringResource(R.string.chat_quick_environment),
        stringResource(R.string.chat_quick_refactor),
    )

    RuntimeAlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                RuntimeIcon(RuntimeIconName.Plus, Modifier.size(20.dp), tint = RuntimeTheme.colorScheme.onSurface)
                Text(stringResource(R.string.chat_new_agent_session), fontWeight = FontWeight.Medium)
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(stringResource(R.string.chat_session_title), style = RuntimeTheme.typography.labelMedium, fontWeight = FontWeight.Medium)
                RuntimeFilledTextField(
                    value = title,
                    onValueChange = { title = it },
                    placeholder = { Text(stringResource(R.string.chat_session_name_hint)) },
                    singleLine = true,
                    shape = RuntimeTheme.shapes.small,
                    modifier = Modifier.fillMaxWidth(),
                )

                // 快捷预设标题标签
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    quickTags.forEach { tag ->
                        Surface(
                            shape = RuntimeTheme.shapes.extraSmall,
                            color = if (title == tag) RuntimeTheme.colorScheme.primary.copy(alpha = 0.15f) else RuntimeTheme.colorScheme.surfaceContainerHigh,
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                if (title == tag) RuntimeTheme.colorScheme.primary.copy(alpha = 0.4f) else Color.Transparent,
                            ),
                            modifier = Modifier.clickable { title = tag },
                        ) {
                            Text(
                                tag,
                                style = RuntimeTheme.typography.labelSmall,
                                color = if (title == tag) RuntimeTheme.colorScheme.primary else RuntimeTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            )
                        }
                    }
                }

                HorizontalDivider(color = RuntimeTheme.colorScheme.outlineVariant)

                Text(
                    stringResource(R.string.chat_link_workspace),
                    style = RuntimeTheme.typography.labelMedium,
                    fontWeight = FontWeight.Medium,
                )

                LazyColumn(
                    // A nested lazy list needs finite constraints inside the scrolling dialog form.
                    modifier = Modifier.fillMaxWidth().heightIn(max = 240.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    item {
                        WorkspaceOption(stringResource(R.string.chat_no_workspace), catalog.server?.workspace ?: "/workspace", selected == "", onSelect = {
                            selected = ""
                            selectedTypeName = ProjectType.GENERAL.name
                        })
                    }
                    item { if (catalog.loading) CircularProgressIndicator(); catalog.error?.let { Text(it, color = RuntimeTheme.colorScheme.error) } }
                    items(workspaces.size, key = { workspaces[it].linuxPath }) { index ->
                        val ws = workspaces[index]
                        WorkspaceOption(ws.name, ws.linuxPath, selected == ws.linuxPath) {
                            selected = ws.linuxPath
                            selectedTypeName = ws.projectType.name
                        }
                    }
                }

                val selectedProject = workspaces.firstOrNull { it.linuxPath == selected }
                // Keep the detected type as the default, but allow an imported
                // repository to be assigned to a different specialist Agent.
                Text(
                    if (selectedProject == null || selectedProject.projectType == ProjectType.GENERAL) {
                        stringResource(R.string.chat_project_type_manual)
                    } else {
                        stringResource(R.string.chat_project_type_detected)
                    },
                    style = RuntimeTheme.typography.labelMedium,
                    fontWeight = FontWeight.Medium,
                )
                RuntimeDropdownField(
                    title = stringResource(R.string.chat_select),
                    items = ProjectType.entries.map { it.displayName },
                    selectedIndex = ProjectType.entries.indexOf(selectedType),
                    onSelectedIndexChange = { index ->
                        ProjectType.entries.getOrNull(index)?.let { type ->
                            selectedTypeName = type.name
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onCreate(title.ifBlank { defaultTitle }, selected, selectedType) },
                enabled = !catalog.loading && (selected.isBlank() || catalog.error == null),
                shape = RuntimeTheme.shapes.medium,
            ) {
                Text(stringResource(R.string.chat_create_session))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.chat_cancel)) }
        },
    )
}
