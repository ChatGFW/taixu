package top.wkbin.taixu.ui.components

import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.size
import top.yukonga.miuix.kmp.basic.TopAppBar
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
    color: Color? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    val barColor = color ?: runtimeBarColor(LocalRuntimeBackdrop.current)
    TopAppBar(
        modifier = modifier,
        title = title,
        navigationIcon = {
            RuntimeIconButton(onClick = onBack, contentDescription = stringResource(R.string.components_back)) {
                RuntimeIcon(RuntimeIconName.Back, Modifier.size(24.dp))
            }
        },
        actions = actions,
        color = barColor,
        scrollBehavior = scrollBehavior,
    )
}
