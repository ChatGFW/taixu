package top.wkbin.taixu.ui.git

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import top.wkbin.taixu.feature.git.R
import top.wkbin.taixu.ui.components.RuntimeCard
import top.wkbin.taixu.ui.components.RuntimeCircularProgressIndicator
import top.wkbin.taixu.ui.components.RuntimeFilledTextField
import top.wkbin.taixu.ui.components.RuntimeIcon
import top.wkbin.taixu.ui.components.RuntimeIconButton
import top.wkbin.taixu.ui.components.RuntimeIconName
import top.wkbin.taixu.ui.components.StatusBadge
import top.wkbin.taixu.ui.miuix.*
import top.yukonga.miuix.kmp.basic.Text

// ----------------------------------------------------------------------
// 改动页签：选文件暂存 → AI/手写 message → 提交
// ----------------------------------------------------------------------

@Composable
internal fun ChangesTab(
    state: GitUiState,
    onToggleStage: (GitManager.GitFileChange) -> Unit,
    onStageAll: () -> Unit,
    onUnstageAll: () -> Unit,
    onDiscard: (GitManager.GitFileChange) -> Unit,
    loadDiff: suspend (GitManager.GitFileChange) -> GitChangeDiff,
    onCommit: (String) -> Unit,
    onCommitAndPush: (String) -> Unit,
    onGenerateMessage: () -> Unit,
    onMessageChange: (String) -> Unit,
) {
    // message 草稿来自 ViewModel：返回聊天页后 AI 生成的结果不丢
    val message = state.commitMessageDraft
    val staged = state.changes.filter { it.staged }
    val conflicts = state.changes.filter { it.changeType == GitManager.ChangeType.CONFLICT }
    val unstaged = state.changes.filter { !it.staged && it.changeType != GitManager.ChangeType.CONFLICT }
    val stagedCount = staged.size
    val canCommit = !state.busy && conflicts.isEmpty() && stagedCount > 0 && message.isNotBlank()
    var diffTarget by remember(state.projectPath) { mutableStateOf<GitManager.GitFileChange?>(null) }
    diffTarget?.let { change ->
        GitChangeDiffDialog(change, loadDiff = { loadDiff(change) }, onDismiss = { diffTarget = null })
    }

    Column(Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 6.dp, bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            if (state.changes.isEmpty()) {
                item {
                    EmptyHint(stringResource(R.string.fgit_no_changes))
                }
            } else {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            stringResource(R.string.fgit_staged_count, stagedCount, state.changes.distinctBy { it.path }.size),
                            style = RuntimeTheme.typography.titleSmall,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.weight(1f),
                        )
                        if (unstaged.isNotEmpty()) {
                            Surface(
                                onClick = onStageAll,
                                enabled = !state.busy,
                                color = RuntimeTheme.colorScheme.primary.copy(alpha = 0.12f),
                                shape = RuntimeTheme.shapes.chip,
                            ) {
                                Text(
                                    stringResource(R.string.fgit_stage_all),
                                    style = RuntimeTheme.typography.labelMedium.copy(fontWeight = FontWeight.Medium),
                                    color = RuntimeTheme.colorScheme.primary,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                )
                            }
                        }
                        if (stagedCount > 0 && conflicts.isEmpty()) {
                            Surface(
                                onClick = onUnstageAll,
                                enabled = !state.busy,
                                color = RuntimeTheme.colorScheme.surfaceContainerHigh,
                                shape = RuntimeTheme.shapes.chip,
                            ) {
                                Text(
                                    stringResource(R.string.fgit_unstage_all),
                                    style = RuntimeTheme.typography.labelMedium,
                                    color = RuntimeTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                )
                            }
                        }
                    }
                }
                listOf(
                    R.string.fgit_changes_conflicts to conflicts,
                    R.string.fgit_staged to staged,
                    R.string.fgit_changes_unstaged to unstaged,
                ).forEach { (title, changes) ->
                    if (changes.isNotEmpty()) {
                        item(key = title) {
                            Text(
                                stringResource(title),
                                style = RuntimeTheme.typography.titleSmall,
                                modifier = Modifier.padding(top = 8.dp, bottom = 2.dp),
                            )
                        }
                        items(changes, key = { "$title:${it.path}" }) { change ->
                            ChangeRow(
                                change = change,
                                enabled = !state.busy,
                                onToggle = { onToggleStage(change) },
                                onDiscard = { onDiscard(change) },
                                onDiff = { diffTarget = change },
                            )
                        }
                    }
                }
            }
        }

        // 底部提交栏：message 输入 + AI 生成 + 提交按钮
        Surface(
            color = RuntimeTheme.colorScheme.surfaceContainerLow,
            shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    RuntimeFilledTextField(
                        value = message,
                        onValueChange = onMessageChange,
                        placeholder = { Text(stringResource(R.string.fgit_message_placeholder), style = RuntimeTheme.typography.bodySmall) },
                        textStyle = RuntimeTheme.typography.bodySmall,
                        minLines = 1,
                        maxLines = 4,
                        modifier = Modifier.weight(1f),
                    )
                    if (state.operation == GitOperation.AI_MESSAGE) {
                        RuntimeCircularProgressIndicator(Modifier.size(22.dp))
                    } else {
                        val aiColor = gitAiColor()
                        Surface(
                            onClick = onGenerateMessage,
                            enabled = !state.busy && state.changes.isNotEmpty(),
                            color = aiColor.copy(alpha = 0.12f),
                            shape = RuntimeTheme.shapes.small,
                            modifier = Modifier.minimumInteractiveComponentSize(),
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                RuntimeIcon(RuntimeIconName.Sparkles, Modifier.size(15.dp), aiColor)
                                Text(
                                    stringResource(R.string.fgit_ai_message),
                                    style = RuntimeTheme.typography.labelMedium.copy(fontWeight = FontWeight.Medium),
                                    color = aiColor,
                                )
                            }
                        }
                    }
                }
                val commitColor = gitSuccessColor()
                val pushColor = gitInfoColor()
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Surface(
                        onClick = { onCommit(message) },
                        enabled = canCommit,
                        shape = RuntimeTheme.shapes.small,
                        color = if (canCommit) commitColor.copy(alpha = 0.16f) else RuntimeTheme.colorScheme.surfaceContainerHigh,
                        modifier = Modifier.weight(1f).minimumInteractiveComponentSize(),
                    ) {
                        Text(
                            text = if (state.operation == GitOperation.COMMIT) {
                                stringResource(R.string.fgit_committing)
                            } else {
                                stringResource(
                                    if (stagedCount > 0) R.string.fgit_commit_n else R.string.fgit_commit_no_staged,
                                    stagedCount,
                                )
                            },
                            style = RuntimeTheme.typography.labelLarge,
                            color = if (canCommit) commitColor else RuntimeTheme.colorScheme.onSurfaceVariant,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            modifier = Modifier.padding(vertical = 10.dp),
                        )
                    }
                    // 提交并推送：commit 成功后立即 push（省一次点击）
                    Surface(
                        onClick = { onCommitAndPush(message) },
                        enabled = canCommit,
                        shape = RuntimeTheme.shapes.small,
                        color = if (canCommit) pushColor.copy(alpha = 0.14f) else RuntimeTheme.colorScheme.surfaceContainerHigh,
                        modifier = Modifier.minimumInteractiveComponentSize(),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            RuntimeIcon(RuntimeIconName.ArrowUp, Modifier.size(15.dp), if (canCommit) pushColor else RuntimeTheme.colorScheme.onSurfaceVariant)
                            Text(
                                stringResource(R.string.fgit_commit_push),
                                style = RuntimeTheme.typography.labelLarge,
                                color = if (canCommit) pushColor else RuntimeTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ChangeRow(
    change: GitManager.GitFileChange,
    enabled: Boolean,
    onToggle: () -> Unit,
    onDiscard: () -> Unit,
    onDiff: () -> Unit,
) {
    val (typeLabel, typeTint) = when (change.changeType) {
        GitManager.ChangeType.ADDED -> R.string.fgit_change_added to gitSuccessColor()
        GitManager.ChangeType.MODIFIED -> R.string.fgit_change_modified to gitInfoColor()
        GitManager.ChangeType.DELETED -> R.string.fgit_change_deleted to RuntimeTheme.colorScheme.error
        GitManager.ChangeType.UNTRACKED -> R.string.fgit_change_untracked to gitWarningColor()
        GitManager.ChangeType.CONFLICT -> R.string.fgit_change_conflict to RuntimeTheme.colorScheme.error
    }
    val conflict = change.changeType == GitManager.ChangeType.CONFLICT
    RuntimeCard(
        containerColor = RuntimeTheme.colorScheme.surfaceContainerLow,
        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
        onClick = if (enabled && !conflict) onDiff else null,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            StatusBadge(stringResource(typeLabel), typeTint)
            Text(
                text = change.path,
                style = RuntimeTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                color = RuntimeTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (!change.staged && !conflict) {
                RuntimeIconButton(
                    onClick = onDiscard,
                    enabled = enabled,
                    contentDescription = stringResource(R.string.fgit_discard),
                ) {
                    RuntimeIcon(RuntimeIconName.Reverse, Modifier.size(18.dp), RuntimeTheme.colorScheme.error)
                }
            }
            RuntimeIconButton(
                onClick = onToggle,
                enabled = enabled,
                contentDescription = stringResource(
                    when {
                        conflict -> R.string.fgit_stage_resolved
                        change.staged -> R.string.fgit_unstage_file
                        else -> R.string.fgit_stage_file
                    },
                ),
            ) {
                val icon = when {
                    conflict -> RuntimeIconName.Check
                    change.staged -> RuntimeIconName.ChevronDown
                    else -> RuntimeIconName.Plus
                }
                RuntimeIcon(icon, Modifier.size(18.dp))
            }
        }
    }
}
