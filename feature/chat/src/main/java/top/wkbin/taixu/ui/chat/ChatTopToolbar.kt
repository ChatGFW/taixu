package top.wkbin.taixu.ui.chat

import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import top.wkbin.taixu.core.model.ChatTopToolbarAction
import top.wkbin.taixu.core.model.RunMode
import top.wkbin.taixu.feature.chat.R
import top.wkbin.taixu.ui.components.MainDestination
import top.wkbin.taixu.ui.components.RuntimeIcon
import top.wkbin.taixu.ui.components.RuntimeIconButton
import top.wkbin.taixu.ui.components.RuntimeIconName
import top.wkbin.taixu.ui.miuix.RuntimeTheme

/** 顶部栏可渲染的单个置顶动作（可用性已在构造时过滤）。 */
internal data class ChatTopToolbarEntry(
    val action: ChatTopToolbarAction,
    val icon: RuntimeIconName,
    val contentDescription: String,
    val onClick: () -> Unit,
    val highlighted: Boolean = false,
)

/** 将偏好中的置顶 id 解析为当前会话可点击的顶部按钮。 */
@Composable
internal fun rememberChatTopToolbarEntries(
    pinned: List<ChatTopToolbarAction>,
    runMode: RunMode,
    onOpenBots: () -> Unit,
    onOpenWorkshop: () -> Unit,
    onOpenRunModes: () -> Unit,
    onOpenBranches: () -> Unit,
    onOpenRuntime: () -> Unit,
    onOpenToolchain: () -> Unit,
    toolchainHighlight: Boolean,
    onOpenBrowser: (() -> Unit)?,
    browserHighlight: Boolean,
    onOpenRepository: (() -> Unit)?,
    repositoryHighlight: Boolean,
    onOpenReasoning: (() -> Unit)?,
    onOpenMemory: () -> Unit,
    onOpenOutline: () -> Unit,
    onExportMarkdown: (() -> Unit)?,
    onFloatingChat: (() -> Unit)?,
    speech: ChatSpeechToggle?,
): List<ChatTopToolbarEntry> {
    val botsLabel = stringResource(MainDestination.Bots.labelRes)
    val workshopLabel = stringResource(MainDestination.Workspace.labelRes)
    val memoryLabel = stringResource(R.string.chat_memory_sheet_title)
    val outlineLabel = stringResource(R.string.chat_outline_title)
    val exportLabel = stringResource(R.string.chat_export_markdown)
    val browserLabel = stringResource(R.string.chat_browser_entry)
    val repositoryLabel = stringResource(R.string.chat_repository)
    val speechEnable = stringResource(R.string.chat_speech_enable)
    val speechDisable = stringResource(R.string.chat_speech_disable)
    val runModeLabel = stringResource(R.string.chat_menu_run_mode)
    val branchesLabel = stringResource(R.string.chat_branches_title)
    val runtimeLabel = stringResource(R.string.chat_runtime_overview)
    val toolchainLabel = stringResource(R.string.chat_toolchain_entry)
    val reasoningLabel = stringResource(R.string.chat_reasoning_effort)
    val floatingLabel = stringResource(R.string.chat_workbench_floating_window)
    return pinned.mapNotNull { action ->
        when (action) {
            ChatTopToolbarAction.BOTS -> ChatTopToolbarEntry(action, MainDestination.Bots.icon, botsLabel, onOpenBots)
            ChatTopToolbarAction.WORKSPACE ->
                ChatTopToolbarEntry(action, MainDestination.Workspace.icon, workshopLabel, onOpenWorkshop)
            ChatTopToolbarAction.MEMORY ->
                ChatTopToolbarEntry(action, RuntimeIconName.Brain, memoryLabel, onOpenMemory)
            ChatTopToolbarAction.OUTLINE ->
                ChatTopToolbarEntry(action, RuntimeIconName.List, outlineLabel, onOpenOutline)
            ChatTopToolbarAction.EXPORT_MARKDOWN -> onExportMarkdown?.let {
                ChatTopToolbarEntry(action, RuntimeIconName.Save, exportLabel, it)
            }
            ChatTopToolbarAction.BROWSER -> onOpenBrowser?.let {
                ChatTopToolbarEntry(action, RuntimeIconName.Globe, browserLabel, it, browserHighlight)
            }
            ChatTopToolbarAction.REPOSITORY -> onOpenRepository?.let {
                ChatTopToolbarEntry(action, RuntimeIconName.GitBranch, repositoryLabel, it, repositoryHighlight)
            }
            ChatTopToolbarAction.SPEECH -> speech?.let { toggle ->
                ChatTopToolbarEntry(
                    action,
                    if (toggle.enabled) RuntimeIconName.VolumeOn else RuntimeIconName.VolumeOff,
                    if (toggle.enabled) speechDisable else speechEnable,
                    toggle.onToggle,
                    highlighted = toggle.enabled,
                )
            }
            ChatTopToolbarAction.RUN_MODE ->
                ChatTopToolbarEntry(action, runModeIcon(runMode), runModeLabel, onOpenRunModes)
            ChatTopToolbarAction.BRANCHES ->
                ChatTopToolbarEntry(action, RuntimeIconName.GitBranch, branchesLabel, onOpenBranches)
            ChatTopToolbarAction.RUNTIME ->
                ChatTopToolbarEntry(action, RuntimeIconName.Logs, runtimeLabel, onOpenRuntime)
            ChatTopToolbarAction.TOOLCHAIN ->
                ChatTopToolbarEntry(
                    action, RuntimeIconName.Wrench, toolchainLabel, onOpenToolchain,
                    highlighted = toolchainHighlight,
                )
            ChatTopToolbarAction.REASONING -> onOpenReasoning?.let {
                ChatTopToolbarEntry(action, RuntimeIconName.Sparkles, reasoningLabel, it)
            }
            ChatTopToolbarAction.FLOATING_CHAT -> onFloatingChat?.let {
                ChatTopToolbarEntry(action, RuntimeIconName.OpenInNew, floatingLabel, it)
            }
        }
    }
}

@Composable
internal fun ChatTopToolbarButton(entry: ChatTopToolbarEntry) {
    val tint = if (entry.highlighted) {
        RuntimeTheme.colorScheme.tertiary
    } else {
        RuntimeTheme.colorScheme.onSurface
    }
    RuntimeIconButton(
        onClick = entry.onClick,
        modifier = Modifier.size(40.dp),
        contentDescription = entry.contentDescription,
    ) {
        RuntimeIcon(entry.icon, Modifier.size(22.dp), tint)
    }
}
