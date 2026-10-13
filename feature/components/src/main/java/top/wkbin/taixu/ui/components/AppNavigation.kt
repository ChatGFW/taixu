package top.wkbin.taixu.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import top.wkbin.taixu.feature.components.R

/** Root pages open the same app drawer; secondary pages keep their back navigation. */
val LocalAppNavigation = staticCompositionLocalOf<(() -> Unit)?> { null }

@Composable
fun AppNavigationSection(selected: MainDestination, onNavigate: (MainDestination) -> Unit,
    onOpenTerminal: () -> Unit, onOpenTools: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        listOf(MainDestination.Agent, MainDestination.Workspace, MainDestination.Home, MainDestination.Settings).forEach { destination ->
            RuntimeCard(
                modifier = Modifier.fillMaxWidth(),
                containerColor = if (selected == destination) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                onClick = { onNavigate(destination) },
            ) {
                Row(Modifier.fillMaxWidth().heightIn(min = 40.dp), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    RuntimeIcon(destination.icon, Modifier.size(20.dp), tint = if (selected == destination)
                        MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(stringResource(when (destination) {
                        MainDestination.Agent -> R.string.components_drawer_chat
                        MainDestination.Workspace -> R.string.components_drawer_workspace
                        MainDestination.Home -> R.string.components_drawer_system
                        MainDestination.Settings -> R.string.components_drawer_settings
                    }), style = MaterialTheme.typography.labelLarge,
                        maxLines = 1, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurface)
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            RuntimeTextButton(onClick = onOpenTerminal, modifier = Modifier.weight(1f)) {
                RuntimeIcon(RuntimeIconName.Terminal, Modifier.size(18.dp))
                Text(stringResource(R.string.components_local_terminal), Modifier.padding(start = 6.dp), maxLines = 1,
                    style = MaterialTheme.typography.labelMedium, overflow = TextOverflow.Ellipsis)
            }
            RuntimeTextButton(onClick = onOpenTools, modifier = Modifier.weight(1f)) {
                RuntimeIcon(RuntimeIconName.Wrench, Modifier.size(18.dp))
                Text(stringResource(R.string.components_tools), Modifier.padding(start = 6.dp), maxLines = 1,
                    style = MaterialTheme.typography.labelMedium, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}
