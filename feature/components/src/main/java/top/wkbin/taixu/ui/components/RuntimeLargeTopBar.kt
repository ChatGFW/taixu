package top.wkbin.taixu.ui.components

import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.size
import top.yukonga.miuix.kmp.glass.GlassTopAppBar
import top.yukonga.miuix.kmp.glass.GlassTopAppBarDefaults
import top.wkbin.taixu.ui.miuix.RuntimeTheme
import top.wkbin.taixu.ui.miuix.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
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
    GlassTopAppBar(
        modifier = modifier,
        title = title,
        progressiveBlur = true,
        backdrop = LocalRuntimeChromeBackdrop.current,
        bandBrush = GlassTopAppBarDefaults.bandBrush(color ?: RuntimeTheme.colorScheme.surface),
        buttonSize = 40.dp,
        navigationIcon = {
            CompositionLocalProvider(LocalRuntimeGlassTopBar provides true) {
                RuntimeIconButton(onClick = onBack, contentDescription = stringResource(R.string.components_back)) {
                    RuntimeIcon(RuntimeIconName.Back, Modifier.size(24.dp))
                }
            }
        },
        actions = { CompositionLocalProvider(LocalRuntimeGlassTopBar provides true) { actions() } },
        scrollBehavior = scrollBehavior,
    )
}
