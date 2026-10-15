package top.wkbin.taixu.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
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
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // 1. 核心四大导航标签栏 (水平 4 等分，极致紧凑)
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceContainer,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(4.dp)
                    .selectableGroup(),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                val destinations = listOf(
                    MainDestination.Agent to R.string.components_drawer_tab_chat,
                    MainDestination.Workspace to R.string.components_drawer_tab_workspace,
                    MainDestination.Home to R.string.components_drawer_tab_system,
                    MainDestination.Settings to R.string.components_drawer_settings,
                )

                destinations.forEach { (destination, labelRes) ->
                    val active = selected == destination
                    val activeBg = if (active) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent
                    val tint = if (active) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant

                    Surface(
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        color = activeBg,
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .selectable(
                                    selected = active,
                                    role = Role.Tab,
                                    onClick = { onNavigate(destination) },
                                )
                                .padding(vertical = 8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            RuntimeIcon(
                                name = destination.icon,
                                modifier = Modifier.size(20.dp),
                                tint = tint,
                            )
                            Text(
                                text = stringResource(labelRes),
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = if (active) FontWeight.SemiBold else FontWeight.Medium,
                                ),
                                color = tint,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        }

        // 2. 演武终端与工具中心快捷操作 (水平并排药丸按钮)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (pluginMenu == null) Surface(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .clickable(onClick = onOpenTerminal),
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                ) {
                    RuntimeIcon(
                        name = RuntimeIconName.Terminal,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = stringResource(R.string.components_local_terminal),
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Medium),
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            Surface(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .clickable(onClick = onOpenTools),
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                ) {
                    RuntimeIcon(
                        name = RuntimeIconName.Wrench,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = stringResource(R.string.components_tools),
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Medium),
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        pluginMenu?.invoke()
    }
}
