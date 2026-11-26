package top.wkbin.taixu.ui.chat

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import top.wkbin.taixu.core.model.ChatTopToolbarAction
import top.wkbin.taixu.core.model.RunMode
import top.wkbin.taixu.feature.chat.R
import top.wkbin.taixu.feature.components.R as ComponentsR
import top.wkbin.taixu.harness.events.HarnessEvent
import top.wkbin.taixu.harness.session.ConversationBranch
import top.wkbin.taixu.ui.components.*
import top.wkbin.taixu.ui.miuix.RuntimeTheme
import top.yukonga.miuix.kmp.basic.Text

/**
 * 顶栏「更多」抽屉正文。操作仍是原来的入口，只把双列大卡片收成横排小块，
 * 让手机高度里少滚一段才能点到底部动作。
 */
@Composable
internal fun ChatWorkbenchSheetBody(
    pinned: Set<ChatTopToolbarAction>,
    companion: ChatCompanionToggle?,
    speech: ChatSpeechToggle?,
    browserHighlight: Boolean,
    repositoryHighlight: Boolean,
    toolchainHighlight: Boolean,
    runMode: RunMode,
    branch: ConversationBranch?,
    events: List<HarnessEvent>,
    running: Boolean,
    onOpenBrowser: (() -> Unit)?,
    onOpenRepository: (() -> Unit)?,
    onOpenBots: () -> Unit,
    onOpenWorkshop: () -> Unit,
    onOpenMemory: () -> Unit,
    onOpenOutline: () -> Unit,
    onExportMarkdown: (() -> Unit)?,
    onOpenRunModes: () -> Unit,
    onOpenBranches: () -> Unit,
    onOpenRuntime: () -> Unit,
    onOpenToolchain: () -> Unit,
    onOpenReasoning: (() -> Unit)?,
    onFloatingChat: (() -> Unit)?,
    closeThen: (() -> Unit) -> () -> Unit,
) {
    Column(
        Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
            .padding(horizontal = 12.dp).padding(bottom = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.chat_workbench_controls),
                modifier = Modifier.weight(1f),
                style = RuntimeTheme.typography.titleLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            companion?.let { toggle ->
                RuntimeIconButton(
                    onClick = closeThen(toggle.onToggle),
                    modifier = Modifier.size(40.dp),
                    contentDescription = stringResource(
                        if (toggle.open) ComponentsR.string.components_companion_collapse
                        else ComponentsR.string.components_companion_open,
                    ),
                ) {
                    RuntimeIcon(RuntimeIconName.PanelRightOpen, Modifier.size(22.dp))
                }
            }
        }
        ChatWorkspaceBar(
            onOpenBrowser = onOpenBrowser.takeUnless { ChatTopToolbarAction.BROWSER in pinned }?.let(closeThen),
            browserHighlight = browserHighlight,
            onOpenRepository = onOpenRepository.takeUnless { ChatTopToolbarAction.REPOSITORY in pinned }?.let(closeThen),
            repositoryHighlight = repositoryHighlight,
        )
        SessionActions(
            pinned = pinned,
            onOpenBots = onOpenBots,
            onOpenWorkshop = onOpenWorkshop,
            onOpenMemory = onOpenMemory,
            onOpenOutline = onOpenOutline,
            onExportMarkdown = onExportMarkdown,
            closeThen = closeThen,
        )
        ChatWorkbenchControls(
            runMode, branch, events, running,
            closeThen(onOpenRunModes), closeThen(onOpenBranches),
            closeThen(onOpenRuntime), closeThen(onOpenToolchain), toolchainHighlight,
            onReasoning = onOpenReasoning.takeUnless { ChatTopToolbarAction.REASONING in pinned }?.let(closeThen),
            onFloatingChat = onFloatingChat.takeUnless { ChatTopToolbarAction.FLOATING_CHAT in pinned }?.let(closeThen),
            speech = speech.takeUnless { ChatTopToolbarAction.SPEECH in pinned }
                ?.let { toggle -> toggle.copy(onToggle = closeThen(toggle.onToggle)) },
            hideRunMode = ChatTopToolbarAction.RUN_MODE in pinned,
            hideBranches = ChatTopToolbarAction.BRANCHES in pinned,
            hideRuntime = ChatTopToolbarAction.RUNTIME in pinned,
            hideToolchain = ChatTopToolbarAction.TOOLCHAIN in pinned,
        )
    }
}

