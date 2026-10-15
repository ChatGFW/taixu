package top.wkbin.taixu.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.compose.viewmodel.koinViewModel
import top.wkbin.taixu.feature.settings.R
import top.wkbin.taixu.ui.components.RuntimeCard
import top.wkbin.taixu.ui.components.RuntimeTextButton

@Composable
fun PackagedPluginInventorySection(onOpenTool: (String) -> Unit, viewModel: PackagedPluginsViewModel = koinViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(R.string.harness_plugins_title), style = MaterialTheme.typography.titleLarge)
        Text(stringResource(R.string.harness_plugins_scope), style = MaterialTheme.typography.bodyMedium)
        viewModel.harnessPlugins.forEach { plugin ->
            RuntimeCard(modifier = Modifier.fillMaxWidth()) {
                Text(plugin.id, style = MaterialTheme.typography.titleMedium)
                Text(plugin.version, style = MaterialTheme.typography.bodySmall)
                val points = plugin.extensions.groupingBy { it.point }.eachCount()
                Text(stringResource(R.string.harness_plugins_counts, points[top.wkbin.taixu.plugin.api.PluginExtensionPoint.TOOL] ?: 0,
                    points[top.wkbin.taixu.plugin.api.PluginExtensionPoint.CONTEXT] ?: 0, points[top.wkbin.taixu.plugin.api.PluginExtensionPoint.PROVIDER] ?: 0,
                    points[top.wkbin.taixu.plugin.api.PluginExtensionPoint.WORKFLOW] ?: 0))
            }
        }
        Text(stringResource(R.string.packaged_plugins_title), style = MaterialTheme.typography.titleLarge)
        Text(stringResource(R.string.packaged_plugins_scope), style = MaterialTheme.typography.bodyMedium)
        if (state.failed) Text(stringResource(R.string.packaged_plugins_failed), color = MaterialTheme.colorScheme.error)
        state.entries.forEach { entry ->
            RuntimeCard(modifier = Modifier.fillMaxWidth()) {
                Text(entry.name, style = MaterialTheme.typography.titleMedium)
                Text(entry.descriptor.id, style = MaterialTheme.typography.bodySmall)
                Text(stringResource(R.string.packaged_plugins_version, entry.descriptor.version, entry.installedVersion ?: stringResource(R.string.packaged_plugins_uninstalled)))
                RuntimeTextButton(onClick = { onOpenTool(entry.toolId) }) { Text(stringResource(R.string.packaged_plugins_details)) }
            }
        }
    }
}
