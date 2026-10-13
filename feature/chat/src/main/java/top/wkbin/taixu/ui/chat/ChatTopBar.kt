package top.wkbin.taixu.ui.chat

import android.app.Activity
import android.provider.Settings
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import top.wkbin.taixu.core.database.AiModelEntity
import top.wkbin.taixu.core.model.ApprovalMode
import top.wkbin.taixu.core.model.RunMode
import top.wkbin.taixu.feature.chat.R
import top.wkbin.taixu.harness.events.HarnessEvent
import top.wkbin.taixu.harness.session.ConversationBranch
import top.wkbin.taixu.ui.chat.floating.FloatingChatService
import top.wkbin.taixu.ui.components.*

/** Conversation and model lead; advanced controls unfold on demand. */
@Composable
internal fun ChatTopBar(
    title: String, workspace: String, distroDisplayName: String, activeModel: AiModelEntity?,
    approvalMode: ApprovalMode, runMode: RunMode, currentBranch: ConversationBranch?,
    runtimeEvents: List<HarnessEvent>, running: Boolean, onShowFloatingPermissionDialog: () -> Unit,
    onOpenSessions: () -> Unit, onNewSession: () -> Unit, onOpenModels: () -> Unit,
    onOpenApprovalModes: () -> Unit, onOpenRunModes: () -> Unit, onOpenBranches: () -> Unit,
    onOpenRuntime: () -> Unit, onOpenBrowser: (() -> Unit)? = null, browserHighlight: Boolean = false,
    onOpenRepository: (() -> Unit)? = null, repositoryHighlight: Boolean = false,
    toolchainHighlight: Boolean = false, onOpenToolchain: () -> Unit,
) {
    val context = LocalContext.current
    var expanded by rememberSaveable { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }
    val model = activeModel?.let { it.model.substringBefore(',').trim().ifBlank { it.name } }
        ?: stringResource(R.string.chat_no_model_selected)
    Column(Modifier.fillMaxWidth().statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            RuntimeIconButton(onClick = onOpenSessions, contentDescription = stringResource(R.string.chat_open_session_list)) {
                RuntimeIcon(RuntimeIconName.List, Modifier.size(22.dp))
            }
            Column(Modifier.weight(1f).clickable(onClick = onOpenModels).heightIn(min = 48.dp),
                verticalArrangement = Arrangement.Center) {
                Text(title.ifBlank { stringResource(R.string.chat_title) }, style = MaterialTheme.typography.titleMedium,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("$model ▾", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            RuntimeIconButton(onClick = onNewSession, contentDescription = stringResource(R.string.chat_drawer_new_task)) {
                RuntimeIcon(RuntimeIconName.Edit, Modifier.size(22.dp))
            }
            Box {
                RuntimeIconButton(onClick = { showMenu = true }, contentDescription = stringResource(R.string.chat_more_controls)) {
                    RuntimeIcon(RuntimeIconName.More, Modifier.size(22.dp))
                }
                DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                    DropdownMenuItem(text = { Text(stringResource(R.string.chat_workbench_controls)) },
                        onClick = { expanded = !expanded; showMenu = false })
                    DropdownMenuItem(text = { Text(stringResource(R.string.chat_floating_collapse)) }, onClick = {
                        showMenu = false
                        if (Settings.canDrawOverlays(context)) {
                            FloatingChatService.start(context); (context as? Activity)?.moveTaskToBack(true)
                        } else onShowFloatingPermissionDialog()
                    })
                }
            }
        }
        ChatWorkspaceBar(workspace, distroDisplayName, approvalMode, runMode, expanded,
            onToggle = { expanded = !expanded }, onOpenWorkspace = onOpenSessions,
            onOpenApproval = onOpenApprovalModes, onOpenBrowser = onOpenBrowser, browserHighlight = browserHighlight,
            onOpenRepository = onOpenRepository, repositoryHighlight = repositoryHighlight,
            toolchainHighlight = toolchainHighlight)
        androidx.compose.animation.AnimatedVisibility(expanded) {
            ChatWorkbenchControls(approvalMode, runMode, currentBranch, runtimeEvents, running,
                onOpenApprovalModes, onOpenRunModes, onOpenBranches, onOpenRuntime, onOpenToolchain, toolchainHighlight)
        }
    }
}
