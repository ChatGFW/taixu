package top.wkbin.taixu.ui.chat

import android.app.Activity
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
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
@OptIn(ExperimentalMaterial3Api::class)
internal fun ChatTopBar(
    title: String, workspace: String, distroDisplayName: String, activeModel: AiModelEntity?,
    approvalMode: ApprovalMode, runMode: RunMode, currentBranch: ConversationBranch?,
    runtimeEvents: List<HarnessEvent>, running: Boolean, onShowFloatingPermissionDialog: () -> Unit,
    onOpenSessions: () -> Unit, onNewSession: () -> Unit, onOpenModels: () -> Unit,
    onOpenApprovalModes: () -> Unit, onOpenRunModes: () -> Unit, onOpenBranches: () -> Unit,
    onOpenRuntime: () -> Unit, onOpenBrowser: (() -> Unit)? = null, browserHighlight: Boolean = false,
    onOpenRepository: (() -> Unit)? = null, repositoryHighlight: Boolean = false,
    toolchainHighlight: Boolean = false, onOpenToolchain: () -> Unit,
    onOpenReasoning: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    var expanded by rememberSaveable { mutableStateOf(false) }
    val model = activeModel?.let { it.model.substringBefore(',').trim().ifBlank { it.name } }
    Column(
        Modifier
            .fillMaxWidth()
            .statusBarsPadding(),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            RuntimeIconButton(
                onClick = onOpenSessions,
                modifier = Modifier.size(44.dp),
                contentDescription = stringResource(R.string.chat_open_session_list),
            ) {
                RuntimeIcon(RuntimeIconName.List, Modifier.size(22.dp))
            }
            Row(
                Modifier
                    .weight(1f)
                    .clickable(onClick = onOpenModels)
                    .heightIn(min = 48.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = title.ifBlank { stringResource(R.string.chat_title) },
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (model != null) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.7f),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(3.dp),
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
                        ) {
                            Text(
                                text = model,
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            RuntimeIcon(
                                name = RuntimeIconName.ChevronDown,
                                modifier = Modifier.size(11.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                if (runMode == RunMode.PLAN) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.5f),
                    ) {
                        Text(
                            text = stringResource(R.string.chat_run_mode_plan),
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.tertiary,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            maxLines = 1,
                        )
                    }
                }
            }
            if (approvalMode == ApprovalMode.FULL_ACCESS) {
                RuntimeIconButton(
                    onClick = onOpenApprovalModes,
                    modifier = Modifier.size(44.dp),
                    contentDescription = stringResource(R.string.chat_approval_full_access),
                ) {
                    RuntimeIcon(RuntimeIconName.Shield, Modifier.size(20.dp), MaterialTheme.colorScheme.error)
                }
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
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.chat_workbench_controls), Modifier.padding(horizontal = 24.dp),
                    style = MaterialTheme.typography.titleMedium)
                ChatWorkspaceBar(workspace, distroDisplayName, approvalMode, runMode,
                    onOpenWorkspace = closeThen(onOpenSessions), onOpenApproval = closeThen(onOpenApprovalModes),
                    onOpenBrowser = onOpenBrowser?.let(closeThen), browserHighlight = browserHighlight,
                    onOpenRepository = onOpenRepository?.let(closeThen), repositoryHighlight = repositoryHighlight)
                ChatWorkbenchControls(approvalMode, runMode, currentBranch, runtimeEvents, running,
                    closeThen(onOpenApprovalModes), closeThen(onOpenRunModes), closeThen(onOpenBranches),
                    closeThen(onOpenRuntime), closeThen(onOpenToolchain), toolchainHighlight,
                    onReasoning = onOpenReasoning?.let(closeThen))
                RuntimeTextButton(modifier = Modifier.padding(horizontal = 16.dp), onClick = {
                    expanded = false
                    if (Settings.canDrawOverlays(context)) {
                        FloatingChatService.start(context); (context as? Activity)?.moveTaskToBack(true)
                    } else onShowFloatingPermissionDialog()
                }) {
                    Text(stringResource(R.string.chat_floating_collapse))
                }
            }
        }
    }
}
