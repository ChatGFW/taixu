package top.wkbin.taixu.ui.components

import top.wkbin.taixu.ui.miuix.RuntimeSpacing
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import top.wkbin.taixu.ui.miuix.RuntimeTheme
import top.wkbin.taixu.ui.miuix.Scaffold
import top.wkbin.taixu.ui.miuix.TopAppBarDefaults
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Shared large-title list shell, including centered wide layouts and keyboard-safe scrolling. */
@Composable
fun RuntimeSettingsScaffold(
    title: String,
    onBack: () -> Unit,
    itemSpacing: Dp = RuntimeSpacing.cardGap,
    statusText: String? = null,
    actions: @Composable RowScope.() -> Unit = {},
    snackbarHost: @Composable () -> Unit = {},
    modifier: Modifier = Modifier,
    content: LazyListScope.() -> Unit,
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val backdrop = rememberRuntimeBackdrop()
    Scaffold(
        modifier = modifier,
        containerColor = RuntimeTheme.colorScheme.surface,
        topBar = {
            RuntimeBlurredBar(backdrop) {
                RuntimeTopBar(title, onBack, scrollBehavior, color = runtimeBarColor(backdrop), actions = actions)
            }
        },
        snackbarHost = snackbarHost,
    ) { padding ->
        val direction = LocalLayoutDirection.current
        val side = runtimeContentPadding()
        LazyColumn(
            modifier = Modifier.fillMaxSize().runtimeBackdropSource(backdrop)
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
