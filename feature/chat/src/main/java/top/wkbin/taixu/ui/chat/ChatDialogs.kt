package top.wkbin.taixu.ui.chat

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

import top.wkbin.taixu.ui.components.RuntimeAlertDialog
import android.provider.Settings
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import top.wkbin.taixu.ui.miuix.*
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import top.wkbin.taixu.ui.components.RuntimeButton as Button
import top.wkbin.taixu.ui.components.RuntimeIconButton as IconButton
import top.wkbin.taixu.ui.components.RuntimeFilledTextField
import top.yukonga.miuix.kmp.basic.Text
import top.wkbin.taixu.ui.components.RuntimeTextButton as TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import top.wkbin.taixu.feature.chat.R
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import top.wkbin.taixu.core.database.HarnessSessionEntity
import top.wkbin.taixu.ui.components.RuntimeIcon
import top.wkbin.taixu.ui.theme.semanticColors
import top.wkbin.taixu.ui.components.RuntimeIconName
import top.wkbin.taixu.core.model.SessionRunState

/** 会话 / 模型 / 新建会话对话框。 */
@Composable
internal fun EditAndResendDialog(
    originalText: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var text by remember(originalText) { mutableStateOf(originalText) }
    RuntimeAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.chat_edit_resend), fontWeight = FontWeight.Medium) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                RuntimeFilledTextField(
                    value = text,
                    onValueChange = { text = it },
                    labelText = stringResource(R.string.chat_user_request_clipboard),
                    minLines = 2,
                    maxLines = 6,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    stringResource(R.string.chat_resend_description),
                    style = RuntimeTheme.typography.bodySmall,
                    color = RuntimeTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(text) },
                enabled = text.isNotBlank(),
            ) { Text(stringResource(R.string.chat_send)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.chat_cancel)) }
        },
    )
}

@Composable
internal fun SessionsDialog(
    sessions: List<HarnessSessionEntity>,
    currentSessionId: String,
    sessionRunStates: Map<String, SessionRunState>,
    onDismiss: () -> Unit,
    onSwitch: (String) -> Unit,
    onNew: () -> Unit,
    onDelete: (String) -> Unit,
    onRename: (String, String) -> Unit,
) {
    // 重命名 / 删除目标只保存 id，旋转后可从 sessions 恢复
    var renameTargetId by rememberSaveable { mutableStateOf<String?>(null) }
    var deleteTargetId by rememberSaveable { mutableStateOf<String?>(null) }
    RuntimeAlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    RuntimeIcon(RuntimeIconName.List, Modifier.size(20.dp), tint = RuntimeTheme.colorScheme.primary)
                    Text(stringResource(R.string.chat_session_manager), fontWeight = FontWeight.Medium)
                }
                Surface(
                    color = RuntimeTheme.colorScheme.primary.copy(alpha = 0.12f),
                    shape = RuntimeTheme.shapes.extraSmall,
                ) {
                    Text(
                        stringResource(R.string.chat_session_count, sessions.size),
                        style = RuntimeTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                        color = RuntimeTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 400.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (sessions.size == 1) {
                    Surface(
                        color = RuntimeTheme.colorScheme.surfaceContainerHigh,
                        shape = RuntimeTheme.shapes.chip,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            modifier = Modifier.padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            RuntimeIcon(RuntimeIconName.Alert, Modifier.size(14.dp), tint = RuntimeTheme.colorScheme.primary)
                            Text(
                                stringResource(R.string.chat_last_session_hint),
                                style = RuntimeTheme.typography.labelSmall,
                                color = RuntimeTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().weight(1f, fill = false),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(sessions.size, key = { sessions[it].id }) { index ->
                        val session = sessions[index]
                        val isCurrent = session.id == currentSessionId
                        val runState = sessionRunStates[session.id] ?: SessionRunState.IDLE

                        val semantics = semanticColors()
                        val (dotColor, stateLabel) = when (runState) {
                            SessionRunState.RUNNING -> semantics.warning to stringResource(R.string.chat_state_running)
                            SessionRunState.WAITING_APPROVAL -> semantics.agent to stringResource(R.string.chat_state_approval)
                            SessionRunState.FAILED -> RuntimeTheme.colorScheme.error to stringResource(R.string.chat_state_failed)
                            SessionRunState.COMPLETED -> semantics.success to stringResource(R.string.chat_state_completed)
                            SessionRunState.IDLE -> semantics.success to stringResource(R.string.chat_state_ready)
                        }

                        Surface(
                            shape = RuntimeTheme.shapes.small,
                            color = if (isCurrent) RuntimeTheme.colorScheme.surfaceContainer
                                else RuntimeTheme.colorScheme.surfaceContainerLow,
                            border = androidx.compose.foundation.BorderStroke(
                                if (isCurrent) 1.5.dp else 1.dp,
                                if (isCurrent) RuntimeTheme.colorScheme.primary
                                else RuntimeTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                            ),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onSwitch(session.id) }
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                // 状态指示圆点（绿色完成/橙色进行中/红色失败）
                                Box(
                                    modifier = Modifier
                                        .size(10.dp)
                                        .clip(CircleShape)
                                        .background(dotColor),
                                )

                                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    ) {
                                        Text(
                                            session.title,
                                            style = RuntimeTheme.typography.bodyMedium.copy(
                                                fontWeight = if (isCurrent) FontWeight.Medium else FontWeight.Medium,
                                            ),
                                            color = RuntimeTheme.colorScheme.onSurface,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f, fill = false),
                                        )
                                        if (isCurrent) {
                                            Surface(
                                                color = RuntimeTheme.colorScheme.primaryContainer,
                                                shape = RuntimeTheme.shapes.tiny,
                                            ) {
                                                Text(
                                                    stringResource(R.string.chat_current),
                                                    style = RuntimeTheme.typography.labelSmall.copy(fontSize = 11.sp),
                                                    color = RuntimeTheme.colorScheme.onPrimaryContainer,
                                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                                                )
                                            }
                                        }
                                    }
                                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Surface(
                                            color = dotColor.copy(alpha = 0.12f),
                                            shape = RuntimeTheme.shapes.tiny,
                                        ) {
                                            Text(
                                                stateLabel,
                                                style = RuntimeTheme.typography.labelSmall.copy(
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Medium,
                                                ),
                                                color = dotColor,
                                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                                            )
                                        }

                                        if (session.workspace.isNotBlank()) {
                                            Surface(
                                                color = RuntimeTheme.colorScheme.surfaceContainerHigh,
                                                shape = RuntimeTheme.shapes.tiny,
                                            ) {
                                                Text(
                                                    session.workspace,
                                                    style = RuntimeTheme.typography.labelSmall.copy(fontSize = 11.sp),
                                                    color = RuntimeTheme.colorScheme.onSurfaceVariant,
                                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                )
                                            }
                                        } else {
                                            Text(
                                                stringResource(R.string.chat_isolated_sandbox),
                                                style = RuntimeTheme.typography.labelSmall.copy(fontSize = 11.sp),
                                                color = RuntimeTheme.colorScheme.onSurfaceVariant,
                                            )
                                        }
                                    }
                                }
                                IconButton(
                                    onClick = { renameTargetId = session.id },
                                    modifier = Modifier.minimumInteractiveComponentSize(),
                                    contentDescription = stringResource(R.string.chat_rename_session_button),
                                ) {
                                    RuntimeIcon(RuntimeIconName.Settings, Modifier.size(15.dp), RuntimeTheme.colorScheme.onSurfaceVariant)
                                }
                                IconButton(
                                    onClick = { deleteTargetId = session.id },
                                    modifier = Modifier.minimumInteractiveComponentSize(),
                                    contentDescription = stringResource(R.string.chat_delete_session),
                                ) {
                                    RuntimeIcon(RuntimeIconName.Trash, Modifier.size(15.dp), RuntimeTheme.colorScheme.error)
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = onNew, shape = RuntimeTheme.shapes.medium) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    RuntimeIcon(RuntimeIconName.Plus, Modifier.size(16.dp), RuntimeTheme.colorScheme.onPrimary)
                    Text(stringResource(R.string.chat_new_session))
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.chat_close)) }
        },
    )

    val renameTarget = renameTargetId?.let { id -> sessions.firstOrNull { it.id == id } }
    renameTarget?.let { target ->
        RenameSessionDialog(
            currentTitle = target.title,
            onDismiss = { renameTargetId = null },
            onRename = { title ->
                onRename(target.id, title)
                renameTargetId = null
            },
        )
    }

    // 删除会话二次确认（运行中会话也提示，避免误删不可恢复）
    deleteTargetId?.let { targetId ->
        RuntimeAlertDialog(
            onDismissRequest = { deleteTargetId = null },
            title = { Text(stringResource(R.string.chat_delete_session_title), fontWeight = FontWeight.Medium) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        stringResource(R.string.chat_delete_session_message),
                        style = RuntimeTheme.typography.bodyMedium,
                        color = RuntimeTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        deleteTargetId = null
                        onDelete(targetId)
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = RuntimeTheme.colorScheme.error,
                        disabledContainerColor = RuntimeTheme.colorScheme.error.copy(alpha = 0.3f),
                    ),
                ) {
                    Text(stringResource(R.string.chat_confirm_delete), color = RuntimeTheme.colorScheme.onError)
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteTargetId = null }) { Text(stringResource(R.string.chat_cancel)) }
            },
        )
    }
}

