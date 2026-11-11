package top.wkbin.taixu.ui.chat

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import top.wkbin.taixu.ui.components.RuntimeAlertDialog
import top.wkbin.taixu.ui.miuix.HorizontalDivider
import top.wkbin.taixu.ui.miuix.RuntimeTheme
import top.wkbin.taixu.ui.miuix.rememberModalBottomSheetState
import top.wkbin.taixu.ui.components.RuntimeFilledTextField
import top.wkbin.taixu.ui.miuix.Surface
import top.yukonga.miuix.kmp.basic.Text
import top.wkbin.taixu.ui.components.RuntimeTextButton as TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import top.wkbin.taixu.core.database.AgentMemoryEntity
import top.wkbin.taixu.core.database.AgentScratchpadEntity
import top.wkbin.taixu.feature.chat.R
import top.wkbin.taixu.harness.AssistantText
import top.wkbin.taixu.harness.CapabilityEvent
import top.wkbin.taixu.harness.ModelSwitchEvent
import top.wkbin.taixu.harness.TurnChangesEvent
import top.wkbin.taixu.harness.SkillSuggestion
import top.wkbin.taixu.harness.TaskDelivery
import top.wkbin.taixu.harness.HarnessMessage
import top.wkbin.taixu.harness.ToolCall
import top.wkbin.taixu.harness.ToolResult
import top.wkbin.taixu.harness.UserMessage
import top.wkbin.taixu.harness.events.HarnessEvent
import top.wkbin.taixu.harness.session.ConversationBranch
import top.wkbin.taixu.harness.session.ConversationBranchKind
import top.wkbin.taixu.ui.components.RuntimeCard
import top.wkbin.taixu.ui.components.RuntimeIcon
import top.wkbin.taixu.ui.components.StatusBadge
import top.wkbin.taixu.ui.theme.semanticColors
import top.wkbin.taixu.ui.components.RuntimeIconName
import top.wkbin.taixu.ui.components.RuntimeCircularProgressIndicator
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import top.wkbin.taixu.ui.components.RuntimeIconButton
import top.wkbin.taixu.ui.components.RuntimeActionRow
import top.wkbin.taixu.ui.components.RuntimeExpandChevron

