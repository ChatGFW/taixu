package top.wkbin.taixu.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import top.yukonga.miuix.kmp.basic.InputField

/**
 * 外观与 miuix [InputField] 一致的搜索入口：点击跳转专用搜索页，不在原地输入。
 */
@Composable
fun RuntimeSearchEntry(label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onClick),
    ) {
        InputField(
            query = "",
            onQueryChange = {},
            onSearch = {},
            expanded = false,
            onExpandedChange = {},
            modifier = Modifier.fillMaxWidth(),
            label = label,
            enabled = false,
        )
    }
}
