package top.wkbin.taixu.ui.components

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection

/**
 * 滚动视口的水平保护（缺口 / 侧边栏），**故意不加 top**。
 * miuix Scaffold 把顶栏叠在内容上；若这里再垫 top，列表永远进不了顶栏后方，
 * [RuntimeBlurredBar] 只能采到空白底色，毛玻璃看起来像实心条。
 */
@Composable
fun runtimeScrollViewport(scaffoldPadding: PaddingValues): Modifier {
    val direction = LocalLayoutDirection.current
    return Modifier.padding(
        start = scaffoldPadding.calculateStartPadding(direction),
        end = scaffoldPadding.calculateEndPadding(direction),
    ).consumeWindowInsets(scaffoldPadding)
}

/**
 * LazyColumn / 滚动子树的 contentPadding：把 Scaffold 顶/底 inset 并进内边距，
 * 首屏避开顶栏，滚动后内容可穿过毛玻璃后方。
 */
@Composable
fun runtimeScrollContentPadding(scaffoldPadding: PaddingValues, contentPadding: PaddingValues): PaddingValues {
    val direction = LocalLayoutDirection.current
    return PaddingValues(
        start = contentPadding.calculateStartPadding(direction),
        top = contentPadding.calculateTopPadding() + scaffoldPadding.calculateTopPadding(),
        end = contentPadding.calculateEndPadding(direction),
        bottom = contentPadding.calculateBottomPadding() + scaffoldPadding.calculateBottomPadding(),
    )
}
