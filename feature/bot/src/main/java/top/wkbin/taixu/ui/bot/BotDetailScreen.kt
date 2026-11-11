package top.wkbin.taixu.ui.bot

import top.wkbin.taixu.ui.miuix.RuntimeSpacing
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import top.wkbin.taixu.ui.miuix.RuntimeTheme
import top.wkbin.taixu.ui.miuix.Scaffold
import top.yukonga.miuix.kmp.basic.SnackbarHost
import top.yukonga.miuix.kmp.basic.SnackbarHostState
import top.yukonga.miuix.kmp.basic.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.compose.viewmodel.koinViewModel
import top.wkbin.taixu.core.model.bot.Bot
import top.wkbin.taixu.feature.bot.R
import top.wkbin.taixu.ui.components.RuntimeAlertDialog
import top.wkbin.taixu.ui.components.RuntimeCircularProgressIndicator
import top.wkbin.taixu.ui.components.RuntimeFilterChip
import top.wkbin.taixu.ui.components.RuntimeIcon
import top.wkbin.taixu.ui.components.RuntimeIconButton
import top.wkbin.taixu.ui.components.RuntimeIconName
import top.wkbin.taixu.ui.components.RuntimeTextButton
import top.wkbin.taixu.ui.components.RuntimeTopBar
import top.wkbin.taixu.ui.components.runtimeContentPadding
import top.wkbin.taixu.ui.components.runtimeScrollViewport

@Composable
fun BotDetailScreen(
    botId: String,
    onBack: () -> Unit,
    onOpenSession: (String) -> Unit = {},
    viewModel: BotDetailViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val profiles by viewModel.modelProfiles.collectAsStateWithLifecycle()
    val mcpServers by viewModel.mcpServers.collectAsStateWithLifecycle()
    val runtimeServerStatus by viewModel.externalRuntimeServerStatus.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    var memoryPath by rememberSaveable(botId) { mutableStateOf<String?>(null) }
    var memoryHash by rememberSaveable(botId) { mutableStateOf<String?>(null) }
    val bot = state.bot

    LaunchedEffect(botId) { viewModel.bind(botId) }
    LaunchedEffect(Unit) { viewModel.notices.collect { snackbarHostState.showSnackbar(it) } }
    LaunchedEffect(Unit) { viewModel.deleted.collect { onBack() } }

    Scaffold(
        topBar = {
            RuntimeTopBar(
                title = bot?.name ?: stringResource(R.string.fbot_loading),
                onBack = onBack,
                statusText = bot?.role,
                actions = {
                    RuntimeIconButton(
                        onClick = { bot?.let { onOpenSession(it.sessionId) } },
                        enabled = bot != null,
                        contentDescription = stringResource(R.string.fbot_open_session),
                    ) {
                        RuntimeIcon(RuntimeIconName.Chat, Modifier.size(20.dp))
                    }
                    RuntimeIconButton(
                        onClick = viewModel::exportBot,
                        enabled = bot != null && !state.busy,
                        contentDescription = stringResource(R.string.fbot_export),
                    ) {
                        RuntimeIcon(RuntimeIconName.FolderDownload, Modifier.size(20.dp))
                    }
                    RuntimeIconButton(
                        onClick = { confirmDelete = true },
                        enabled = bot != null && !state.busy,
                        contentDescription = stringResource(R.string.fbot_delete),
                    ) {
                        RuntimeIcon(
                            RuntimeIconName.Trash,
                            Modifier.size(20.dp),
                            tint = RuntimeTheme.colorScheme.error,
                        )
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        if (bot == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                if (state.loading) {
                    RuntimeCircularProgressIndicator(Modifier.size(28.dp))
                } else {
                    Text(
                        stringResource(R.string.fbot_bot_missing),
                        color = RuntimeTheme.colorScheme.error,
                    )
                }
            }
            return@Scaffold
        }
        Column(
            Modifier
                .fillMaxSize()
                .then(runtimeScrollViewport(padding))
                .padding(bottom = padding.calculateBottomPadding()),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = padding.calculateTopPadding())
                    .padding(runtimeContentPadding(top = RuntimeSpacing.pageTop)),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                listOf(
                    R.string.fbot_tab_persona to RuntimeIconName.Bot,
                    R.string.fbot_tab_memory to RuntimeIconName.History,
                    R.string.fbot_tab_schedule to RuntimeIconName.Alarm,
                ).forEachIndexed { index, (label, icon) ->
                    RuntimeFilterChip(
                        selected = tab == index,
                        onClick = { tab = index },
                        label = { Text(stringResource(label)) },
                        leadingIcon = { RuntimeIcon(icon, Modifier.size(16.dp)) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            when (tab) {
                TAB_PERSONA -> BotPersonaPanel(
                    bot = bot,
                    busy = state.busy,
                    profiles = profiles,
                    mcpServers = mcpServers,
                    onSave = viewModel::save,
                    externalRuntime = {
                        BotExternalRuntimePanel(
                            bot = bot,
                            busy = state.busy,
                            tokens = state.runtimeTokens,
                            issuedPlaintext = state.issuedRuntimeToken,
                            serverStatus = runtimeServerStatus,
                            onIssue = viewModel::issueRuntimeToken,
                            onRevoke = viewModel::revokeRuntimeToken,
                            onRevokeExpired = viewModel::revokeExpiredRuntimeTokens,
                            onRestoreLocal = viewModel::restoreLocalRuntime,
                            onToggleServer = viewModel::toggleExternalRuntimeServer,
                            onClearIssued = viewModel::clearIssuedRuntimeToken,
                        )
                    },
                )
                TAB_MEMORY -> BotMemoryPanel(
                    files = state.memoryFiles,
                    commits = state.commits,
                    busy = state.busy,
                    onRollback = viewModel::rollback,
                    onOpenFile = { memoryPath = it },
                    onOpenDiff = { memoryHash = it },
                )
                else -> BotSchedulePanel(
                    schedules = state.schedules,
                    deliveries = state.deliveries,
                    groups = state.groups,
                    busy = state.busy,
                    onAdd = viewModel::addSchedule,
                    onToggle = viewModel::setScheduleEnabled,
                    onDelete = viewModel::deleteSchedule,
                )
            }
        }
    }

    memoryPath?.let { path ->
        key(botId, path) {
            BotMemoryEditorDialog(path, { viewModel.closeMemoryDraft(botId, path); memoryPath = null },
                state = viewModel.memoryDraft(botId, path),
                load = { viewModel.readMemoryDocument(botId, path) },
                save = { document, content -> viewModel.saveMemoryDocument(botId, document, content) })
        }
    }
    memoryHash?.let { hash ->
        key(botId, hash) { BotMemoryDiffDialog(hash, { memoryHash = null }) { viewModel.memoryDiff(botId, hash) } }
    }
    if (confirmDelete && bot != null) {
        RuntimeAlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.fbot_delete_bot_title)) },
            text = { Text(stringResource(R.string.fbot_delete_bot_confirm, bot.name)) },
            confirmButton = {
                RuntimeTextButton(onClick = {
                    confirmDelete = false
                    viewModel.deleteBot()
                }) { Text(stringResource(R.string.fbot_delete)) }
            },
            dismissButton = {
                RuntimeTextButton(onClick = { confirmDelete = false }) {
                    Text(stringResource(R.string.fbot_cancel))
                }
            },
        )
    }
}

private const val TAB_PERSONA = 0
private const val TAB_MEMORY = 1
