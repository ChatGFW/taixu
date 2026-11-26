package top.wkbin.taixu.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import top.wkbin.taixu.feature.components.R
import top.wkbin.taixu.ui.miuix.LocalGlassSheet
import top.yukonga.miuix.kmp.basic.InputField
import top.yukonga.miuix.kmp.basic.SearchBarDefaults
import top.yukonga.miuix.kmp.basic.SearchClearButton
import top.yukonga.miuix.kmp.glass.GlassTextField

/** 胶囊搜索栏，委托 miuix InputField 处理输入和清空。 */
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
    InputField(
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

/** 抽屉搜索框使用玻璃表面的输入控件，无玻璃材质时沿用胶囊搜索栏。 */
@Composable
fun RuntimeSheetSearchBar(
    state: TextFieldState,
    placeholder: String,
    modifier: Modifier = Modifier,
) {
    if (!LocalGlassSheet.current) {
        RuntimeSearchBar(
            query = state.text.toString(),
            onQueryChange = state::setTextAndPlaceCursorAtEnd,
            modifier = modifier,
            placeholder = placeholder,
        )
        return
    }
    val keyboard = LocalSoftwareKeyboardController.current
    GlassTextField(
        state = state,
        modifier = modifier,
        label = placeholder,
        useLabelAsPlaceholder = true,
        lineLimits = TextFieldLineLimits.SingleLine,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        onKeyboardAction = { keyboard?.hide() },
        leadingIcon = { RuntimeIcon(RuntimeIconName.Search, Modifier.size(20.dp)) },
        trailingIcon = if (state.text.isNotEmpty()) {
            {
                SearchClearButton(
                    onClick = state::clearText,
                    modifier = Modifier.size(48.dp),
                    contentDescription = stringResource(R.string.components_clear_search),
                )
            }
        } else null,
    )
}
