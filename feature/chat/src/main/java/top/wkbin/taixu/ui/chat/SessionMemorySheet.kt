package top.wkbin.taixu.ui.chat

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import top.wkbin.taixu.ui.miuix.minimumInteractiveComponentSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import top.wkbin.taixu.ui.miuix.ButtonDefaults
import top.wkbin.taixu.ui.miuix.RuntimeTheme
import top.wkbin.taixu.ui.miuix.ModalBottomSheet
import top.wkbin.taixu.ui.miuix.rememberModalBottomSheetState
import top.wkbin.taixu.ui.miuix.Surface
import top.yukonga.miuix.kmp.basic.Text
import top.wkbin.taixu.ui.components.RuntimeButton as Button
import top.wkbin.taixu.ui.components.RuntimeTextButton as TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import top.wkbin.taixu.core.database.AgentMemoryEntity
import top.wkbin.taixu.core.database.AgentScratchpadEntity
import top.wkbin.taixu.feature.chat.R
import top.wkbin.taixu.ui.components.RuntimeAlertDialog
import top.wkbin.taixu.ui.components.RuntimeIcon
import top.wkbin.taixu.ui.components.RuntimeIconName
import top.wkbin.taixu.ui.components.TaixuMemoryEmptyIllustration

/**
 * 会话工作记忆抽屉：长期记忆（memory 工具写入的 AgentMemoryEntity，全量观察流）
 * 与任务草稿便签（scratchpad 工具写入的 AgentScratchpadEntity）的管理入口——
 * 模型记了什么、存了什么草稿，用户可查、可删。
 */
@Composable
internal fun SessionMemorySheet(
    memories: List<AgentMemoryEntity>,
    scratchpads: List<AgentScratchpadEntity>,
    sessionId: String,
    failureMemoryAccess: top.wkbin.taixu.harness.operation.FailureMemoryAccess?,
    onDeleteMemory: (String) -> Unit,
    onDeleteScratchpad: (String) -> Unit,
    onClearScratchpads: () -> Unit,
    onDismiss: () -> Unit,
) {
    val haptic = LocalHapticFeedback.current

    // 破坏性操作待确认目标：非 null 时弹出二次确认
    var confirmTarget by rememberSaveable { mutableStateOf<String?>(null) } // "clear" / "memory:<id>"

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                RuntimeIcon(RuntimeIconName.Brain, Modifier.size(20.dp), RuntimeTheme.colorScheme.primary)
                Text(
                    stringResource(R.string.chat_memory_sheet_title),
                    style = RuntimeTheme.typography.titleMedium,
                )
            }

            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (failureMemoryAccess != null) {
                    item(key = "failure_memories:$sessionId") { FailureMemoryPanel(sessionId, failureMemoryAccess) }
                }
                // —— 长期记忆 ——
                item(key = "header_memories") {
                    SectionHeader(
                        icon = RuntimeIconName.Brain,
                        title = stringResource(R.string.chat_memory_section, memories.size),
                    )
                }
                if (memories.isEmpty()) {
                    item(key = "memories_empty") {
                        EmptyHint(stringResource(R.string.chat_memory_empty), illustration = true)
                    }
                } else {
                    items(memories.size, key = { memories[it].id }) { index ->
                        val memory = memories[index]
                        MemoryRow(
                            scope = memory.scope,
                            kind = memory.kind,
                            keyText = memory.key,
                            value = memory.value,
                            onDelete = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                confirmTarget = "memory:${memory.id}"
                            },
                        )
                    }
                }

                // —— 任务草稿 ——
                item(key = "header_scratchpads") {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        SectionHeader(
                            icon = RuntimeIconName.Copy,
                            title = stringResource(R.string.chat_scratchpad_section, scratchpads.size),
                            modifier = Modifier.weight(1f),
                        )
                        if (scratchpads.isNotEmpty()) {
                            TextButton(onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                confirmTarget = "clear"
                            }) {
                                Text(
                                    stringResource(R.string.chat_scratchpad_clear),
                                    style = RuntimeTheme.typography.labelMedium,
                                    color = RuntimeTheme.colorScheme.error,
                                )
                            }
                        }
                    }
                }
                if (scratchpads.isEmpty()) {
                    item(key = "scratchpads_empty") {
                        EmptyHint(stringResource(R.string.chat_scratchpad_empty))
                    }
                } else {
                    items(scratchpads.size, key = { scratchpads[it].key }) { index ->
                        val pad = scratchpads[index]
                        MemoryRow(
                            scope = null,
                            kind = null,
                            keyText = pad.key,
                            value = pad.value,
                            onDelete = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onDeleteScratchpad(pad.key)
                            },
                        )
                    }
                }

                item(key = "sheet_bottom_spacer") {
                    Spacer(Modifier.size(24.dp))
                }
            }
        }
    }

    // 破坏性操作二次确认（清空草稿 / 删除单条记忆），样式对齐 DistroManagementScreen 的确认弹窗
    confirmTarget?.let { target ->
        val isClear = target == "clear"
        RuntimeAlertDialog(
            onDismissRequest = { confirmTarget = null },
            title = {
                Text(
                    text = stringResource(
                        if (isClear) R.string.chat_clear_scratchpad_title else R.string.chat_delete_memory_title,
                    ),
                    style = RuntimeTheme.typography.titleMedium,
                )
            },
            text = {
                Text(
                    text = stringResource(
                        if (isClear) R.string.chat_clear_scratchpad_message else R.string.chat_delete_memory_message,
                    ),
                    style = RuntimeTheme.typography.bodyMedium,
                    color = RuntimeTheme.colorScheme.onSurfaceVariant,
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (isClear) {
                            onClearScratchpads()
                        } else {
                            onDeleteMemory(target.removePrefix("memory:"))
                        }
                        confirmTarget = null
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = RuntimeTheme.colorScheme.error,
                    ),
                ) {
                    Text(
                        text = stringResource(if (isClear) R.string.chat_confirm_clear else R.string.chat_confirm_delete),
                        color = RuntimeTheme.colorScheme.onError,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmTarget = null }) {
                    Text(text = stringResource(R.string.chat_cancel))
                }
            },
        )
    }
}

