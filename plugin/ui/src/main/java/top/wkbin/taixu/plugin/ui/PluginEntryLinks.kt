package top.wkbin.taixu.plugin.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import top.wkbin.taixu.plugin.api.PluginExtensionRef
import top.wkbin.taixu.plugin.runtime.PluginController
import top.wkbin.taixu.plugin.runtime.PluginPhase
import top.wkbin.taixu.ui.components.RuntimeTextButton

/** Entries reference validated pages and disappear when their owner cannot admit work. */
@Composable
fun PluginEntryLinks(
    plugins: List<NativePagePlugin>,
    controller: PluginController,
    location: PluginEntryLocation,
    onOpen: (PluginExtensionRef) -> Unit,
) {
    val states by controller.states.collectAsState()
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        plugins.filter { states[it.descriptor.id]?.phase == PluginPhase.RUNNING }.forEach { plugin ->
            plugin.entries.filter { it.location == location }.forEach { entry ->
                RuntimeTextButton(
                    onClick = { onOpen(PluginExtensionRef(plugin.descriptor.id, entry.pageId)) },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(entry.title(), maxLines = 1, overflow = TextOverflow.Ellipsis) }
            }
        }
    }
}
