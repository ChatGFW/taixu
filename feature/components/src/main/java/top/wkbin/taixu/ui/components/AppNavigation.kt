package top.wkbin.taixu.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import top.wkbin.taixu.feature.components.R

/** Root pages open the same app drawer; secondary pages keep their back navigation. */
val LocalAppNavigation = staticCompositionLocalOf<(() -> Unit)?> { null }

@Composable
fun AppNavigationSection(
    selected: MainDestination,
    onNavigate: (MainDestination) -> Unit,
    onOpenTerminal: () -> Unit,
    onOpenTools: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val pluginMenu = LocalPluginMenuContent.current
    val destinations = listOf(
        MainDestination.Workspace to R.string.components_drawer_tab_workspace,
        MainDestination.Home to R.string.components_drawer_tab_system,
    )
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        destinations.chunked(2).forEach { pair ->
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                pair.forEach { (destination, label) ->
                    RuntimeNavigationTile(stringResource(label), destination.icon, { onNavigate(destination) },
                        Modifier.weight(1f), active = selected == destination && !LocalPluginDestinationActive.current)
                }
            }
        }
        if (pluginMenu != null) pluginMenu() else {
            RuntimeNavigationTile(stringResource(R.string.components_local_terminal), RuntimeIconName.Terminal,
                onOpenTerminal, Modifier.fillMaxWidth())
        }
        RuntimeNavigationTile(stringResource(R.string.components_tools), RuntimeIconName.Wrench,
            onOpenTools, Modifier.fillMaxWidth(), showChevron = true)
    }
}
