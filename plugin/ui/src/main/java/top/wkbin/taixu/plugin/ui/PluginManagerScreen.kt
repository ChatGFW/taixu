package top.wkbin.taixu.plugin.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import top.wkbin.taixu.plugin.api.PluginExtensionRef
import top.wkbin.taixu.plugin.runtime.PluginController
import top.wkbin.taixu.plugin.runtime.PluginPhase
import top.wkbin.taixu.ui.components.RuntimeCard
import top.wkbin.taixu.ui.components.RuntimeSwitch
import top.wkbin.taixu.ui.components.RuntimeTextButton
import top.wkbin.taixu.ui.components.RuntimeTopBar

@Composable
fun PluginManagerScreen(
    plugins: List<NativePagePlugin>,
    controller: PluginController,
    onOpen: (PluginExtensionRef) -> Unit,
    onBack: () -> Unit,
    packageContent: @Composable () -> Unit = {},
) {
    val states by controller.states.collectAsState()
    val initialized by controller.initialized.collectAsState()
    val preferenceFailure by controller.preferenceFailure.collectAsState()
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    fun perform(action: suspend () -> Unit) {
        if (busy) return
        busy = true
        scope.launch { try { action() } finally { busy = false } }
    }
    Scaffold(topBar = { RuntimeTopBar(stringResource(R.string.ui_plugin_manager_title), onBack) }) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                Text(stringResource(R.string.ui_plugin_manager_scope), style = MaterialTheme.typography.bodyMedium)
            }
            if (preferenceFailure) item {
                Text(stringResource(R.string.ui_plugin_preferences_failed), color = MaterialTheme.colorScheme.error)
                if (!initialized) RuntimeTextButton(onClick = { perform { controller.initialize() } }, enabled = !busy) {
                    Text(stringResource(R.string.ui_plugin_retry))
                }
            }
            items(plugins, key = { it.descriptor.id }) { plugin ->
                val status = states.getValue(plugin.descriptor.id)
                val title = plugin.title()
                RuntimeCard(modifier = Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(phaseLabel(status.phase), style = MaterialTheme.typography.bodyMedium)
                        }
                        RuntimeSwitch(
                            modifier = Modifier.semantics { contentDescription = title },
                            checked = status.selected, onCheckedChange = { enabled -> perform { controller.setEnabled(plugin.descriptor.id, enabled) } },
                            enabled = initialized && !busy && status.phase !in setOf(PluginPhase.STARTING, PluginPhase.DRAINING),
                        )
                    }
                    if (status.phase == PluginPhase.FAILED) RuntimeTextButton(
                        onClick = { perform { controller.setEnabled(plugin.descriptor.id, status.selected) } }, enabled = !busy,
                    ) { Text(stringResource(R.string.ui_plugin_retry)) }
                    if (status.phase == PluginPhase.RUNNING) {
                        plugin.entries.distinctBy { it.pageId }.forEach { entry ->
                            RuntimeTextButton(onClick = { onOpen(PluginExtensionRef(plugin.descriptor.id, entry.pageId)) }) {
                                Text(entry.title(), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
            }
            item { packageContent() }
        }
    }
}

@Composable
private fun phaseLabel(phase: PluginPhase): String = stringResource(when (phase) {
    PluginPhase.READY -> R.string.ui_plugin_ready
    PluginPhase.STARTING -> R.string.ui_plugin_starting
    PluginPhase.RUNNING -> R.string.ui_plugin_running
    PluginPhase.DRAINING -> R.string.ui_plugin_draining
    PluginPhase.STOPPED -> R.string.ui_plugin_stopped
    PluginPhase.BLOCKED -> R.string.ui_plugin_blocked
    PluginPhase.FAILED -> R.string.ui_plugin_failed
})
