package top.wkbin.taixu.ui.workflow

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import top.wkbin.taixu.core.database.WorkflowScheduleEntity
import top.wkbin.taixu.core.model.workflow.WorkflowDefinition
import top.wkbin.taixu.core.model.workflow.WorkflowRuntimeState
import top.wkbin.taixu.harness.workflow.SentinelState
import top.wkbin.taixu.ui.components.RuntimeActionRow
import top.wkbin.taixu.ui.components.RuntimeAlertDialog
import top.wkbin.taixu.ui.components.RuntimeButton
import top.wkbin.taixu.ui.components.RuntimeCard
import top.wkbin.taixu.ui.components.RuntimeIcon
import top.wkbin.taixu.ui.components.RuntimeIconName
import top.wkbin.taixu.ui.components.RuntimeOutlinedButton
import top.wkbin.taixu.ui.components.RuntimeSwitch as Switch
import top.wkbin.taixu.ui.components.runtimeContentPadding
import top.wkbin.taixu.ui.miuix.RuntimeSpacing
import top.wkbin.taixu.ui.miuix.RuntimeTheme
import top.yukonga.miuix.kmp.basic.Text
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
internal fun WorkflowCatalog(
    definitions: List<WorkflowDefinition>,
    onRun: (WorkflowDefinition) -> Unit,
    onEdit: (WorkflowDefinition) -> Unit,
    onDelete: (WorkflowDefinition) -> Unit,
    onCreate: () -> Unit,
    onImport: (WorkflowDefinition) -> Unit,
    history: List<WorkflowHistoryEntry>,
    onHistory: (WorkflowRuntimeState) -> Unit,
    onRerun: (WorkflowHistoryEntry) -> Unit,
    onResume: (WorkflowRuntimeState, Set<String>) -> Unit,
    backgroundRuns: List<WorkflowRuntimeState>,
    onViewBackgroundRun: (String) -> Unit,
    schedules: List<WorkflowScheduleEntity> = emptyList(),
    onNewSchedule: () -> Unit = {},
    onEditSchedule: (WorkflowScheduleEntity) -> Unit = {},
    onToggleSchedule: (String, Boolean) -> Unit = { _, _ -> },
    onDeleteSchedule: (String) -> Unit = {},
    sentinelState: SentinelState = SentinelState(),
    onEnableSentinel: (Int, Int) -> Unit = { _, _ -> },
    onDisableSentinel: () -> Unit = {},
    modifier: Modifier = Modifier,
    topPadding: androidx.compose.ui.unit.Dp = 0.dp,
    bottomPadding: androidx.compose.ui.unit.Dp = 0.dp,
) {
    var deleteRequested by remember { mutableStateOf<WorkflowDefinition?>(null) }
    LazyColumn(
        modifier = modifier,
        contentPadding = runtimeContentPadding(
            top = RuntimeSpacing.pageTop + topPadding,
            bottom = RuntimeSpacing.pageBottom + bottomPadding,
        ),
        verticalArrangement = Arrangement.spacedBy(RuntimeSpacing.cardGap),
    ) {
        item { WorkflowCatalogEntry(onCreate, onImport) }
        if (backgroundRuns.isNotEmpty()) {
            item(key = "background_banner") {
                RuntimeCard(modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RuntimeIcon(RuntimeIconName.Play, Modifier.size(18.dp), tint = RuntimeTheme.colorScheme.onSurface)
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                if (backgroundRuns.size == 1) "1 个工作流正在后台运行" else "${backgroundRuns.size} 个工作流正在后台运行",
                                style = RuntimeTheme.typography.titleSmall,
                                fontWeight = FontWeight.Medium,
                            )
                            Text(
                                backgroundRuns.joinToString("、") { it.definition.name },
                                style = RuntimeTheme.typography.labelSmall,
                                color = RuntimeTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        RuntimeOutlinedButton(
                            onClick = { onViewBackgroundRun(backgroundRuns.first().executionId) },
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                        ) {
                            Text("查看", maxLines = 1)
                        }
                    }
                }
            }
        }
        items(definitions, key = { it.id }) { workflow ->
            RuntimeCard(modifier = Modifier.fillMaxWidth(), shape = RuntimeTheme.shapes.extraLarge, containerColor = RuntimeTheme.colorScheme.surfaceContainer, contentPadding = PaddingValues(RuntimeSpacing.cardContent)) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RuntimeIcon(RuntimeIconName.Hub, Modifier.size(22.dp), tint = RuntimeTheme.colorScheme.onSurface)
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(workflow.name, style = RuntimeTheme.typography.titleMedium, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("${workflow.category} · ${workflow.nodes.size} 个节点", style = RuntimeTheme.typography.labelSmall, color = RuntimeTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Text(workflow.description, style = RuntimeTheme.typography.bodyMedium, color = RuntimeTheme.colorScheme.onSurfaceVariant)
                    RuntimeActionRow(modifier = Modifier.fillMaxWidth()) {
                        if (!workflow.isBuiltin) {
                            RuntimeOutlinedButton(onClick = { deleteRequested = workflow }) {
                                RuntimeIcon(RuntimeIconName.Trash, Modifier.size(16.dp))
                            }
                        }
                        RuntimeOutlinedButton(onClick = { onEdit(workflow) }) {
                            RuntimeIcon(RuntimeIconName.Edit, Modifier.size(16.dp))
                            Text(if (workflow.isBuiltin) "创建副本" else "编辑", maxLines = 1)
                        }
                        RuntimeButton(onClick = { onRun(workflow) }) {
                            RuntimeIcon(RuntimeIconName.Play, Modifier.size(16.dp))
                            Text("运行", maxLines = 1)
                        }
                    }
                }
            }
        }
        item(key = "sentinel") {
            WorkflowSentinelCard(sentinelState, onEnableSentinel, onDisableSentinel)
        }
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "定时计划",
                    modifier = Modifier.weight(1f),
                    style = RuntimeTheme.typography.titleMedium,
                )
                    RuntimeOutlinedButton(onClick = onNewSchedule, contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)) {
                        RuntimeIcon(RuntimeIconName.Plus, Modifier.size(16.dp))
                        Text("新建计划", maxLines = 1)
                    }
                }
            }
            if (schedules.isEmpty()) {
                item {
                    Text(
                        "还没有定时计划。把工作流设为每天定时运行、周期巡检或一次性延时触发；到点即使应用未在运行，系统也会拉起执行。",
                        style = RuntimeTheme.typography.bodyMedium,
                        color = RuntimeTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            items(schedules, key = { "schedule:${it.id}" }) { schedule ->
                val workflowName = definitions.firstOrNull { it.id == schedule.workflowId }?.name ?: schedule.workflowId
                RuntimeCard(modifier = Modifier.fillMaxWidth(), shape = RuntimeTheme.shapes.extraLarge, containerColor = RuntimeTheme.colorScheme.surfaceContainer, contentPadding = PaddingValues(RuntimeSpacing.cardContent)) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RuntimeIcon(RuntimeIconName.Refresh, Modifier.size(20.dp), tint = RuntimeTheme.colorScheme.onSurface)
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(schedule.name, style = RuntimeTheme.typography.titleSmall, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(
                                    "$workflowName · ${scheduleRepeatLabel(schedule)}" +
                                        schedule.nextRunAt?.let { next ->
                                            " · 下次 ${java.text.SimpleDateFormat("MM-dd HH:mm", java.util.Locale.getDefault()).format(java.util.Date(next))}"
                                        }.orEmpty(),
                                    style = RuntimeTheme.typography.labelSmall,
                                    color = RuntimeTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Switch(checked = schedule.enabled, onCheckedChange = { onToggleSchedule(schedule.id, it) })
                        }
                        schedule.lastExecutionId?.let {
                            Text(
                                "上次触发：${schedule.lastRunAt?.let { last -> java.text.SimpleDateFormat("MM-dd HH:mm", java.util.Locale.getDefault()).format(java.util.Date(last)) } ?: "—"}",
                                style = RuntimeTheme.typography.labelSmall,
                                color = RuntimeTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        RuntimeActionRow(modifier = Modifier.fillMaxWidth()) {
                            RuntimeOutlinedButton(onClick = { onDeleteSchedule(schedule.id) }) {
                                RuntimeIcon(RuntimeIconName.Trash, Modifier.size(16.dp))
                            }
                            RuntimeOutlinedButton(onClick = { onEditSchedule(schedule) }) {
                                RuntimeIcon(RuntimeIconName.Edit, Modifier.size(16.dp))
                                Text("编辑", maxLines = 1)
                            }
                        }
                    }
                }
            }
        if (history.isNotEmpty()) item { Text("最近运行（最近 ${history.size} 条）", style = RuntimeTheme.typography.titleMedium) }
        items(history, key = { "history:${it.state.executionId}" }) { entry ->
            WorkflowHistoryCard(entry, onRerun, onHistory, onResume)
        }
    }
    deleteRequested?.let { workflow ->
        RuntimeAlertDialog(
            onDismissRequest = { deleteRequested = null },
            title = { Text("删除工作流？") },
            text = { Text("“${workflow.name}”及其运行历史将被永久删除，无法撤销。") },
            dismissButton = { RuntimeOutlinedButton(onClick = { deleteRequested = null }) { Text("取消") } },
            confirmButton = {
                RuntimeButton(
                    onClick = {
                        deleteRequested = null
                        onDelete(workflow)
                    },
                ) { Text("删除") }
            },
        )
    }
}

