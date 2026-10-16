package top.wkbin.taixu.ui.chat

import top.wkbin.taixu.ui.theme.chatBackdropChrome

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import top.wkbin.taixu.core.database.AiModelEntity
import top.wkbin.taixu.core.model.RunMode
import top.wkbin.taixu.feature.chat.R
import top.wkbin.taixu.harness.events.HarnessEvent
import top.wkbin.taixu.harness.session.ConversationBranch
import top.wkbin.taixu.ui.components.*

/** Conversation and model lead; advanced controls unfold on demand. */
@Composable
@OptIn(ExperimentalMaterial3Api::class)
internal fun ChatTopBar(
    title: String, workspace: String, distroDisplayName: String, activeModel: AiModelEntity?,
    runMode: RunMode, currentBranch: ConversationBranch?,
    runtimeEvents: List<HarnessEvent>, running: Boolean,
    onOpenSessions: () -> Unit, onOpenModels: () -> Unit,
    onOpenRunModes: () -> Unit, onOpenBranches: () -> Unit,
    onOpenRuntime: () -> Unit, onOpenBrowser: (() -> Unit)? = null, browserHighlight: Boolean = false,
    onOpenRepository: (() -> Unit)? = null, repositoryHighlight: Boolean = false,
    toolchainHighlight: Boolean = false, onOpenToolchain: () -> Unit,
    onOpenReasoning: (() -> Unit)? = null, companion: ChatCompanionToggle? = null,
    speech: ChatSpeechToggle? = null,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val model = activeModel?.let { it.model.substringBefore(',').trim().ifBlank { it.name } }
    Column(
        Modifier
            .fillMaxWidth()
            .background(chatBackdropChrome())
            .statusBarsPadding(),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            if (!LocalRuntimeWidthClass.current.hasPersistentNavigation) RuntimeIconButton(
                onClick = onOpenSessions,
                modifier = Modifier.size(44.dp),
                contentDescription = stringResource(R.string.chat_open_session_list),
            ) {
                RuntimeIcon(RuntimeIconName.List, Modifier.size(22.dp))
            } else Spacer(Modifier.width(8.dp))
            Column(
                Modifier.weight(1f).clickable(onClick = onOpenModels).heightIn(min = 48.dp),
                verticalArrangement = Arrangement.Center,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = title.ifBlank { stringResource(R.string.chat_title) },
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Medium),
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (runMode == RunMode.PLAN) {
                        Text(stringResource(R.string.chat_run_mode_plan),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.tertiary, maxLines = 1)
                    }
                }
                if (model != null) {
                    Text(model,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            if (companion != null) RuntimeIconButton(
                onClick = companion.onToggle, modifier = Modifier.size(44.dp),
                contentDescription = stringResource(if (companion.open) top.wkbin.taixu.feature.components.R.string.components_companion_collapse
                    else top.wkbin.taixu.feature.components.R.string.components_companion_open),
            ) {
                RuntimeIcon(RuntimeIconName.Terminal, Modifier.size(20.dp),
                    if (companion.open || browserHighlight) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (speech != null) RuntimeIconButton(
                onClick = speech.onToggle, modifier = Modifier.size(48.dp),
                contentDescription = stringResource(if (speech.enabled) R.string.chat_speech_disable else R.string.chat_speech_enable),
            ) {
                RuntimeIcon(if (speech.enabled) RuntimeIconName.VolumeOn else RuntimeIconName.VolumeOff,
                    Modifier.size(22.dp), if (speech.enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Box {
                RuntimeIconButton(
                    onClick = { expanded = true },
                    modifier = Modifier.size(44.dp),
                    contentDescription = stringResource(R.string.chat_workbench_controls),
                ) {
                    RuntimeIcon(
                        RuntimeIconName.Wrench,
                        Modifier.size(20.dp),
                        if (toolchainHighlight || repositoryHighlight || browserHighlight) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (toolchainHighlight || repositoryHighlight || browserHighlight) {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .align(Alignment.TopEnd)
                            .offset(x = (-6).dp, y = 6.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary),
                    )
                }
            }
        }
    }
    if (expanded) {
        val closeThen: (() -> Unit) -> () -> Unit = { action -> { expanded = false; action() } }
        PlainBackdropModalBottomSheet(onDismissRequest = { expanded = false }) {
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp).padding(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.chat_workbench_controls),
                    style = MaterialTheme.typography.titleLarge)
                Text(stringResource(R.string.chat_workbench_workspace),
                    style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                ChatWorkspaceBar(workspace, distroDisplayName,
                    onOpenWorkspace = closeThen(onOpenSessions),
                    onOpenBrowser = onOpenBrowser?.let(closeThen), browserHighlight = browserHighlight,
                    onOpenRepository = onOpenRepository?.let(closeThen), repositoryHighlight = repositoryHighlight)
                Text(stringResource(R.string.chat_workbench_session), Modifier.padding(top = 8.dp),
                    style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                ChatWorkbenchControls(runMode, currentBranch, runtimeEvents, running,
                    closeThen(onOpenRunModes), closeThen(onOpenBranches),
                    closeThen(onOpenRuntime), closeThen(onOpenToolchain), toolchainHighlight,
                    onReasoning = onOpenReasoning?.let(closeThen))
            }
        }
    }
}
