package top.wkbin.taixu.ui.components

import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.size
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.wkbin.taixu.ui.miuix.RuntimeTheme
import top.wkbin.taixu.ui.miuix.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import top.wkbin.taixu.feature.components.R

/** Scroll-aware variant for spacious settings and personalization pages. */
@Composable
fun RuntimeTopBar(
    title: String,
    onBack: () -> Unit,
    scrollBehavior: TopAppBarScrollBehavior,
    modifier: Modifier = Modifier,
    color: Color = RuntimeTheme.colorScheme.surface,
    actions: @Composable RowScope.() -> Unit = {},
) {
    TopAppBar(
        modifier = modifier,
        title = title,
        navigationIcon = {
            RuntimeIconButton(onClick = onBack, contentDescription = stringResource(R.string.components_back)) {
                RuntimeIcon(RuntimeIconName.Back, Modifier.size(24.dp))
            }
        },
        actions = actions,
        color = color,
        scrollBehavior = scrollBehavior,
    )
}
