package top.wkbin.taixu.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import top.yukonga.miuix.kmp.basic.SearchBarDefaults

/**
 * 太墟 · 胶囊搜索栏：直接委托 miuix [top.yukonga.miuix.kmp.basic.InputField]
 *（自带 Search / SearchCleanup 图标与清空动效）。
 */
@Composable
fun RuntimeSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "搜索...",
    leadingIcon: (@Composable () -> Unit)? = null,
    onSearch: ((String) -> Unit)? = null,
    onClear: (() -> Unit)? = null,
    enabled: Boolean = true,
) {
    // InputField 内建清空；若调用方另需 onClear 副作用，在查询被清空时补一次回调。
    var previousQuery by remember { mutableStateOf(query) }
    LaunchedEffect(query) {
        if (previousQuery.isNotEmpty() && query.isEmpty()) onClear?.invoke()
        previousQuery = query
    }
    top.yukonga.miuix.kmp.basic.InputField(
        query = query,
        onQueryChange = onQueryChange,
        onSearch = { onSearch?.invoke(it) },
        expanded = false,
        onExpandedChange = {},
        modifier = modifier,
        label = placeholder,
        enabled = enabled,
        leadingIcon = leadingIcon?.let { icon ->
            {
                Box(Modifier.padding(
                    start = SearchBarDefaults.LeadingIconStartPadding,
                    end = SearchBarDefaults.LeadingIconEndPadding,
                )) { icon() }
            }
        },
    )
}
