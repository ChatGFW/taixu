package top.wkbin.taixu.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import top.wkbin.taixu.ui.miuix.RuntimeTheme
import top.yukonga.miuix.kmp.glass.GlassIconButton
import top.yukonga.miuix.kmp.glass.GlassNavigationItem
import top.yukonga.miuix.kmp.glass.GlassNavigationRail
import top.yukonga.miuix.kmp.glass.GlassNavigationRailDefaults
import top.yukonga.miuix.kmp.glass.rememberGlassNavigationRailState

private val RailPanelWidth = 84.dp
val RuntimeNavigationRailWidth = RailPanelWidth + GlassNavigationRailDefaults.Margin

internal val runtimeRailInsets: WindowInsets
    @Composable get() = WindowInsets.systemBars.union(WindowInsets.displayCutout)
        .only(WindowInsetsSides.Vertical + WindowInsetsSides.Start)

data class RuntimeRailItem(
    val label: String,
    val icon: RuntimeIconName,
    val selected: Boolean = false,
    val onClick: () -> Unit,
)

/** Scrollable navigation with a pinned footer. The adaptive shell owns drawer expansion. */
@Composable
fun RuntimeNavigationRail(
    header: List<RuntimeRailItem>,
    items: List<RuntimeRailItem>,
    footer: List<RuntimeRailItem>,
    modifier: Modifier = Modifier,
) {
    val railItems = header + items
    val colors = RuntimeTheme.colorScheme
    Column(
        modifier.fillMaxWidth().fillMaxHeight().windowInsetsPadding(runtimeRailInsets),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        GlassNavigationRail(
            items = railItems.map { item ->
                GlassNavigationItem(icon = runtimeIconImageVector(item.icon), contentDescription = item.label)
            },
            selectedIndex = railItems.indexOfFirst { it.selected },
            onSelect = { railItems[it].onClick() },
            backdrop = null,
            modifier = Modifier.weight(1f),
            state = rememberGlassNavigationRailState(initialExpanded = true),
            showToggle = false,
            defaultWindowInsetsPadding = false,
            expandedWidth = RailPanelWidth,
            selectedColor = colors.primary,
        )
        Column(
            Modifier.padding(start = GlassNavigationRailDefaults.Margin, bottom = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            footer.forEach { item ->
                RuntimeTooltip(text = item.label) {
                    GlassIconButton(
                        onClick = item.onClick,
                        size = 40.dp,
                        modifier = Modifier.semantics {
                            role = Role.Tab
                            contentDescription = item.label
                            selected = item.selected
                        },
                    ) {
                        RuntimeIcon(
                            name = item.icon,
                            modifier = Modifier.size(20.dp),
                            tint = if (item.selected) colors.primary else colors.onSurface,
                        )
                    }
                }
            }
        }
    }
}
