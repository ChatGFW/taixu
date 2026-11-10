package top.wkbin.taixu.ui.components

import top.wkbin.taixu.ui.miuix.RuntimeSpacing
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.rememberLazyListState
import top.wkbin.taixu.ui.miuix.RuntimeTheme
import top.wkbin.taixu.ui.miuix.Scaffold
import top.wkbin.taixu.ui.miuix.TopAppBarDefaults
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.VerticalScrollBar
import top.yukonga.miuix.kmp.basic.rememberScrollBarAdapter
import top.yukonga.miuix.kmp.interfaces.ExperimentalScrollBarApi
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Shared large-title list shell, including centered wide layouts and keyboard-safe scrolling. */
@OptIn(ExperimentalScrollBarApi::class)
@Composable
fun RuntimeSettingsScaffold(
    title: String,
    onBack: () -> Unit,
    itemSpacing: Dp = RuntimeSpacing.cardGap,
    statusText: String? = null,
    actions: @Composable RowScope.() -> Unit = {},
    snackbarHost: @Composable () -> Unit = {},
    modifier: Modifier = Modifier,
    isRefreshing: Boolean = false,
    onRefresh: (() -> Unit)? = null,
    content: LazyListScope.() -> Unit,
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val listState = rememberLazyListState()
    // Scaffold owns the frosted backdrop; TopBar reads LocalRuntimeBackdrop for a transparent fill.
    Scaffold(
        modifier = modifier,
        containerColor = RuntimeTheme.colorScheme.surface,
        topBar = {
            RuntimeTopBar(title, onBack, scrollBehavior, actions = actions)
        },
        snackbarHost = snackbarHost,
    ) { padding ->
        val direction = LocalLayoutDirection.current
        val side = runtimeContentPadding()
        val list: @Composable () -> Unit = {
            Box(Modifier.fillMaxSize()) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize()
                        .padding(start = padding.calculateStartPadding(direction), end = padding.calculateEndPadding(direction))
                        .consumeWindowInsets(padding)
                        .imePadding()
                        .runtimeListScrollEffects(scrollBehavior.nestedScrollConnection),
                    contentPadding = PaddingValues(
                        start = side.calculateStartPadding(direction),
                        end = side.calculateEndPadding(direction),
                        top = padding.calculateTopPadding() + RuntimeSpacing.pageTop,
                        bottom = padding.calculateBottomPadding() + RuntimeSpacing.pageBottom,
                    ),
                    verticalArrangement = Arrangement.spacedBy(itemSpacing),
                    content = {
                        if (!statusText.isNullOrBlank()) item("runtime-settings-status") {
                            Text(statusText, Modifier.padding(horizontal = 16.dp), style = RuntimeTheme.typography.bodySmall,
                                color = RuntimeTheme.colorScheme.onSurfaceVariant)
                        }
                        content()
                    },
                )
                VerticalScrollBar(
                    adapter = rememberScrollBarAdapter(listState),
                    modifier = Modifier.align(Alignment.CenterEnd),
                )
            }
        }
        if (onRefresh != null) {
            RuntimePullToRefresh(
                isRefreshing = isRefreshing,
                onRefresh = onRefresh,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(top = padding.calculateTopPadding()),
                topAppBarScrollBehavior = scrollBehavior,
                content = list,
            )
        } else {
            list()
        }
    }
}

/**
 * miuix list feel: edge haptic, spring overscroll, then the top bar's collapse connection.
 * The connection must sit inside the overscroll so pulling down re-expands the large title
 * before any rubber-banding starts.
 */
@Composable
fun Modifier.runtimeListScrollEffects(
    topBarConnection: androidx.compose.ui.input.nestedscroll.NestedScrollConnection? = null,
): Modifier = this
    .scrollEndHaptic()
    .overScrollVertical()
    .then(if (topBarConnection != null) Modifier.nestedScroll(topBarConnection) else Modifier)
