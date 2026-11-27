package top.wkbin.taixu.ui.workspace

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.compose.viewmodel.koinViewModel
import top.wkbin.taixu.feature.workspace.R
import top.wkbin.taixu.ui.components.RuntimeActionRow
import top.wkbin.taixu.ui.components.RuntimeButton
import top.wkbin.taixu.ui.components.RuntimeCard
import top.wkbin.taixu.ui.components.RuntimeCircularProgressIndicator
import top.wkbin.taixu.ui.components.RuntimeIcon
import top.wkbin.taixu.ui.components.RuntimeIconButton
import top.wkbin.taixu.ui.components.RuntimeIconName
import top.wkbin.taixu.ui.components.RuntimeOutlinedButton
import top.wkbin.taixu.ui.components.RuntimeTopBar
import top.wkbin.taixu.ui.components.runtimeContentPadding
import top.wkbin.taixu.ui.miuix.RuntimeTheme
import top.wkbin.taixu.ui.miuix.Scaffold
import top.yukonga.miuix.kmp.basic.Text

@Composable
fun WorkspaceRunScreen(
    projectName: String,
    onBack: () -> Unit,
    onOpenFile: (String) -> Unit,
    onOpenPreview: (Int) -> Unit,
) {
    val model = koinViewModel<WorkspaceRunViewModel>()
    val state by model.state.collectAsStateWithLifecycle()
    LifecycleResumeEffect(projectName) {
        model.load(projectName)
        onPauseOrDispose { }
    }
    Scaffold(
        containerColor = RuntimeTheme.colorScheme.background,
        topBar = {
            RuntimeTopBar(title = stringResource(R.string.workspace_run_title), statusText = projectName, onBack = onBack) {
                RuntimeIconButton(
                    onClick = { model.load(projectName) },
                    enabled = !state.busy,
                    contentDescription = stringResource(R.string.workspace_cd_refresh),
                ) { RuntimeIcon(RuntimeIconName.Refresh, Modifier.size(20.dp)) }
            }
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = runtimeContentPadding(top = padding.calculateTopPadding() + 12.dp, bottom = padding.calculateBottomPadding() + 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            state.error?.let { error ->
                item { Text(error, color = RuntimeTheme.colorScheme.error) }
            }
            state.run?.let { run ->
                item {
                    WorkspaceRunOutput(
                        run = run,
                        state = state,
                        onStop = { model.stop(projectName) },
                        onOpenPreview = onOpenPreview,
                    )
                }
            }
            if (state.loaded) {
                item {
                    RuntimeCard(Modifier.fillMaxWidth()) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(WORKSPACE_RUN_FILE, style = RuntimeTheme.typography.titleSmall)
                            Text(
                                stringResource(R.string.workspace_run_config_help),
                                style = RuntimeTheme.typography.bodySmall,
                                color = RuntimeTheme.colorScheme.onSurfaceVariant,
                            )
                            RuntimeOutlinedButton(
                                onClick = {
                                    if (state.hasFile) onOpenFile(WORKSPACE_RUN_FILE)
                                    else model.createConfig(projectName) { onOpenFile(WORKSPACE_RUN_FILE) }
                                },
                                enabled = !state.busy,
                            ) {
                                Text(stringResource(if (state.hasFile) R.string.workspace_run_edit else R.string.workspace_run_create))
                            }
                            if (state.hasFile && state.configs.isEmpty() && state.error == null) {
                                Text(stringResource(R.string.workspace_run_empty), style = RuntimeTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            } else if (state.busy) {
                item { RuntimeCircularProgressIndicator() }
            }
            items(state.configs, key = { it.name }) { config ->
                RuntimeCard(Modifier.fillMaxWidth()) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(config.name, style = RuntimeTheme.typography.titleSmall)
                        SelectionContainer {
                            Text(config.command, style = RuntimeTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace))
                        }
                        Text(stringResource(R.string.workspace_run_cwd, config.cwd), style = RuntimeTheme.typography.bodySmall)
                        RuntimeButton(onClick = { model.start(projectName, config) }, enabled = !state.busy && !state.running) {
                            Text(stringResource(R.string.workspace_run_start))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WorkspaceRunOutput(
    run: WorkspaceRun,
    state: WorkspaceRunState,
    onStop: () -> Unit,
    onOpenPreview: (Int) -> Unit,
) {
    RuntimeCard(Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(run.config.name, style = RuntimeTheme.typography.titleSmall)
            Text(
                stringResource(
                    if (state.running) R.string.workspace_run_running else R.string.workspace_run_finished,
                    run.process.distroId.orEmpty(),
                ),
                style = RuntimeTheme.typography.bodySmall,
            )
            run.config.previewUrl?.let { Text(it, style = RuntimeTheme.typography.bodySmall) }
            if (state.running) {
                RuntimeActionRow {
                    run.config.previewPort?.let { port ->
                        RuntimeButton(onClick = { onOpenPreview(port) }, enabled = !state.busy) {
                            Text(stringResource(R.string.workspace_run_preview))
                        }
                    }
                    RuntimeOutlinedButton(onClick = onStop, enabled = !state.busy) {
                        Text(stringResource(R.string.workspace_run_stop))
                    }
                }
            }
            Text(stringResource(R.string.workspace_run_logs), style = RuntimeTheme.typography.labelLarge)
            SelectionContainer {
                Text(
                    state.logs.joinToString("\n").takeLast(16_384).ifEmpty { stringResource(R.string.workspace_run_no_logs) },
                    style = RuntimeTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
                )
            }
        }
    }
}
