package top.wkbin.taixu.ui.workspace.codex

import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.compose.viewmodel.koinViewModel
import top.wkbin.taixu.feature.workspace.R
import top.wkbin.taixu.harness.codex.CodexApproval
import top.wkbin.taixu.harness.codex.CodexEnd
import top.wkbin.taixu.harness.codex.CodexFailure
import top.wkbin.taixu.ui.components.NoticeBanner
import top.wkbin.taixu.ui.components.RuntimeActionRow
import top.wkbin.taixu.ui.components.RuntimeAlertDialog
import top.wkbin.taixu.ui.components.RuntimeCard
import top.wkbin.taixu.ui.components.RuntimeFilledTextField
import top.wkbin.taixu.ui.components.RuntimeIcon
import top.wkbin.taixu.ui.components.RuntimeIconButton
import top.wkbin.taixu.ui.components.RuntimeIconName
import top.wkbin.taixu.ui.components.RuntimeTextButton
import top.wkbin.taixu.ui.components.RuntimeTopBar
import top.wkbin.taixu.ui.components.runtimeContentPadding
import top.wkbin.taixu.ui.components.runtimeScrollViewport
import top.wkbin.taixu.ui.miuix.RuntimeTheme
import top.wkbin.taixu.ui.miuix.Scaffold
import top.yukonga.miuix.kmp.basic.Text

@Composable
fun WorkspaceCodexScreen(project: String, onBack: () -> Unit) {
    val model = koinViewModel<WorkspaceCodexViewModel>()
    val state by model.state.collectAsStateWithLifecycle()
    val running = state.phase in setOf(CodexPhase.STARTING, CodexPhase.RUNNING, CodexPhase.STOPPING)
    val ready = state.phase == CodexPhase.READY
    var confirmExit by rememberSaveable { mutableStateOf(false) }
    var confirmNew by rememberSaveable { mutableStateOf(false) }
    val back = { if (running) confirmExit = true else onBack() }
    BackHandler(enabled = running, onBack = back)
    LaunchedEffect(project) { model.initialize(project) }

    Scaffold(
        containerColor = RuntimeTheme.colorScheme.background,
        topBar = {
            RuntimeTopBar(
                title = stringResource(R.string.workspace_codex_title),
                statusText = state.target?.let { "$project · ${it.distribution}" } ?: project,
                onBack = back,
            ) {
                CodexIcon(RuntimeIconName.Refresh, R.string.workspace_codex_retry, ready) { model.retry(project) }
                CodexIcon(RuntimeIconName.Plus, R.string.workspace_codex_new, ready && state.canReset) { confirmNew = true }
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().then(runtimeScrollViewport(padding))
                .padding(top = padding.calculateTopPadding(), bottom = padding.calculateBottomPadding())
                .imePadding(),
        ) {
            state.failure?.let {
                NoticeBanner(
                    text = stringResource(it.stringId()),
                    isError = true,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                )
            }
            val list = rememberLazyListState()
            LaunchedEffect(state.lines.lastOrNull()?.id) {
                val count = state.lines.size
                if (count > 0 && list.layoutInfo.visibleItemsInfo.lastOrNull()?.index?.let { it >= count - 3 } != false) {
                    list.scrollToItem(count - 1)
                }
            }
            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                state = list,
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = runtimeContentPadding(top = 12.dp, bottom = 12.dp),
            ) {
                if (state.lines.isEmpty()) {
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(stringResource(R.string.workspace_codex_intro), style = RuntimeTheme.typography.bodyMedium)
                            SelectionContainer {
                                Text(CODEX_LOGIN, fontFamily = FontFamily.Monospace, style = RuntimeTheme.typography.bodySmall)
                            }
                        }
                    }
                }
                items(state.lines, key = { "${it.user}:${it.id}" }) { line ->
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            stringResource(if (line.user) R.string.workspace_codex_you else R.string.workspace_codex_assistant),
                            style = RuntimeTheme.typography.labelMedium,
                            color = RuntimeTheme.colorScheme.onSurfaceVariant,
                        )
                        SelectionContainer {
                            Text(line.text, style = RuntimeTheme.typography.bodyMedium)
                        }
                    }
                }
            }
            state.approvals.firstOrNull()?.let { request ->
                CodexPermissionCard(
                    request = request,
                    enabled = state.phase == CodexPhase.RUNNING,
                    onDecline = { model.decline(request.token) },
                    onStop = model::stop,
                )
            }
            codexStatus(state)?.let {
                Text(stringResource(it), modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                    color = RuntimeTheme.colorScheme.onSurfaceVariant, style = RuntimeTheme.typography.bodySmall)
            }
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                RuntimeFilledTextField(
                    value = state.draft,
                    onValueChange = model::edit,
                    enabled = ready && state.target != null,
                    modifier = Modifier.weight(1f),
                    labelText = stringResource(R.string.workspace_codex_prompt),
                    maxLines = 4,
                )
                if (running) {
                    CodexIcon(RuntimeIconName.Stop, R.string.workspace_codex_stop, state.phase != CodexPhase.STOPPING, model::stop)
                } else {
                    CodexIcon(
                        RuntimeIconName.Send, R.string.workspace_codex_send,
                        state.canSend,
                        model::send,
                    )
                }
            }
        }
    }

    if (confirmExit) {
        CodexConfirmation(R.string.workspace_codex_exit, R.string.workspace_codex_exit_body,
            R.string.workspace_codex_exit_action, { confirmExit = false }, onBack)
    }
    if (confirmNew) {
        CodexConfirmation(R.string.workspace_codex_new, R.string.workspace_codex_new_body,
            R.string.workspace_codex_new, { confirmNew = false }) {
            confirmNew = false
            model.newConversation()
        }
    }
}