@Composable
internal fun StatusDivider() {
    Box(
        modifier = Modifier
            .padding(horizontal = 2.dp)
            .size(width = 1.dp, height = 9.dp)
            .background(RuntimeTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
    )
}

@Composable
internal fun WorkbenchStatusItem(
    icon: RuntimeIconName,
    label: String,
    tint: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    highlight: Boolean = false,
) {
    Surface(
        onClick = onClick,
        shape = RuntimeTheme.shapes.tiny,
        color = if (highlight) tint.copy(alpha = 0.14f) else Color.Transparent,
        modifier = modifier,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            RuntimeIcon(
                name = icon,
                modifier = Modifier.size(11.dp),
                tint = tint,
            )
            Text(
                text = label,
                style = RuntimeTheme.typography.labelSmall.copy(
                    fontSize = 11.sp,
                    fontWeight = if (highlight) FontWeight.Medium else FontWeight.Medium,
                ),
                color = if (highlight) tint else RuntimeTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
internal fun BranchBrowserSheet(
    branches: List<ConversationBranch>,
    running: Boolean,
    onDismiss: () -> Unit,
    onSwitch: (ConversationBranch) -> Unit,
    onOpenSubagent: (ConversationBranch) -> Unit,
) {
    val conversation = remember(branches) {
        branches.filter { it.kind != ConversationBranchKind.SUBAGENT }
    }
    val subagents = remember(branches) { stableSubagentBranches(branches) }

    PlainBackdropModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item(key = "branch_sheet_title") {
                Text(
                    stringResource(R.string.chat_branches_title),
                    style = RuntimeTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Medium,
                )
            }
            item(key = "branch_sheet_description") {
                Text(
                    stringResource(R.string.chat_branches_description),
                    style = RuntimeTheme.typography.bodySmall,
                    color = RuntimeTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp, bottom = 6.dp),
                )
            }
            if (branches.isEmpty()) {
                item(key = "branch_sheet_empty") {
                    RuntimeCard {
                        Text(
                            stringResource(R.string.chat_no_branches),
                            color = RuntimeTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            } else {
                item(key = "branch_sheet_conversation_header") {
                    SectionLabel(stringResource(R.string.chat_conversation_paths), conversation.size)
                }
                items(conversation, key = ::branchUiKey) { branch ->
                    BranchCard(branch, enabled = !running && !branch.isBusy, onClick = { onSwitch(branch) })
                }
                if (subagents.isNotEmpty()) {
                    item(key = "branch_sheet_subagent_header") {
                        SectionLabel(stringResource(R.string.chat_subagent_lanes), subagents.size)
                    }
                    items(subagents, key = ::branchUiKey) { branch ->
                        BranchCard(
                            branch = branch,
                            enabled = branch.laneName != null,
                            onClick = { onOpenSubagent(branch) },
                        )
                    }
                }
            }
            item(key = "branch_sheet_bottom_spacer") {
                Spacer(Modifier.height(32.dp))
            }
        }
    }
}

internal fun branchUiKey(branch: ConversationBranch): String =
    if (branch.kind == ConversationBranchKind.SUBAGENT) branch.laneName ?: branch.id else branch.id

internal fun stableSubagentBranches(branches: List<ConversationBranch>): List<ConversationBranch> =
    branches.asSequence()
        .filter { it.kind == ConversationBranchKind.SUBAGENT }
        .sortedBy(::branchUiKey)
        .toList()

@Composable
private fun SectionLabel(title: String, count: Int) {
    Row(Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = RuntimeTheme.typography.titleSmall, fontWeight = FontWeight.Medium)
        Spacer(Modifier.weight(1f))
        Text("$count", style = RuntimeTheme.typography.labelMedium, color = RuntimeTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun BranchCard(branch: ConversationBranch, enabled: Boolean, onClick: () -> Unit) {
    val tint = when (branch.kind) {
        ConversationBranchKind.MAIN -> RuntimeTheme.colorScheme.primary
        ConversationBranchKind.BRANCH -> semanticColors().agent
        ConversationBranchKind.SUBAGENT -> RuntimeTheme.colorScheme.tertiary
        ConversationBranchKind.HISTORY -> RuntimeTheme.colorScheme.secondary
    }
    RuntimeCard(
        containerColor = if (branch.isCurrent) tint.copy(alpha = 0.10f) else RuntimeTheme.colorScheme.surfaceContainerLow,
        borderColor = if (branch.isCurrent) tint.copy(alpha = 0.55f) else Color.Transparent,
        contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp),
        onClick = if (enabled && !branch.isCurrent) onClick else null,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(Modifier.size(34.dp).background(tint.copy(alpha = 0.14f), CircleShape), contentAlignment = Alignment.Center) {
                RuntimeIcon(if (branch.kind == ConversationBranchKind.SUBAGENT) RuntimeIconName.Bot else RuntimeIconName.GitBranch, Modifier.size(18.dp), tint)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        branch.name,
                        style = RuntimeTheme.typography.titleSmall,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.weight(1f, fill = false),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (branch.isCurrent) StatusBadge(stringResource(R.string.chat_badge_current), tint)
                    if (branch.isBusy) StatusBadge(stringResource(R.string.chat_badge_busy), semanticColors().agent)
                    if (branch.faulted) StatusBadge(stringResource(R.string.chat_badge_faulted), RuntimeTheme.colorScheme.error)
                }
                Text(
                    branch.preview.ifBlank { stringResource(R.string.chat_branch_start) },
                    style = RuntimeTheme.typography.bodySmall,
                    color = RuntimeTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (branch.isBusy) {
                    // 低优先级修复：running 禁用需给出解释，否则用户不知道为何点不动
                    Text(
                        stringResource(
                            if (branch.kind == ConversationBranchKind.SUBAGENT) {
                                R.string.chat_subagent_live_hint
                            } else {
                                R.string.chat_branch_busy_hint
                            },
                        ),
                        style = RuntimeTheme.typography.labelSmall,
                        color = tint,
                    )
                }
                Text(
                    stringResource(R.string.chat_branch_records, branch.depth) + " · " + stringResource(R.string.chat_branch_tool_calls, branch.toolCallCount),
                    style = RuntimeTheme.typography.labelSmall,
                    color = RuntimeTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (enabled && !branch.isCurrent) RuntimeIcon(RuntimeIconName.ChevronRight, Modifier.size(18.dp), tint)
        }
    }
}

@Composable
internal fun SubagentResultSheet(
    state: SubagentResultUiState,
    onRefresh: () -> Unit,
    onDismiss: () -> Unit,
) {
    val branch = state.branch
    val finalResult = state.messages.filterIsInstance<AssistantText>().lastOrNull { it.text.isNotBlank() }
    val task = state.messages.filterIsInstance<UserMessage>().firstOrNull()
    val toolResults = remember(state.messages) {
        state.messages.filterIsInstance<ToolResult>().associateBy { it.toolCallId }
    }
    // 悬挂工具调用（发起但没落结果）说明 lane 是被超时/异常打断的，最后一条助手文本只是中途独白，
    // 不是结论——标题与提示要换掉，避免把"Let me verify…"这类过程话当成最终成果。
    val interrupted = branch.faulted || state.messages.filterIsInstance<ToolCall>()
        .any { it.id !in toolResults.keys }
    var processExpanded by rememberSaveable(branchUiKey(branch)) { mutableStateOf(false) }

    PlainBackdropModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Box(
                        Modifier.size(38.dp).background(RuntimeTheme.colorScheme.tertiaryContainer, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        RuntimeIcon(RuntimeIconName.Bot, Modifier.size(20.dp), RuntimeTheme.colorScheme.onTertiaryContainer)
                    }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            branch.name,
                            style = RuntimeTheme.typography.titleLarge,
                            fontWeight = FontWeight.Medium,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            stringResource(R.string.chat_subagent_result_description),
                            style = RuntimeTheme.typography.bodySmall,
                            color = RuntimeTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (branch.isBusy) StatusBadge(stringResource(R.string.chat_badge_busy), semanticColors().agent)
                    if (branch.faulted) StatusBadge(stringResource(R.string.chat_badge_faulted), RuntimeTheme.colorScheme.error)
                }
            }

            if (state.loading) {
                item {
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 24.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RuntimeCircularProgressIndicator(Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.chat_subagent_loading), color = RuntimeTheme.colorScheme.onSurfaceVariant)
                    }
                }
            } else if (state.error != null) {
                item {
                    RuntimeCard(containerColor = RuntimeTheme.colorScheme.errorContainer) {
                        Text(state.error, color = RuntimeTheme.colorScheme.onErrorContainer)
                    }
                }
            } else {
                task?.let { taskMessage ->
                    item {
                        RuntimeCard(containerColor = RuntimeTheme.colorScheme.surfaceContainerLow) {
                            Text(
                                stringResource(R.string.chat_subagent_task),
                                style = RuntimeTheme.typography.labelMedium,
                                fontWeight = FontWeight.Medium,
                                color = RuntimeTheme.colorScheme.tertiary,
                            )
                            Spacer(Modifier.height(4.dp))
                            SelectionContainer {
                                Text(
                                    subagentTaskDetails(taskMessage.text),
                                    style = RuntimeTheme.typography.bodyMedium,
                                    color = RuntimeTheme.colorScheme.onSurface,
                                )
                            }
                        }
                    }
                }

                item {
                    Text(
                        stringResource(
                            if (interrupted) R.string.chat_subagent_partial_result else R.string.chat_subagent_final_result,
                        ),
                        style = RuntimeTheme.typography.titleMedium,
                        fontWeight = FontWeight.Medium,
                        color = if (interrupted) RuntimeTheme.colorScheme.error else RuntimeTheme.colorScheme.onSurface,
                    )
                }
                item {
                    RuntimeCard(
                        containerColor = if (interrupted) {
                            RuntimeTheme.colorScheme.errorContainer.copy(alpha = 0.38f)
                        } else {
                            RuntimeTheme.colorScheme.tertiaryContainer.copy(alpha = 0.38f)
                        },
                        borderColor = if (interrupted) {
                            RuntimeTheme.colorScheme.error.copy(alpha = 0.35f)
                        } else {
                            RuntimeTheme.colorScheme.tertiary.copy(alpha = 0.35f)
                        },
                    ) {
                        if (interrupted) {
                            Text(
                                stringResource(R.string.chat_subagent_partial_hint),
                                style = RuntimeTheme.typography.labelMedium,
                                color = RuntimeTheme.colorScheme.onErrorContainer,
                            )
                            Spacer(Modifier.height(4.dp))
                        }
                        if (finalResult != null) {
                            MarkdownText(finalResult.text, Modifier.fillMaxWidth())
                        } else {
                            Text(
                                stringResource(
                                    if (branch.isBusy) R.string.chat_subagent_pending else R.string.chat_subagent_no_result,
                                ),
                                color = RuntimeTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }

                item {
                    RuntimeCard(
                        containerColor = RuntimeTheme.colorScheme.surfaceContainerLow,
                        onClick = { processExpanded = !processExpanded },
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            RuntimeIcon(RuntimeIconName.Logs, Modifier.size(17.dp), RuntimeTheme.colorScheme.onSurface)
                            Text(
                                stringResource(R.string.chat_subagent_process, branch.depth, branch.toolCallCount),
                                style = RuntimeTheme.typography.titleSmall,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.weight(1f),
                            )
                            RuntimeIcon(
                                RuntimeIconName.ChevronDown,
                                Modifier.size(18.dp).rotate(if (processExpanded) 180f else 0f),
                                RuntimeTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }

                if (processExpanded) {
                    items(
                        items = state.messages.filter { it !is ToolResult && it.id != finalResult?.id }, key = { it.id },
                    ) { message ->
                        when (message) {
                            is UserMessage -> Unit
                            is AssistantText -> RuntimeCard(containerColor = RuntimeTheme.colorScheme.surfaceContainerLow) {
                                MarkdownText(message.text, Modifier.fillMaxWidth())
                            }
                            is ToolCall -> ToolCard(
                                call = message,
                                result = toolResults[message.id],
                                workspace = "",
                                onOpenFile = null,
                                running = branch.isBusy,
                                liveStatus = null,
                            )
                            is CapabilityEvent, is TurnChangesEvent -> Unit
                            is SkillSuggestion, is TaskDelivery -> Unit
                            is ModelSwitchEvent -> Unit
                            is ToolResult -> Unit
                        }
                    }
                }
            }

            item {
                RuntimeActionRow(modifier = Modifier.fillMaxWidth()) {
                    TextButton(onClick = onRefresh) { Text(stringResource(R.string.chat_subagent_refresh)) }
                    TextButton(onClick = onDismiss) { Text(stringResource(R.string.chat_close)) }
                }
            }
        }
    }
}

private fun subagentTaskDetails(prompt: String): String {
    val details = prompt.substringAfter("任务详情：", missingDelimiterValue = prompt)
        .substringBefore("你是被主智能体派发的子智能体")
        .trim()
    return details.ifBlank { prompt.trim() }
}

@Composable
internal fun RuntimeTimelineSheet(
    events: List<HarnessEvent>,
    messages: List<HarnessMessage>,
    memories: List<AgentMemoryEntity> = emptyList(),
    scratchpads: List<AgentScratchpadEntity> = emptyList(),
    onDeleteMemory: (String) -> Unit = {},
    onDeleteScratchpad: (String) -> Unit = {},
    onClearScratchpads: () -> Unit = {},
    onNavigateToMessage: (String) -> Unit = {},
    onDismiss: () -> Unit,
) {
    PlainBackdropModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        val (rounds, lifecycle) = remember(events, messages) { buildRoundGroups(events, messages) }
        var memoriesExpanded by rememberSaveable { mutableStateOf(false) }
        var scratchpadsExpanded by rememberSaveable { mutableStateOf(false) }

        LazyColumn(
            modifier = Modifier
                .fillMaxWidth(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                Column {
                    Text(
                        stringResource(R.string.chat_runtime_title),
                        style = RuntimeTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Medium,
                    )
                    Text(
                        stringResource(R.string.chat_runtime_description),
                        style = RuntimeTheme.typography.bodySmall,
                        color = RuntimeTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp, bottom = 4.dp),
                    )
                }
            }

            // 1. 长期工作记忆折叠卡片 (Collapsible Long-term Memory Section)
            item {
                CollapsibleRuntimeSectionCard(
                    title = stringResource(R.string.chat_memory_section, memories.size),
                    icon = RuntimeIconName.Brain,
                    tint = RuntimeTheme.colorScheme.onSurfaceVariant,
                    expanded = memoriesExpanded,
                    onToggle = { memoriesExpanded = !memoriesExpanded },
                ) {
                    if (memories.isEmpty()) {
                        Text(
                            text = stringResource(R.string.chat_memory_empty),
                            style = RuntimeTheme.typography.bodySmall,
                            color = RuntimeTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 4.dp),
                        )
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            memories.forEach { memory ->
                                Surface(
                                    color = RuntimeTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.5f),
                                    shape = RuntimeTheme.shapes.chip,
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = memory.key,
                                                style = RuntimeTheme.typography.labelMedium.copy(fontWeight = FontWeight.Medium),
                                                color = RuntimeTheme.colorScheme.primary,
                                            )
                                            Text(
                                                text = limitDiagnosticText(memory.value),
                                                style = RuntimeTheme.typography.bodySmall,
                                                color = RuntimeTheme.colorScheme.onSurface,
                                                maxLines = DIAGNOSTIC_PREVIEW_LINES,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                        }
                                        RuntimeIconButton(
                                            onClick = { onDeleteMemory(memory.id) },
                                            modifier = Modifier.size(24.dp),
                                        ) {
                                            RuntimeIcon(RuntimeIconName.Trash, Modifier.size(14.dp), RuntimeTheme.colorScheme.error)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 2. 任务草稿折叠卡片 (Collapsible Scratchpad Section)
            item {
                CollapsibleRuntimeSectionCard(
                    title = stringResource(R.string.chat_scratchpad_section, scratchpads.size),
                    icon = RuntimeIconName.File,
                    tint = RuntimeTheme.colorScheme.onSurfaceVariant,
                    expanded = scratchpadsExpanded,
                    onToggle = { scratchpadsExpanded = !scratchpadsExpanded },
                ) {
                    if (scratchpads.isEmpty()) {
                        Text(
                            text = stringResource(R.string.chat_scratchpad_empty),
                            style = RuntimeTheme.typography.bodySmall,
                            color = RuntimeTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 4.dp),
                        )
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            scratchpads.forEach { pad ->
                                Surface(
                                    color = RuntimeTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.5f),
                                    shape = RuntimeTheme.shapes.chip,
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = pad.key.ifBlank { "草稿" },
                                                style = RuntimeTheme.typography.labelMedium.copy(fontWeight = FontWeight.Medium),
                                                color = RuntimeTheme.colorScheme.tertiary,
                                            )
                                            Text(
                                                text = limitDiagnosticText(pad.value),
                                                style = RuntimeTheme.typography.bodySmall,
                                                color = RuntimeTheme.colorScheme.onSurface,
                                                maxLines = DIAGNOSTIC_PREVIEW_LINES,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                        }
                                        RuntimeIconButton(
                                            onClick = { onDeleteScratchpad(pad.key) },
                                            modifier = Modifier.size(24.dp),
                                        ) {
                                            RuntimeIcon(RuntimeIconName.Trash, Modifier.size(14.dp), RuntimeTheme.colorScheme.error)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            if (events.isEmpty()) {
                item {
                    RuntimeCard {
                        Text(
                            stringResource(R.string.chat_no_events),
                            color = RuntimeTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            } else {
                // 汇总统计
                val roundCount = events.count { it is HarnessEvent.ProviderRoundStarted }
                val toolCount = events.count { it is HarnessEvent.ToolCallStarted }
                val approvalCount = events.count { it is HarnessEvent.ApprovalRequested }
                val recoveryCount = events.count { it is HarnessEvent.RecoveryApplied }
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        StatPill(RuntimeIconName.Brain, roundCount.toString(), RuntimeTheme.colorScheme.primary, Modifier.weight(1f))
                        StatPill(RuntimeIconName.Wrench, toolCount.toString(), RuntimeTheme.colorScheme.tertiary, Modifier.weight(1f))
                        StatPill(RuntimeIconName.Shield, approvalCount.toString(), semanticColors().warning, Modifier.weight(1f))
                        StatPill(RuntimeIconName.Refresh, recoveryCount.toString(), RuntimeTheme.colorScheme.secondary, Modifier.weight(1f))
                    }
                    HorizontalDivider(color = RuntimeTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                }

                // 最新一轮在最上，组内按发生顺序链式展示；轮间以分隔线区隔。
                items(rounds.asReversed()) { round ->
                    RoundSection(round, onNavigateToMessage)
                    HorizontalDivider(
                        color = RuntimeTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
                        modifier = Modifier.padding(vertical = 4.dp),
                    )
                }
                if (lifecycle.isNotEmpty()) {
                    item {
                        Text(
                            stringResource(R.string.chat_tl_lifecycle),
                            style = RuntimeTheme.typography.titleSmall,
                            fontWeight = FontWeight.Medium,
                            color = RuntimeTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                    items(lifecycle) { event -> RuntimeEventRow(event) }
                }
            }
        }
    }
}

@Composable
private fun CollapsibleRuntimeSectionCard(
    title: String,
    icon: RuntimeIconName,
    tint: Color,
    expanded: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Surface(
        color = RuntimeTheme.colorScheme.surfaceContainerLow,
        shape = RuntimeTheme.shapes.small,
        border = BorderStroke(0.6.dp, RuntimeTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onToggle),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    RuntimeIcon(icon, Modifier.size(16.dp), tint)
                    Text(
                        text = title,
                        style = RuntimeTheme.typography.titleSmall,
                        color = RuntimeTheme.colorScheme.onSurface,
                    )
                }
                RuntimeExpandChevron(
                    expanded = expanded,
                    modifier = Modifier.size(16.dp),
                    tint = RuntimeTheme.colorScheme.onSurfaceVariant,
                    vertical = true,
                )
            }

            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut(),
            ) {
                Box(modifier = Modifier.padding(top = 4.dp)) {
                    content()
                }
            }
        }
    }
}

/** 某一轮的完整调用链：模型响应 + 工具执行 + 审批事件。 */
internal class RoundGroup(
    val key: Long,
    val roundNumber: Int,
    var displayIndex: Int = 0,
    val modelId: String?,
    val startedAt: Long,
    var userPromptMessage: UserMessage? = null,
) {
    val entries = mutableListOf<TimelineEntry>()
}

/** 轮内条目：展开详情时从 [messages] 回查 payload。 */
internal sealed interface TimelineEntry {
    val timestamp: Long

    data class Assistant(
        override val timestamp: Long,
        val entryId: String,
        val inputTokens: Long,
        val outputTokens: Long,
        val message: AssistantText?,
    ) : TimelineEntry

    data class Tool(
        override val timestamp: Long,
        val callId: String,
        var name: String,
        var settled: HarnessEvent.ToolCallSettled?,
        var callMessage: ToolCall?,
        var result: ToolResult?,
    ) : TimelineEntry

    data class Approval(override val timestamp: Long, val toolName: String, val riskLevel: String) : TimelineEntry
}

/** 把扁平事件流切成（轮次组，会话级生命周期）两段；tool call 与 settled 结果就地配对。 */
internal fun buildRoundGroups(
    events: List<HarnessEvent>,
    messages: List<HarnessMessage>,
): Pair<List<RoundGroup>, List<HarnessEvent>> {
    val toolResultsById = messages.filterIsInstance<ToolResult>()
        .associateBy { it.toolCallId }
    val callsById = messages.filterIsInstance<ToolCall>().associateBy { it.id }
    val assistantsById = messages.filterIsInstance<AssistantText>().associateBy { it.id }
    val userMessages = messages.filterIsInstance<UserMessage>()

    val rounds = mutableListOf<RoundGroup>()
    val lifecycle = mutableListOf<HarnessEvent>()
    var current: RoundGroup? = null
    val toolIndexByCallId = mutableMapOf<String, Int>()

    fun newGroup(event: HarnessEvent.ProviderRoundStarted) {
        val userMsg = userMessages.filter { it.createdAt <= event.timestamp }.lastOrNull()
            ?: userMessages.getOrNull(event.round)
            ?: userMessages.lastOrNull()
        current = RoundGroup(
            key = event.round.toLong() * 1_000_000_000L + event.timestamp,
            roundNumber = event.round,
            displayIndex = rounds.size + 1,
            modelId = event.modelId,
            startedAt = event.timestamp,
            userPromptMessage = userMsg,
        ).also { rounds += it }
        toolIndexByCallId.clear()
    }

    fun getOrCreateGroup(): RoundGroup {
        return current ?: RoundGroup(
            key = 0L,
            roundNumber = 0,
            displayIndex = rounds.size + 1,
            modelId = null,
            startedAt = System.currentTimeMillis(),
            userPromptMessage = userMessages.lastOrNull(),
        ).also {
            rounds += it
            current = it
        }
    }

    for (event in events) {
        when (event) {
            is HarnessEvent.ProviderRoundStarted -> newGroup(event)
            is HarnessEvent.ProviderRoundSettled -> getOrCreateGroup().entries.add(
                TimelineEntry.Assistant(
                    event.timestamp,
                    event.entryId.orEmpty(),
                    event.inputTokens,
                    event.outputTokens,
                    assistantsById[event.entryId],
                ),
            )
            is HarnessEvent.ToolCallStarted -> {
                val group = getOrCreateGroup()
                val entry = TimelineEntry.Tool(
                    timestamp = event.timestamp,
                    callId = event.toolCallId,
                    name = event.toolName,
                    settled = null,
                    callMessage = callsById[event.toolCallId],
                    result = toolResultsById[event.toolCallId],
                )
                group.entries.add(entry)
                toolIndexByCallId[event.toolCallId] = group.entries.lastIndex
            }
            is HarnessEvent.ToolCallSettled -> {
                val group = getOrCreateGroup()
                val index = toolIndexByCallId[event.toolCallId]
                val entry = (index?.let { group.entries.getOrNull(it) } as? TimelineEntry.Tool)
                    ?: TimelineEntry.Tool(event.timestamp, event.toolCallId, event.toolName, null, callsById[event.toolCallId], toolResultsById[event.toolCallId])
                        .also { group.entries.add(it); toolIndexByCallId[event.toolCallId] = group.entries.lastIndex }
                entry.settled = event
            }
            is HarnessEvent.ApprovalRequested -> getOrCreateGroup().entries.add(
                TimelineEntry.Approval(event.timestamp, event.toolName, event.riskLevel),
            )
            else -> lifecycle += event
        }
    }
    rounds.forEachIndexed { idx, round ->
        round.displayIndex = idx + 1
    }
    return rounds to lifecycle
}

@Composable
private fun RoundSection(
    group: RoundGroup,
    onNavigateToMessage: ((String) -> Unit)? = null,
) {
    val assistantEntries = group.entries.filterIsInstance<TimelineEntry.Assistant>()
    val totalIn = assistantEntries.sumOf { it.inputTokens }
    val totalOut = assistantEntries.sumOf { it.outputTokens }
    Column {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
        ) {
            Text(
                stringResource(R.string.chat_tl_round, if (group.displayIndex > 0) group.displayIndex else (group.roundNumber + 1)),
                style = RuntimeTheme.typography.titleSmall,
                fontWeight = FontWeight.Medium,
            )
            group.modelId?.takeIf { it.isNotBlank() }?.let {
                Text(it, style = RuntimeTheme.typography.labelSmall, color = RuntimeTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.weight(1f))
            if (totalIn > 0 || totalOut > 0) {
                Text(
                    "↑$totalIn ↓$totalOut",
                    style = RuntimeTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                    color = RuntimeTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        group.userPromptMessage?.let { userMsg ->
            val cleanPrompt = userMsg.text
                .substringBefore("\n\n[附件：")
                .substringBefore("\n\n[Attachment:")
                .trim()
            Surface(
                color = RuntimeTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.65f),
                shape = RuntimeTheme.shapes.chip,
                border = BorderStroke(0.6.dp, RuntimeTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 6.dp)
                    .clip(RuntimeTheme.shapes.chip)
                    .clickable(enabled = onNavigateToMessage != null) {
                        onNavigateToMessage?.invoke(userMsg.id)
                    },
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    RuntimeIcon(
                        RuntimeIconName.Prompt,
                        Modifier.size(15.dp),
                        RuntimeTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = cleanPrompt.ifBlank { stringResource(R.string.chat_user_request_clipboard) },
                        style = RuntimeTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                        color = RuntimeTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    if (onNavigateToMessage != null) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            Text(
                                stringResource(R.string.chat_navigate_to_message),
                                style = RuntimeTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                                color = RuntimeTheme.colorScheme.primary,
                            )
                            RuntimeIcon(
                                RuntimeIconName.ChevronRight,
                                Modifier.size(13.dp),
                                RuntimeTheme.colorScheme.onSurface,
                            )
                        }
                    }
                }
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            group.entries.forEachIndexed { index, entry ->
                RoundEntryRow(entry, isLast = index == group.entries.lastIndex)
            }
        }
    }
}

@Composable
private fun RoundEntryRow(entry: TimelineEntry, isLast: Boolean) {
    var expanded by rememberSaveable { mutableStateOf(false) }

    val visual = when (entry) {
        is TimelineEntry.Assistant -> Triple(RuntimeIconName.Sparkles, semanticColors().agent, stringResource(R.string.chat_tl_model_response))
        is TimelineEntry.Approval -> Triple(RuntimeIconName.Shield, semanticColors().warning, entry.toolName)
        is TimelineEntry.Tool -> {
            val settled = entry.settled
            val icon = when {
                settled == null -> RuntimeIconName.Wrench
                settled.success -> RuntimeIconName.Check
                else -> RuntimeIconName.Alert
            }
            val color = when {
                settled == null -> RuntimeTheme.colorScheme.tertiary
                settled.success -> semanticColors().success
                else -> RuntimeTheme.colorScheme.error
            }
            Triple(icon, color, entry.name)
        }
    }
    val (icon, color, title) = visual
    val detail: String? = when (entry) {
        is TimelineEntry.Assistant -> stringResource(R.string.chat_token_usage, entry.inputTokens, entry.outputTokens)
        is TimelineEntry.Approval -> entry.riskLevel
        is TimelineEntry.Tool -> buildString {
            append(entry.settled?.durationMs?.let(::formatPanelDuration) ?: stringResource(R.string.chat_tl_running))
            entry.callMessage?.args?.get("command")?.let { cmd ->
                append(" · ").append(cmd.toString().replace('\n', ' ').take(80))
            }
        }
    }
    val expandable: Boolean = when (entry) {
        is TimelineEntry.Tool -> true
        is TimelineEntry.Assistant ->
            entry.message != null && (entry.message.text.isNotBlank() || !entry.message.reasoning.isNullOrBlank())
        is TimelineEntry.Approval -> false
    }

    Column(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(enabled = expandable) { expanded = !expanded },
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            // 链式节点：圆点图标 + 连接线，未轮条目首尾相接。
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.size(28.dp).background(color.copy(alpha = 0.13f), CircleShape), contentAlignment = Alignment.Center) {
                    RuntimeIcon(icon, Modifier.size(15.dp), color)
                }
                if (!isLast) {
                    Box(Modifier.width(1.dp).height(22.dp).background(RuntimeTheme.colorScheme.outlineVariant))
                }
            }
            Column(Modifier.weight(1f).padding(top = 3.dp, bottom = if (isLast) 4.dp else 8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        title,
                        style = RuntimeTheme.typography.titleSmall,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        formatEventTime(entry.timestamp),
                        style = RuntimeTheme.typography.labelSmall,
                        color = RuntimeTheme.colorScheme.onSurfaceVariant,
                        fontFamily = FontFamily.Monospace,
                        maxLines = 1,
                    )
                }
                detail?.let {
                    Text(
                        it,
                        style = RuntimeTheme.typography.labelSmall,
                        color = RuntimeTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (expandable) {
                RuntimeExpandChevron(expanded,
                    Modifier.size(14.dp).padding(top = 6.dp),
                    RuntimeTheme.colorScheme.onSurfaceVariant)
            }
        }
        AnimatedVisibility(
            visible = expanded && expandable,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut(),
        ) {
            Box(Modifier.padding(start = 38.dp, bottom = 8.dp)) {
                when (entry) {
                    is TimelineEntry.Tool -> ToolExpandContent(entry)
                    is TimelineEntry.Assistant -> entry.message?.let { AssistantExpandContent(it) }
                    is TimelineEntry.Approval -> Unit
                }
            }
        }
    }
}

@Composable
private fun ToolExpandContent(entry: TimelineEntry.Tool) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        entry.callMessage?.let { call ->
            PayloadBox(stringResource(R.string.chat_tl_args), prettyArgs(call.args))
        }
        val result = entry.result
        PayloadBox(
            stringResource(R.string.chat_tl_result),
            result?.output?.ifBlank { stringResource(R.string.chat_result_empty) }
                ?: stringResource(R.string.chat_result_pending),
        )
    }
}

@Composable
private fun AssistantExpandContent(message: AssistantText) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (message.text.isNotBlank()) {
            PayloadBox(
                stringResource(R.string.chat_tl_response),
                sanitizeModelResponseForDiagnostics(message.text),
            )
        }
        message.reasoning?.takeIf { it.isNotBlank() }?.let {
            PayloadBox(stringResource(R.string.chat_tl_reasoning), it)
        }
    }
}

/** 展开详情中的长文本容器：等宽字体 + 一键复制按钮，彻底避免内部手势拦截与底部抽屉滑动冲突。 */
@Composable
private fun PayloadBox(label: String, text: String) {
    val context = androidx.compose.ui.platform.LocalContext.current
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                label,
                style = RuntimeTheme.typography.labelSmall,
                fontWeight = FontWeight.Medium,
                color = RuntimeTheme.colorScheme.onSurfaceVariant,
            )
            RuntimeIconButton(
                onClick = {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                    clipboard?.setPrimaryClip(ClipData.newPlainText(label, text))
                    Toast.makeText(context, "已复制", Toast.LENGTH_SHORT).show()
                },
                modifier = Modifier.size(20.dp),
            ) {
                RuntimeIcon(RuntimeIconName.Copy, Modifier.size(12.dp), RuntimeTheme.colorScheme.onSurfaceVariant)
            }
        }
        Surface(color = RuntimeTheme.colorScheme.surfaceContainerLow, shape = RuntimeTheme.shapes.chip) {
            Text(
                text = limitDiagnosticText(text),
                style = RuntimeTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
            )
        }
    }
}

@Composable
private fun StatPill(icon: RuntimeIconName, count: String, color: Color, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        color = color.copy(alpha = 0.10f),
        shape = RuntimeTheme.shapes.small,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            RuntimeIcon(icon, Modifier.size(14.dp), color)
            Spacer(Modifier.width(5.dp))
            Text(
                count,
                style = RuntimeTheme.typography.labelMedium.copy(fontWeight = FontWeight.Medium),
                color = color,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun RuntimeEventRow(event: HarnessEvent) {
    val visual = eventVisual(event)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.size(30.dp).background(visual.color.copy(alpha = 0.13f), CircleShape), contentAlignment = Alignment.Center) {
                RuntimeIcon(visual.icon, Modifier.size(16.dp), visual.color)
            }
            Box(Modifier.width(1.dp).height(34.dp).background(RuntimeTheme.colorScheme.outlineVariant))
        }
        Column(Modifier.weight(1f).padding(top = 2.dp, bottom = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    visual.title,
                    style = RuntimeTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    formatEventTime(event.timestamp),
                    style = RuntimeTheme.typography.labelSmall,
                    color = RuntimeTheme.colorScheme.onSurfaceVariant,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 1,
                )
            }
            Text(
                visual.detail,
                style = RuntimeTheme.typography.bodySmall,
                color = RuntimeTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

private data class EventVisual(val icon: RuntimeIconName, val color: Color, val title: String, val detail: String)

@Composable
private fun eventVisual(event: HarnessEvent): EventVisual = when (event) {
    is HarnessEvent.OperationStarted -> EventVisual(
        RuntimeIconName.Play, RuntimeTheme.colorScheme.primary,
        stringResource(R.string.chat_event_operation_started),
        stringResource(R.string.chat_operation_lane, event.laneName),
    )
    is HarnessEvent.OperationFinished -> EventVisual(
        if (event.outcome == "completed") RuntimeIconName.Check else RuntimeIconName.Stop,
        if (event.outcome == "completed") semanticColors().success else RuntimeTheme.colorScheme.error,
        if (event.outcome == "completed") stringResource(R.string.chat_event_operation_completed) else stringResource(R.string.chat_event_operation_ended),
        event.detail ?: event.outcome,
    )
    is HarnessEvent.ProviderRoundStarted -> EventVisual(
        RuntimeIconName.Brain, semanticColors().agent,
        stringResource(R.string.chat_event_provider_round, event.round + 1),
        buildString {
            append(stringResource(R.string.chat_attempt_count, event.attempt + 1))
            event.modelId?.let { append(" · $it") }
        },
    )
    is HarnessEvent.ProviderRoundSettled -> EventVisual(
        RuntimeIconName.Sparkles, semanticColors().agent,
        stringResource(R.string.chat_event_provider_settled),
        stringResource(R.string.chat_token_usage, event.inputTokens, event.outputTokens),
    )
    is HarnessEvent.ToolCallStarted -> EventVisual(
        RuntimeIconName.Wrench, RuntimeTheme.colorScheme.tertiary,
        stringResource(R.string.chat_event_tool_started, event.toolName),
        stringResource(R.string.chat_event_tool_executing),
    )
    is HarnessEvent.ToolCallSettled -> EventVisual(
        if (event.success) RuntimeIconName.Check else RuntimeIconName.Alert,
        if (event.success) semanticColors().success else RuntimeTheme.colorScheme.error,
        if (event.success) stringResource(R.string.chat_event_tool_completed, event.toolName)
        else stringResource(R.string.chat_event_tool_failed, event.toolName),
        event.durationMs?.let { stringResource(R.string.chat_event_duration, formatPanelDuration(it)) }
            ?: stringResource(R.string.chat_event_settled),
    )
    is HarnessEvent.ApprovalRequested -> EventVisual(
        RuntimeIconName.Shield, semanticColors().warning,
        stringResource(R.string.chat_event_approval),
        "${event.toolName} · ${event.riskLevel}",
    )
    is HarnessEvent.RecoveryApplied -> EventVisual(
        RuntimeIconName.Refresh, RuntimeTheme.colorScheme.secondary,
        stringResource(R.string.chat_event_recovery),
        event.detail ?: event.outcome,
    )
    is HarnessEvent.PermissionRequired -> EventVisual(
        RuntimeIconName.Shield, semanticColors().warning,
        stringResource(R.string.chat_event_permission_required, event.permission),
        event.reason,
    )
    is HarnessEvent.PlanStepProgress -> EventVisual(
        when (event.status) {
            "COMPLETED" -> RuntimeIconName.Check
            "FAILED" -> RuntimeIconName.Alert
            "RUNNING" -> RuntimeIconName.Play
            else -> RuntimeIconName.Sparkles
        },
        when (event.status) {
            "COMPLETED" -> semanticColors().success
            "FAILED" -> RuntimeTheme.colorScheme.error
            "RUNNING" -> RuntimeTheme.colorScheme.primary
            else -> semanticColors().agent
        },
        "工序 ${event.stepId} · ${event.title}",
        event.resultSummary ?: "状态: ${event.status}",
    )
}

@Composable
internal fun CreateBranchDialog(messageId: String, onDismiss: () -> Unit, onCreate: (String, String) -> Unit) {
    var name by remember { mutableStateOf("") }
    val defaultName = stringResource(R.string.chat_branch_default_name)
    RuntimeAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.chat_create_branch)) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(stringResource(R.string.chat_create_branch_description), style = RuntimeTheme.typography.bodySmall, color = RuntimeTheme.colorScheme.onSurfaceVariant)
                RuntimeFilledTextField(
                    value = name,
                    onValueChange = { name = it.take(48) },
                    labelText = stringResource(R.string.chat_branch_name),
                    placeholder = { Text(stringResource(R.string.chat_branch_name_placeholder)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = { TextButton(onClick = { onCreate(messageId, name.ifBlank { defaultName }) }) { Text(stringResource(R.string.chat_create_and_switch)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.chat_cancel)) } },
    )
}

private fun formatEventTime(timestamp: Long): String = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(timestamp))
private fun formatPanelDuration(ms: Long): String = if (ms < 1_000) "${ms}ms" else String.format(Locale.getDefault(), "%.1fs", ms / 1_000.0)

private val PAYLOAD_JSON = Json { prettyPrint = true; ignoreUnknownKeys = true }

private fun prettyArgs(args: JsonObject): String =
    runCatching { PAYLOAD_JSON.encodeToString(JsonObject.serializer(), args) }.getOrDefault(args.toString())

internal fun limitDiagnosticText(text: String, maxChars: Int = MAX_DIAGNOSTIC_TEXT_CHARS): String {
    if (text.length <= maxChars) return text
    return text.take(maxChars) + "\n…（内容过长，已截断）"
}

/**
 * Runtime details are diagnostics, not a media transport. Keep the assistant message intact for
 * chat rendering, but never copy a generated image's multi-megabyte Base64 payload into the
 * details panel or clipboard.
 */
internal fun sanitizeModelResponseForDiagnostics(
    text: String,
    maxChars: Int = MAX_DIAGNOSTIC_TEXT_CHARS,
): String {
    val out = StringBuilder(minOf(text.length, maxChars))
    var cursor = 0
    while (cursor < text.length && out.length < maxChars) {
        val dataStart = text.indexOf("data:image/", cursor, ignoreCase = true)
        val jsonKeyStart = text.indexOf("\"b64_json\"", cursor, ignoreCase = true)
        val nextStart = listOf(dataStart, jsonKeyStart).filter { it >= 0 }.minOrNull()
        if (nextStart == null) {
            out.append(text, cursor, minOf(text.length, cursor + (maxChars - out.length)))
            break
        }

        if (nextStart == dataStart) {
            val markdownStart = text.lastIndexOf("![", dataStart).takeIf { start ->
                start >= cursor && text.indexOf("](", start).let { it in start until dataStart }
            }
            val htmlStart = text.lastIndexOf("<img", dataStart, ignoreCase = true).takeIf { it >= cursor }
            val mediaStart = listOfNotNull(markdownStart, htmlStart).maxOrNull() ?: dataStart
            out.append(text, cursor, minOf(mediaStart, cursor + (maxChars - out.length)))
            val mime = text.substring(dataStart + 5, text.indexOf(';', dataStart).takeIf { it > dataStart } ?: dataStart)
                .take(40)
                .ifBlank { "image" }
            if (out.isNotEmpty() && !out.last().isWhitespace()) out.append(' ')
            out.append("[图片数据已隐藏：").append(mime).append(']')
            val end = text.indexOfAny(charArrayOf(')', '"', '\'', '>', ' ', '\n', '\r', '\t'), dataStart)
            cursor = if (end >= 0) end + 1 else text.length
        } else {
            out.append(text, cursor, minOf(jsonKeyStart, cursor + (maxChars - out.length)))
            out.append("\"b64_json\":\"[图片 Base64 已隐藏]\"")
            val colon = text.indexOf(':', jsonKeyStart + 10)
            val valueStart = if (colon >= 0) text.indexOf('"', colon + 1) else -1
            val valueEnd = if (valueStart >= 0) text.indexOf('"', valueStart + 1) else -1
            cursor = if (valueEnd >= 0) valueEnd + 1 else text.length
        }
    }
    return limitDiagnosticText(out.toString(), maxChars)
}

private const val MAX_DIAGNOSTIC_TEXT_CHARS = 32_000
private const val DIAGNOSTIC_PREVIEW_LINES = 12
