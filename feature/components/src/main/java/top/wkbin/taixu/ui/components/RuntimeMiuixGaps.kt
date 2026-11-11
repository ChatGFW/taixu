package top.wkbin.taixu.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import top.wkbin.taixu.ui.miuix.RuntimeTheme
import top.wkbin.taixu.ui.miuix.TopAppBarScrollBehavior
import top.yukonga.miuix.kmp.basic.FloatingToolbar
import top.yukonga.miuix.kmp.basic.FloatingToolbarDefaults
import top.yukonga.miuix.kmp.basic.InputField
import top.yukonga.miuix.kmp.basic.NumberPicker
import top.yukonga.miuix.kmp.basic.NumberPickerDefaults
import top.yukonga.miuix.kmp.basic.PullToRefresh
import top.yukonga.miuix.kmp.basic.SearchBar
import top.yukonga.miuix.kmp.basic.SearchBarDefaults
import top.yukonga.miuix.kmp.basic.TooltipBox
import top.yukonga.miuix.kmp.basic.rememberPullToRefreshState
import top.yukonga.miuix.kmp.basic.rememberScrollBarAdapter
import top.yukonga.miuix.kmp.basic.VerticalScrollBar
import top.yukonga.miuix.kmp.interfaces.ExperimentalScrollBarApi

/** miuix 下拉刷新容器。 */
@Composable
fun RuntimePullToRefresh(
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    topAppBarScrollBehavior: TopAppBarScrollBehavior? = null,
    content: @Composable () -> Unit,
) {
    PullToRefresh(
        isRefreshing = isRefreshing,
        onRefresh = onRefresh,
        modifier = modifier,
        pullToRefreshState = rememberPullToRefreshState(),
        contentPadding = contentPadding,
        topAppBarScrollBehavior = topAppBarScrollBehavior,
        color = RuntimeTheme.colorScheme.primary,
        content = content,
    )
}

/**
 * miuix 浮动工具条外壳（聊天顶栏胶囊等）。
 *
 * 禁止在这里套 [RuntimeBlurredBar]：聊天胶囊画在 Scaffold 的
 * `layerBackdrop` 内容树内，对同一 backdrop 再 `textureBlur` 会在
 * RenderThread 上 SIGSEGV（采样自身录制层）。毛玻璃只留给 Scaffold topBar 槽。
 */
@Composable
fun RuntimeFloatingToolbar(
    modifier: Modifier = Modifier,
    color: Color = RuntimeTheme.colorScheme.surfaceContainer,
    cornerRadius: Dp = FloatingToolbarDefaults.CornerRadius,
    content: @Composable () -> Unit,
) {
    FloatingToolbar(
        modifier = modifier,
        color = color,
        cornerRadius = cornerRadius,
        shadowElevation = 6.dp,
        outSidePadding = PaddingValues(0.dp),
        content = content,
    )
}

/** miuix 滚轮数字选择。 */
@Composable
fun RuntimeNumberPicker(
    value: Int,
    onValueChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    range: IntRange,
    enabled: Boolean = true,
    label: (Int) -> String = { it.toString() },
    visibleItemCount: Int = 5,
) {
    NumberPicker(
        value = value.coerceIn(range.first, range.last),
        onValueChange = onValueChange,
        modifier = modifier,
        enabled = enabled,
        range = range,
        label = label,
        visibleItemCount = visibleItemCount,
        colors = NumberPickerDefaults.colors(),
    )
}

/**
 * 可展开搜索壳：上方 InputField，展开后展示 [content]（结果列表）。
 */
@Composable
fun RuntimeExpandableSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "搜索...",
    outsideEndAction: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    SearchBar(
        inputField = {
            InputField(
                query = query,
                onQueryChange = {
                    onQueryChange(it)
                    if (!expanded && it.isNotEmpty()) onExpandedChange(true)
                },
                onSearch = { onExpandedChange(true) },
                expanded = expanded,
                onExpandedChange = onExpandedChange,
                label = placeholder,
            )
        },
        onExpandedChange = onExpandedChange,
        modifier = modifier,
        insideMargin = SearchBarDefaults.InsideMargin,
        expanded = expanded,
        outsideEndAction = outsideEndAction,
        content = content,
    )
}

/** 长按 / 悬停提示；[text] 为空时不包一层。 */
@Composable
fun RuntimeTooltip(
    text: String?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable () -> Unit,
) {
    if (text.isNullOrBlank()) {
        content()
    } else {
        TooltipBox(text = text, modifier = modifier, enabled = enabled, content = content)
    }
}

/** LazyColumn + 右侧 miuix 滚动条。 */
@OptIn(ExperimentalScrollBarApi::class)
@Composable
fun RuntimeScrollableColumn(
    modifier: Modifier = Modifier,
    state: LazyListState = rememberLazyListState(),
    content: @Composable BoxScope.(LazyListState) -> Unit,
) {
    Box(modifier.fillMaxSize()) {
        content(state)
        VerticalScrollBar(
            adapter = rememberScrollBarAdapter(state),
            modifier = Modifier.align(Alignment.CenterEnd),
        )
    }
}

/** HSV 取色器。 */
@Composable
fun RuntimeColorPicker(
    color: Color,
    onColorChanged: (Color) -> Unit,
    modifier: Modifier = Modifier,
) {
    top.yukonga.miuix.kmp.basic.ColorPicker(
        color = color,
        onColorChanged = onColorChanged,
        modifier = modifier,
    )
}

/** 色相网格调色板。 */
@Composable
fun RuntimeColorPalette(
    color: Color,
    onColorChanged: (Color) -> Unit,
    modifier: Modifier = Modifier,
) {
    top.yukonga.miuix.kmp.basic.ColorPalette(
        color = color,
        onColorChanged = onColorChanged,
        modifier = modifier,
    )
}
