package top.wkbin.taixu.ui.chat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import top.wkbin.taixu.feature.chat.R
import top.wkbin.taixu.ui.components.RuntimeCard
import top.wkbin.taixu.ui.components.RuntimeIcon
import top.wkbin.taixu.ui.components.RuntimeIconName
import top.wkbin.taixu.ui.components.RuntimeSegmentedControl
import top.wkbin.taixu.ui.miuix.RuntimeTheme
import top.yukonga.miuix.kmp.basic.Text

internal enum class ChatCompanionTab { Terminal, Browser, Twin }

private data class CompanionTabSpec(
    val tab: ChatCompanionTab,
    val icon: RuntimeIconName,
    val label: String,
)

/**
 * 伴随面板：顶部分段 Tab 固定；终端/浏览器/分身内容常驻不销毁，切换只改可见性。
 * 分身 TextureView 因此不会在切 Tab 时反复 attach → 黑屏闪一下。
 */
@Composable
internal fun ChatCompanionPane(
    selectedTab: ChatCompanionTab,
    onSelectTab: (ChatCompanionTab) -> Unit,
    workspace: String,
    terminal: @Composable (String) -> Unit,
    browser: (@Composable ((() -> Unit)?) -> Unit)?,
    twin: (@Composable () -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val terminalLabel = stringResource(R.string.chat_pane_terminal)
    val browserLabel = stringResource(R.string.chat_pane_browser)
    val twinLabel = stringResource(R.string.chat_pane_twin)
    val tabs = remember(browser != null, twin != null, terminalLabel, browserLabel, twinLabel) {
        buildList {
            add(CompanionTabSpec(ChatCompanionTab.Terminal, RuntimeIconName.Terminal, terminalLabel))
            if (browser != null) {
                add(CompanionTabSpec(ChatCompanionTab.Browser, RuntimeIconName.Globe, browserLabel))
            }
            if (twin != null) {
                add(CompanionTabSpec(ChatCompanionTab.Twin, RuntimeIconName.Phone, twinLabel))
            }
        }
    }
    val selectedIndex = tabs.indexOfFirst { it.tab == selectedTab }.coerceAtLeast(0)

    RuntimeCard(
        modifier = modifier,
        containerColor = RuntimeTheme.colorScheme.surfaceContainerLow,
        contentPadding = PaddingValues(0.dp),
    ) {
        Column(Modifier.fillMaxSize()) {
            if (tabs.size > 1) {
                RuntimeSegmentedControl(
                    items = tabs.map { it.icon to it.label },
                    selectedIndex = selectedIndex,
                    onSelect = { onSelectTab(tabs[it.coerceIn(0, tabs.lastIndex)].tab) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                ) { item, active ->
                    val tint =
                        if (active) RuntimeTheme.colorScheme.onPrimary
                        else RuntimeTheme.colorScheme.onSurfaceVariant
                    Row(
                        Modifier.semantics { selected = active },
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RuntimeIcon(item.first, Modifier.size(18.dp), tint)
                        Text(
                            item.second,
                            style = RuntimeTheme.typography.labelLarge,
                            fontWeight = FontWeight.Medium,
                            color = tint,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            ) {
                CompanionKeepAlive(visible = selectedTab == ChatCompanionTab.Terminal) {
                    terminal(workspace)
                }
                if (browser != null) {
                    CompanionKeepAlive(visible = selectedTab == ChatCompanionTab.Browser) {
                        browser(null)
                    }
                }
                if (twin != null) {
                    CompanionKeepAlive(visible = selectedTab == ChatCompanionTab.Twin) {
                        twin()
                    }
                }
            }
        }
    }
}

/** 内容常驻组合；不可见时透明并移出命中区，避免切走时销毁 AndroidView。 */
@Composable
private fun CompanionKeepAlive(
    visible: Boolean,
    content: @Composable () -> Unit,
) {
    Box(
        Modifier
            .fillMaxSize()
            .graphicsLayer {
                alpha = if (visible) 1f else 0f
                if (!visible) translationY = size.height
            },
    ) {
        content()
    }
}
