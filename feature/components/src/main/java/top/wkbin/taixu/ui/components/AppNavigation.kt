package top.wkbin.taixu.ui.components

import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import top.wkbin.taixu.feature.components.R

/** Root pages open the same app drawer; secondary pages keep their back navigation. */
val LocalAppNavigation = staticCompositionLocalOf<(() -> Unit)?> { null }

@Composable
fun AppNavigationSection(selected: MainDestination, onNavigate: (MainDestination) -> Unit,
    onOpenTerminal: () -> Unit, onOpenTools: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        RuntimeCard(Modifier.fillMaxWidth(), containerColor = MaterialTheme.colorScheme.surfaceContainer,
            contentPadding = PaddingValues(4.dp), shape = MaterialTheme.shapes.large) {
            Row(Modifier.fillMaxWidth().selectableGroup(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                listOf(MainDestination.Agent, MainDestination.Workspace, MainDestination.Home, MainDestination.Settings).forEach { destination ->
                    val active = selected == destination
                    val tint = if (active) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                    Surface(Modifier.weight(1f), shape = MaterialTheme.shapes.medium,
                        color = if (active) MaterialTheme.colorScheme.secondaryContainer else androidx.compose.ui.graphics.Color.Transparent) {
                        Column(Modifier.fillMaxWidth().selectable(selected = active, role = Role.Tab,
                            onClick = { onNavigate(destination) }).heightIn(min = 64.dp).padding(vertical = 8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically)) {
                            RuntimeIcon(when (destination) {
                                MainDestination.Agent -> RuntimeIconName.Chat
                                MainDestination.Workspace -> RuntimeIconName.Folder
                                MainDestination.Home -> RuntimeIconName.Home
                                MainDestination.Settings -> RuntimeIconName.Settings
                            }, Modifier.size(20.dp), tint)
                            Text(stringResource(when (destination) {
                                MainDestination.Agent -> R.string.components_drawer_tab_chat
                                MainDestination.Workspace -> R.string.components_drawer_tab_workspace
                                MainDestination.Home -> R.string.components_drawer_tab_system
                                MainDestination.Settings -> R.string.components_drawer_settings
                            }), style = MaterialTheme.typography.labelMedium, color = tint,
                                maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            RuntimeTextButton(onClick = onOpenTerminal, modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp)) {
                RuntimeIcon(RuntimeIconName.Terminal, Modifier.size(18.dp))
                Text(stringResource(R.string.components_local_terminal), Modifier.padding(start = 6.dp), maxLines = 1,
                    style = MaterialTheme.typography.labelMedium, overflow = TextOverflow.Ellipsis)
            }
            RuntimeTextButton(onClick = onOpenTools, modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp)) {
                RuntimeIcon(RuntimeIconName.Wrench, Modifier.size(18.dp))
                Text(stringResource(R.string.components_tools), Modifier.padding(start = 6.dp), maxLines = 1,
                    style = MaterialTheme.typography.labelMedium, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}
