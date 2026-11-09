package top.wkbin.taixu.ui.chat

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import top.wkbin.taixu.ui.miuix.RuntimeTheme
import top.yukonga.miuix.kmp.basic.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import top.wkbin.taixu.core.model.RunMode
import top.wkbin.taixu.feature.chat.R
import top.wkbin.taixu.harness.events.HarnessEvent
import top.wkbin.taixu.harness.session.ConversationBranch
import top.wkbin.taixu.ui.components.*
import top.wkbin.taixu.ui.chat.floating.rememberFloatingChatLauncher

/** Floating navigation and actions; advanced controls unfold on demand. */
@Composable
internal fun ChatTopBar(
    runMode: RunMode, currentBranch: ConversationBranch?,
    runtimeEvents: List<HarnessEvent>, running: Boolean,
    onOpenSessions: () -> Unit, onOpenBots: () -> Unit, onOpenWorkshop: () -> Unit,
    onOpenRunModes: () -> Unit, onOpenBranches: () -> Unit,
    onOpenRuntime: () -> Unit, onOpenBrowser: (() -> Unit)? = null, browserHighlight: Boolean = false,
    onOpenRepository: (() -> Unit)? = null, repositoryHighlight: Boolean = false,
    toolchainHighlight: Boolean = false, onOpenToolchain: () -> Unit,
    onOpenReasoning: (() -> Unit)? = null, companion: ChatCompanionToggle? = null,
    speech: ChatSpeechToggle? = null,
    onOpenMemory: () -> Unit = {},
    onOpenOutline: () -> Unit = {},
    onExportMarkdown: (() -> Unit)? = null,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val openFloatingChat = rememberFloatingChatLauncher()
    Row(
        Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        if (!LocalRuntimeWidthClass.current.hasPersistentNavigation) {
            RuntimeCard(shape = CircleShape, containerColor = RuntimeTheme.colorScheme.surfaceContainer,
                contentPadding = PaddingValues(4.dp)) {
                RuntimeIconButton(onClick = onOpenSessions, modifier = Modifier.size(40.dp),
                    contentDescription = stringResource(R.string.chat_open_session_list)) {
                    RuntimeIcon(RuntimeIconName.Menu, Modifier.size(22.dp))
                }
            }
        } else Spacer(Modifier.width(48.dp))
        RuntimeCard(shape = CircleShape, containerColor = RuntimeTheme.colorScheme.surfaceContainer,
            contentPadding = PaddingValues(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                RuntimeIconButton(onClick = onOpenBots, modifier = Modifier.size(40.dp),
                    contentDescription = stringResource(MainDestination.Bots.labelRes)) {
                    RuntimeIcon(MainDestination.Bots.icon, Modifier.size(22.dp))
                }
                RuntimeIconButton(onClick = onOpenWorkshop, modifier = Modifier.size(40.dp),
                    contentDescription = stringResource(MainDestination.Workspace.labelRes)) {
                    RuntimeIcon(MainDestination.Workspace.icon, Modifier.size(22.dp))
                }
                RuntimeIconButton(onClick = { expanded = true }, modifier = Modifier.size(40.dp),
                    contentDescription = stringResource(R.string.chat_workbench_controls)) {
                    RuntimeIcon(RuntimeIconName.More, Modifier.size(22.dp),
                        if (toolchainHighlight || repositoryHighlight || browserHighlight) RuntimeTheme.colorScheme.primary
                        else RuntimeTheme.colorScheme.onSurface)
                }
            }
        }
    }
    if (expanded) {
        val closeThen: (() -> Unit) -> () -> Unit = { action -> { expanded = false; action() } }
        PlainBackdropModalBottomSheet(onDismissRequest = { expanded = false }) {
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp).padding(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.chat_workbench_controls), style = RuntimeTheme.typography.titleLarge)
                Row {
                    companion?.let { toggle -> RuntimeIconButton(onClick = closeThen(toggle.onToggle),
                        contentDescription = stringResource(if (toggle.open) top.wkbin.taixu.feature.components.R.string.components_companion_collapse
                            else top.wkbin.taixu.feature.components.R.string.components_companion_open)) {
                        RuntimeIcon(RuntimeIconName.PanelRightOpen, Modifier.size(24.dp))
                    } }
                }
                ChatWorkspaceBar(
                    onOpenBrowser = onOpenBrowser?.let(closeThen), browserHighlight = browserHighlight,
                    onOpenRepository = onOpenRepository?.let(closeThen), repositoryHighlight = repositoryHighlight)
                Column {
                    RuntimeSectionTitle(stringResource(R.string.chat_workbench_session))
                    RuntimeCard(containerColor = RuntimeTheme.colorScheme.surfaceContainerHigh, contentPadding = PaddingValues(0.dp)) {
                        WorkbenchRow(RuntimeIconName.Brain, stringResource(R.string.chat_memory_sheet_title), closeThen(onOpenMemory))
                        WorkbenchRow(RuntimeIconName.List, stringResource(R.string.chat_outline_title), closeThen(onOpenOutline))
                        onExportMarkdown?.let { handler ->
                            WorkbenchRow(RuntimeIconName.Save, stringResource(R.string.chat_export_markdown), closeThen(handler))
                        }
                    }
                }
                ChatWorkbenchControls(runMode, currentBranch, runtimeEvents, running,
                    closeThen(onOpenRunModes), closeThen(onOpenBranches),
                    closeThen(onOpenRuntime), closeThen(onOpenToolchain), toolchainHighlight,
                    onReasoning = onOpenReasoning?.let(closeThen), onFloatingChat = openFloatingChat?.let(closeThen),
                    speech = speech?.copy(onToggle = closeThen(speech.onToggle)))
            }
        }
    }
}

@Composable
private fun WorkbenchRow(icon: RuntimeIconName, title: String, onClick: () -> Unit) {
    top.yukonga.miuix.kmp.preference.ArrowPreference(
        title = title,
        startAction = { RuntimeIcon(icon, Modifier.padding(end = 16.dp).size(24.dp), RuntimeTheme.colorScheme.onSurfaceVariant) },
        onClick = onClick,
    )
}