@Composable
internal fun RenameSessionDialog(
    currentTitle: String,
    onDismiss: () -> Unit,
    onRename: (String) -> Unit,
) {
    var title by remember(currentTitle) { mutableStateOf(currentTitle) }
    val titleBlank = title.isBlank()
    RuntimeAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.chat_rename_session), fontWeight = FontWeight.Medium) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
            ) {
                RuntimeFilledTextField(
                    value = title,
                    onValueChange = { title = it },
                    labelText = stringResource(R.string.chat_title_label),
                    singleLine = true,
                    isError = titleBlank,
                    supportingText = {
                        if (titleBlank) {
                            Text(
                                stringResource(R.string.chat_title_required),
                                color = RuntimeTheme.colorScheme.error,
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onRename(title) },
                enabled = !titleBlank,
            ) { Text(stringResource(R.string.chat_save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.chat_cancel)) } },
    )
}

@Composable
internal fun WorkspaceOption(name: String, path: String, selected: Boolean, onSelect: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onSelect)
            .background(
                if (selected) RuntimeTheme.colorScheme.primary.copy(alpha = 0.12f) else RuntimeTheme.colorScheme.surfaceContainerLow,
                RuntimeTheme.shapes.chip,
            )
            .border(
                1.dp,
                if (selected) RuntimeTheme.colorScheme.primary.copy(alpha = 0.4f) else RuntimeTheme.colorScheme.outlineVariant,
                RuntimeTheme.shapes.chip,
            )
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            Modifier.size(12.dp).clip(CircleShape).background(
                if (selected) RuntimeTheme.colorScheme.primary else RuntimeTheme.colorScheme.onSurfaceVariant,
            ),
        )
        Column {
            Text(name.ifBlank { path }, style = RuntimeTheme.typography.bodyMedium.copy(fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal))
            if (path.isNotBlank()) {
                Text(path, style = RuntimeTheme.typography.labelSmall, color = RuntimeTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
