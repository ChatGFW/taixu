package top.wkbin.taixu.ui.bot

import top.wkbin.taixu.ui.miuix.RuntimeSpacing
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import top.wkbin.taixu.ui.miuix.RuntimeTheme
import top.yukonga.miuix.kmp.basic.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import top.wkbin.taixu.core.model.bot.BotMemoryFile
import top.wkbin.taixu.core.model.bot.MemoryCommit
import top.wkbin.taixu.feature.bot.R
import top.wkbin.taixu.ui.components.RuntimeAlertDialog
import top.wkbin.taixu.ui.components.RuntimeButton
import top.wkbin.taixu.ui.components.RuntimeCard
import top.wkbin.taixu.ui.components.RuntimeTextButton
import top.wkbin.taixu.ui.components.SectionHeader
import top.wkbin.taixu.ui.components.RuntimeActionRow
import top.wkbin.taixu.ui.components.runtimeContentPadding

@Composable
internal fun BotMemoryPanel(
    files: List<BotMemoryFile>,
    commits: List<MemoryCommit>,
    busy: Boolean,
    onRollback: (String) -> Unit,
    onOpenFile: (String) -> Unit,
    onOpenDiff: (String) -> Unit,
) {
    var pendingRollback by remember { mutableStateOf<MemoryCommit?>(null) }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = runtimeContentPadding(top = RuntimeSpacing.pageTop, bottom = RuntimeSpacing.pageBottom),
        verticalArrangement = Arrangement.Top,
    ) {
        item {
            SectionHeader(
                title = stringResource(R.string.fbot_memory_files),
                subtitle = "${files.size}",
                modifier = Modifier.padding(bottom = 4.dp),
            )
        }
        if (files.isEmpty()) {
            item {
                Text(
                    stringResource(R.string.fbot_memory_empty),
                    style = RuntimeTheme.typography.bodySmall,
                    color = RuntimeTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 12.dp),
                )
            }
        }
        items(files, key = { it.path }) { file ->
            RuntimeCard(
                onClick = if (!busy && file.path.endsWith(".md", true)) ({ onOpenFile(file.path) }) else null,
                modifier = Modifier.padding(bottom = 12.dp),
                contentPadding = PaddingValues(16.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        file.path,
                        style = RuntimeTheme.typography.bodyMedium,
                        fontFamily = FontFamily.Monospace,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        formatBytes(file.sizeBytes),
                        style = RuntimeTheme.typography.labelSmall,
                        color = RuntimeTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 10.dp),
                    )
                }
            }
        }
        item {
            SectionHeader(
                title = stringResource(R.string.fbot_memory_commits),
                subtitle = "${commits.size}",
                modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
            )
        }
        if (commits.isEmpty()) {
            item {
                Text(
                    stringResource(R.string.fbot_commits_empty),
                    style = RuntimeTheme.typography.bodySmall,
                    color = RuntimeTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        itemsIndexed(commits, key = { _, commit -> commit.hash }) { index, commit ->
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
                CommitTimeline(
                    isFirst = index == 0,
                    isLast = index == commits.lastIndex,
                    modifier = Modifier.width(24.dp).fillMaxHeight(),
                )
                RuntimeCard(
                    onClick = { onOpenDiff(commit.hash) },
                    modifier = Modifier.weight(1f).padding(bottom = 12.dp),
                    contentPadding = PaddingValues(16.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            commit.shortHash,
                            style = RuntimeTheme.typography.labelMedium,
                            fontFamily = FontFamily.Monospace,
                            color = RuntimeTheme.colorScheme.primary,
                        )
                        Text(
                            commit.message,
                            style = RuntimeTheme.typography.bodyMedium,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f).padding(horizontal = 10.dp),
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            "${commit.author} · ${formatEpoch(commit.timestamp)}",
                            style = RuntimeTheme.typography.labelSmall,
                            color = RuntimeTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        if (commit.filesChanged.isNotEmpty()) {
                            Text(
                                commit.filesChanged.first(),
                                style = RuntimeTheme.typography.labelSmall,
                                color = RuntimeTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                    RuntimeActionRow(modifier = Modifier.fillMaxWidth()) {
                        RuntimeTextButton(
                            onClick = { onOpenDiff(commit.hash) },
                            enabled = !busy,
                        ) { Text(stringResource(R.string.fbot_memory_diff), maxLines = 1) }
                        RuntimeTextButton(
                            onClick = { pendingRollback = commit },
                            enabled = !busy,
                        ) { Text(stringResource(R.string.fbot_rollback), maxLines = 1) }
                    }
                }
            }
        }
    }

    pendingRollback?.let { commit ->
        RuntimeAlertDialog(
            onDismissRequest = { pendingRollback = null },
            title = { Text(stringResource(R.string.fbot_rollback_title)) },
            text = { Text(stringResource(R.string.fbot_rollback_confirm, commit.shortHash)) },
            confirmButton = {
                RuntimeButton(onClick = {
                    onRollback(commit.hash)
                    pendingRollback = null
                }) { Text(stringResource(R.string.fbot_rollback)) }
            },
            dismissButton = {
                RuntimeTextButton(onClick = { pendingRollback = null }) {
                    Text(stringResource(R.string.fbot_cancel))
                }
            },
        )
    }
}

/** Git 提交时间轴：节点 + 连接线，首尾提交自动收口。 */
@Composable
private fun CommitTimeline(isFirst: Boolean, isLast: Boolean, modifier: Modifier = Modifier) {
    val lineColor = RuntimeTheme.colorScheme.outlineVariant
    val nodeColor = RuntimeTheme.colorScheme.primary
    val holeColor = RuntimeTheme.colorScheme.surface
    Canvas(modifier) {
        val centerX = size.width / 2f
        val nodeY = NODE_OFFSET.toPx()
        val radius = 5.dp.toPx()
        val stroke = 2.dp.toPx()
        if (!isFirst) drawLine(lineColor, Offset(centerX, 0f), Offset(centerX, nodeY - radius), stroke)
        if (!isLast) drawLine(lineColor, Offset(centerX, nodeY + radius), Offset(centerX, size.height), stroke)
        drawCircle(nodeColor, radius, Offset(centerX, nodeY))
        drawCircle(holeColor, radius * 0.4f, Offset(centerX, nodeY))
    }
}

private val NODE_OFFSET = 18.dp