@Composable
private fun CodexPermissionCard(request: CodexApproval, enabled: Boolean, onDecline: () -> Unit, onStop: () -> Unit) {
    RuntimeCard(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                stringResource(if (request.fileChange) R.string.workspace_codex_file_request else R.string.workspace_codex_command_request),
                style = RuntimeTheme.typography.titleSmall,
            )
            if (request.summary.isNotBlank()) {
                SelectionContainer {
                    Text(request.summary, maxLines = 6, fontFamily = FontFamily.Monospace, style = RuntimeTheme.typography.bodySmall)
                }
            }
            RuntimeActionRow {
                RuntimeTextButton(onClick = onDecline, enabled = enabled) { Text(stringResource(R.string.workspace_codex_decline)) }
                RuntimeTextButton(onClick = onStop, enabled = enabled) { Text(stringResource(R.string.workspace_codex_stop)) }
            }
        }
    }
}

@Composable
private fun CodexConfirmation(
    @StringRes title: Int,
    @StringRes body: Int,
    @StringRes action: Int,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    RuntimeAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(title)) },
        text = { Text(stringResource(body)) },
        confirmButton = { RuntimeTextButton(onClick = onConfirm) { Text(stringResource(action)) } },
        dismissButton = { RuntimeTextButton(onClick = onDismiss) { Text(stringResource(R.string.workspace_cancel)) } },
    )
}

@Composable
private fun CodexIcon(icon: RuntimeIconName, @StringRes label: Int, enabled: Boolean, action: () -> Unit) {
    RuntimeIconButton(onClick = action, enabled = enabled, contentDescription = stringResource(label)) {
        RuntimeIcon(icon, Modifier.size(20.dp))
    }
}

private fun codexStatus(state: WorkspaceCodexState): Int? = when (state.phase) {
    CodexPhase.LOADING -> R.string.workspace_codex_loading
    CodexPhase.STARTING -> R.string.workspace_codex_starting
    CodexPhase.RUNNING -> R.string.workspace_codex_running
    CodexPhase.STOPPING -> R.string.workspace_codex_stopping
    CodexPhase.READY -> when (state.outcome) {
        CodexEnd.COMPLETED, null -> null
        CodexEnd.INTERRUPTED -> R.string.workspace_codex_stopped
        CodexEnd.FAILED -> R.string.workspace_codex_turn_failed
    }
}

private fun CodexFailure.stringId() = when (this) {
    CodexFailure.AUTH -> R.string.workspace_codex_auth
    CodexFailure.EXITED -> R.string.workspace_codex_exited
    CodexFailure.SANDBOX -> R.string.workspace_codex_sandbox
    CodexFailure.WORKSPACE -> R.string.workspace_codex_workspace
    CodexFailure.RUNTIME -> R.string.workspace_codex_runtime
    CodexFailure.HISTORY -> R.string.workspace_codex_history
    CodexFailure.STORAGE -> R.string.workspace_codex_storage
    CodexFailure.TIMEOUT -> R.string.workspace_codex_timeout
    CodexFailure.PROTOCOL, CodexFailure.RPC -> R.string.workspace_codex_protocol
}

private const val CODEX_LOGIN = "CODEX_HOME=/opt/taixu/data/codex /opt/taixu/bin/codex login"