@Composable
private fun SectionHeader(
    icon: RuntimeIconName,
    title: String,
    modifier: Modifier = Modifier,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = modifier.fillMaxWidth(),
    ) {
        RuntimeIcon(icon, Modifier.size(15.dp), RuntimeTheme.colorScheme.primary)
        Text(
            title,
            style = RuntimeTheme.typography.titleSmall,
        )
    }
}

@Composable
private fun EmptyHint(text: String, illustration: Boolean = false) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        if (illustration) {
            TaixuMemoryEmptyIllustration()
        }
        Text(
            text,
            style = RuntimeTheme.typography.bodySmall,
            color = RuntimeTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun MemoryRow(
    scope: String?,
    kind: String?,
    keyText: String,
    value: String,
    onDelete: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth().clip(RuntimeTheme.shapes.small),
        color = RuntimeTheme.colorScheme.surfaceContainerLow,
        shape = RuntimeTheme.shapes.small,
    ) {
        Row(
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (scope != null) {
                        Surface(color = RuntimeTheme.colorScheme.primary.copy(alpha = 0.12f), shape = RuntimeTheme.shapes.tiny) {
                            Text(
                                scope,
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp),
                                style = RuntimeTheme.typography.labelSmall,
                                color = RuntimeTheme.colorScheme.primary,
                            )
                        }
                    }
                    if (kind != null) {
                        Text(
                            kind,
                            style = RuntimeTheme.typography.labelSmall,
                            color = RuntimeTheme.colorScheme.tertiary,
                        )
                    }
                    Text(
                        keyText,
                        style = RuntimeTheme.typography.labelMedium.copy(fontWeight = FontWeight.Medium),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                }
                Text(
                    value,
                    style = RuntimeTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    color = RuntimeTheme.colorScheme.onSurfaceVariant,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Box(
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .clip(androidx.compose.foundation.shape.CircleShape)
                    .clickable(onClick = onDelete),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(androidx.compose.foundation.shape.CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    RuntimeIcon(
                        RuntimeIconName.Close,
                        Modifier.size(16.dp),
                        RuntimeTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
