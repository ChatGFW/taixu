package top.wkbin.taixu.ui.chat

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import top.wkbin.taixu.core.model.ApprovalMode
import top.wkbin.taixu.core.model.RunMode
import top.wkbin.taixu.feature.chat.R
import top.wkbin.taixu.harness.events.HarnessEvent
import top.wkbin.taixu.harness.session.ConversationBranch
import top.wkbin.taixu.ui.components.*

@Composable
internal fun ChatWorkspaceBar(workspace: String, environment: String, approvalMode: ApprovalMode,
    runMode: RunMode, onOpenWorkspace: () -> Unit,
    onOpenApproval: () -> Unit, onOpenBrowser: (() -> Unit)?, browserHighlight: Boolean,
    onOpenRepository: (() -> Unit)?, repositoryHighlight: Boolean) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        RuntimeTextButton(onClick = onOpenWorkspace, modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp)) {
            RuntimeIcon(RuntimeIconName.FolderOpen, Modifier.size(16.dp), MaterialTheme.colorScheme.onSurfaceVariant)
            Text(workspace.trimEnd('/').substringAfterLast('/').takeIf { it.isNotBlank() }?.let { "$it · $environment" } ?: environment, Modifier.padding(start = 6.dp),
                style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (approvalMode == ApprovalMode.FULL_ACCESS) RuntimeTextButton(onClick = onOpenApproval,
            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 8.dp)) {
            Text(stringResource(R.string.chat_approval_full_access), color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.labelSmall, maxLines = 1)
        }
        if (runMode == RunMode.PLAN) Text(stringResource(R.string.chat_run_mode_plan),
            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.tertiary)
        if (onOpenRepository != null) RuntimeIconButton(onClick = onOpenRepository, contentDescription = stringResource(R.string.chat_repository)) {
            RuntimeIcon(RuntimeIconName.GitBranch, Modifier.size(18.dp), if (repositoryHighlight)
                MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (onOpenBrowser != null) RuntimeIconButton(onClick = onOpenBrowser, contentDescription = stringResource(R.string.chat_browser_entry)) {
            RuntimeIcon(RuntimeIconName.Globe, Modifier.size(18.dp), if (browserHighlight)
                MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ChatWorkbenchControls(approvalMode: ApprovalMode, runMode: RunMode, branch: ConversationBranch?,
    events: List<HarnessEvent>, running: Boolean, onApproval: () -> Unit, onRunMode: () -> Unit,
    onBranches: () -> Unit, onRuntime: () -> Unit, onTools: () -> Unit, toolchainHighlight: Boolean,
    onReasoning: (() -> Unit)? = null) {
    val mode = when (approvalMode) {
        ApprovalMode.FULL_ACCESS -> R.string.chat_approval_full_access
        ApprovalMode.ASSISTED -> R.string.chat_approval_assisted
        ApprovalMode.REQUEST -> R.string.chat_approval_request
    }
    val rounds = events.count { it is HarnessEvent.ProviderRoundStarted }
    RuntimeCard(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            ControlAction(RuntimeIconName.Shield, stringResource(mode), onApproval)
            ControlAction(runModeIcon(runMode), stringResource(if (runMode == RunMode.PLAN) R.string.chat_run_mode_plan else R.string.chat_run_mode_build), onRunMode)
            ControlAction(RuntimeIconName.Hub, branch?.name ?: stringResource(R.string.chat_main_line), onBranches)
            ControlAction(RuntimeIconName.Logs, if (running) stringResource(R.string.chat_round_number, rounds) else stringResource(R.string.chat_runtime_overview), onRuntime)
            ControlAction(RuntimeIconName.Wrench, stringResource(R.string.chat_toolchain_entry) + if (toolchainHighlight) " •" else "", onTools)
            if (onReasoning != null) {
                ControlAction(RuntimeIconName.Sparkles, stringResource(R.string.chat_reasoning_effort), onReasoning)
            }
        }
    }
}

@Composable
private fun ControlAction(icon: RuntimeIconName, label: String, onClick: () -> Unit) {
    RuntimeTextButton(onClick = onClick, contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp)) {
        RuntimeIcon(icon, Modifier.size(16.dp))
        Text(label, Modifier.padding(start = 6.dp).widthIn(max = 160.dp), style = MaterialTheme.typography.labelMedium,
            maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
