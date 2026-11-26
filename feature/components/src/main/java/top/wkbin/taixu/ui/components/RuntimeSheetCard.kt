package top.wkbin.taixu.ui.components

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import top.wkbin.taixu.ui.miuix.LocalGlassSheet
import top.wkbin.taixu.ui.miuix.RuntimeTheme
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.glass.GlassCard

@Composable
fun RuntimeSheetCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val padding = PaddingValues(0.dp)
    if (LocalGlassSheet.current) {
        GlassCard(
            modifier = modifier,
            cornerRadius = CardDefaults.CornerRadius,
            insideMargin = padding,
            content = content,
        )
    } else {
        RuntimeCard(
            modifier = modifier,
            containerColor = RuntimeTheme.colorScheme.surfaceContainerHigh,
            contentPadding = padding,
            content = content,
        )
    }
}