@Composable
private fun SessionActions(
    pinned: Set<ChatTopToolbarAction>,
    onOpenBots: () -> Unit,
    onOpenWorkshop: () -> Unit,
    onOpenMemory: () -> Unit,
    onOpenOutline: () -> Unit,
    onExportMarkdown: (() -> Unit)?,
    closeThen: (() -> Unit) -> () -> Unit,
) {
    val rows = buildList {
        if (ChatTopToolbarAction.BOTS !in pinned) {
            add(WorkbenchAction(MainDestination.Bots.icon, stringResource(MainDestination.Bots.labelRes), onClick = closeThen(onOpenBots)))
        }
        if (ChatTopToolbarAction.WORKSPACE !in pinned) {
            add(WorkbenchAction(MainDestination.Workspace.icon, stringResource(MainDestination.Workspace.labelRes), onClick = closeThen(onOpenWorkshop)))
        }
        if (ChatTopToolbarAction.MEMORY !in pinned) {
            add(WorkbenchAction(RuntimeIconName.Brain, stringResource(R.string.chat_memory_sheet_title), onClick = closeThen(onOpenMemory)))
        }
        if (ChatTopToolbarAction.OUTLINE !in pinned) {
            add(WorkbenchAction(RuntimeIconName.List, stringResource(R.string.chat_outline_title), onClick = closeThen(onOpenOutline)))
        }
        if (ChatTopToolbarAction.EXPORT_MARKDOWN !in pinned) {
            onExportMarkdown?.let { handler ->
                add(WorkbenchAction(RuntimeIconName.Save, stringResource(R.string.chat_export_markdown), onClick = closeThen(handler)))
            }
        }
    }
    if (rows.isEmpty()) return
    Column {
        RuntimeSectionTitle(stringResource(R.string.chat_workbench_session))
        RuntimeCard(containerColor = RuntimeTheme.colorScheme.surfaceContainerHigh, contentPadding = PaddingValues(0.dp)) {
            rows.forEach { row -> WorkbenchRow(row.icon, row.label, row.onClick) }
        }
    }
}

@Composable
internal fun ChatWorkspaceBar(
    onOpenBrowser: (() -> Unit)?, browserHighlight: Boolean,
    onOpenRepository: (() -> Unit)?, repositoryHighlight: Boolean,
) {
    val entries = buildList {
        if (onOpenBrowser != null) add(WorkbenchAction(RuntimeIconName.Globe,
            stringResource(R.string.chat_browser_entry), onClick = onOpenBrowser, highlighted = browserHighlight))
        if (onOpenRepository != null) add(WorkbenchAction(RuntimeIconName.GitBranch,
            stringResource(R.string.chat_repository), onClick = onOpenRepository, highlighted = repositoryHighlight))
    }
    WorkbenchActionGrid(entries)
}

