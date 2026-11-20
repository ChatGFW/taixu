package top.wkbin.taixu.ui.components

import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import top.wkbin.taixu.feature.components.R
import top.wkbin.taixu.ui.miuix.RuntimeTheme
import top.yukonga.miuix.kmp.glass.GlassTopAppBar
import top.yukonga.miuix.kmp.glass.GlassTopAppBarDefaults

@Composable
fun RuntimeTopBar(
    title: String,
    onBack: (() -> Unit)? = null,
    statusText: String? = null,
    modifier: Modifier = Modifier,
    color: Color? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    val openNavigation = LocalAppNavigation.current
    GlassTopAppBar(
        title = title,
        subtitle = statusText.orEmpty(),
        modifier = modifier,
        expandable = false,
        progressiveBlur = true,
        backdrop = LocalRuntimeChromeBackdrop.current,
        bandBrush = GlassTopAppBarDefaults.bandBrush(color ?: RuntimeTheme.colorScheme.surface),
        buttonSize = 40.dp,
        navigationIcon = {
            CompositionLocalProvider(LocalRuntimeGlassTopBar provides true) {
                if (onBack != null) {
                    RuntimeIconButton(onClick = onBack, contentDescription = stringResource(R.string.components_back)) {
                        RuntimeIcon(RuntimeIconName.Back, Modifier.size(24.dp))
                    }
                } else if (openNavigation != null) {
                    RuntimeIconButton(onClick = openNavigation, contentDescription = stringResource(R.string.components_open_navigation)) {
                        RuntimeIcon(RuntimeIconName.Menu, Modifier.size(24.dp))
                    }
                }
            }
        },
        actions = { CompositionLocalProvider(LocalRuntimeGlassTopBar provides true) { actions() } },
    )
}