@Composable
internal fun ChatWorkbenchControls(
    runMode: RunMode, branch: ConversationBranch?, events: List<HarnessEvent>, running: Boolean,
    onRunMode: () -> Unit, onBranches: () -> Unit, onRuntime: () -> Unit, onTools: () -> Unit,
    toolchainHighlight: Boolean, onReasoning: (() -> Unit)? = null, onFloatingChat: (() -> Unit)? = null,
    speech: ChatSpeechToggle? = null,
    hideRunMode: Boolean = false,
    hideBranches: Boolean = false,
    hideRuntime: Boolean = false,
    hideToolchain: Boolean = false,
) {
    val rounds = events.count { it is HarnessEvent.ProviderRoundStarted }
    val actions = buildList {
        speech?.let { toggle -> add(WorkbenchAction(
            if (toggle.enabled) RuntimeIconName.VolumeOn else RuntimeIconName.VolumeOff,
            stringResource(if (toggle.enabled) R.string.chat_speech_disable else R.string.chat_speech_enable),
            onClick = toggle.onToggle, highlighted = toggle.enabled)) }
        if (!hideRunMode) {
            add(WorkbenchAction(runModeIcon(runMode), stringResource(R.string.chat_menu_run_mode),
                stringResource(if (runMode == RunMode.PLAN) R.string.chat_run_mode_plan else R.string.chat_run_mode_build), onRunMode))
        }
        if (!hideBranches) {
            add(WorkbenchAction(RuntimeIconName.GitBranch, stringResource(R.string.chat_branches_title),
                branch?.name ?: stringResource(R.string.chat_main_line), onBranches))
        }
        if (!hideRuntime) {
            add(WorkbenchAction(RuntimeIconName.Logs, stringResource(R.string.chat_runtime_overview),
                if (running) stringResource(R.string.chat_round_number, rounds)
                else stringResource(R.string.chat_menu_runtime_subtitle, events.size), onRuntime, highlighted = running))
        }
        if (!hideToolchain) {
            add(WorkbenchAction(RuntimeIconName.Wrench, stringResource(R.string.chat_toolchain_entry),
                if (toolchainHighlight) stringResource(R.string.chat_workbench_tools_attention) else null,
                onTools, highlighted = toolchainHighlight))
        }
        if (onReasoning != null) add(WorkbenchAction(RuntimeIconName.Sparkles,
            stringResource(R.string.chat_reasoning_effort), onClick = onReasoning))
        if (onFloatingChat != null) add(WorkbenchAction(RuntimeIconName.OpenInNew,
            stringResource(R.string.chat_workbench_floating_window), onClick = onFloatingChat))
    }
    WorkbenchActionGrid(actions)
}

private data class WorkbenchAction(
    val icon: RuntimeIconName,
    val label: String,
    val detail: String? = null,
    val onClick: () -> Unit,
    val highlighted: Boolean = false,
)

/** 字号很大时改单列，避免半宽格子把标签挤没。 */
@Composable
private fun WorkbenchActionGrid(actions: List<WorkbenchAction>) {
    if (actions.isEmpty()) return
    val columns = if (LocalDensity.current.fontScale > 1.3f) 1 else 2
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        actions.chunked(columns).forEach { row ->
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                row.forEach { action ->
                    WorkbenchActionTile(action, Modifier.weight(1f).fillMaxHeight())
                }
                repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

/** 图标与标题横排，副标题跟在标题下；不再用纵向大卡片（内边距 16 + 最低 64）。 */
@Composable
private fun WorkbenchActionTile(action: WorkbenchAction, modifier: Modifier = Modifier) {
    val colors = RuntimeTheme.colorScheme
    val accent = action.highlighted
    val contentColor = if (accent) colors.onTertiaryContainer else colors.onSurface
    RuntimeCard(
        modifier = modifier,
        containerColor = if (accent) colors.tertiaryContainer else colors.surfaceContainerLow,
        onClick = action.onClick,
        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
    ) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 36.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            RuntimeIcon(action.icon, Modifier.size(20.dp),
                if (accent) colors.onTertiaryContainer else colors.onSurface.copy(alpha = 0.8f))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                Text(action.label, style = RuntimeTheme.typography.labelLarge, color = contentColor,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                action.detail?.let {
                    Text(it, style = RuntimeTheme.typography.bodySmall,
                        color = if (accent) colors.onTertiaryContainer else colors.onSurfaceVariant,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

@Composable
private fun WorkbenchRow(icon: RuntimeIconName, title: String, onClick: () -> Unit) {
    RuntimeArrowPreference(
        title = title,
        startAction = { RuntimeIcon(icon, Modifier.padding(end = 16.dp).size(24.dp), RuntimeTheme.colorScheme.onSurfaceVariant) },
        onClick = onClick,
    )
}
