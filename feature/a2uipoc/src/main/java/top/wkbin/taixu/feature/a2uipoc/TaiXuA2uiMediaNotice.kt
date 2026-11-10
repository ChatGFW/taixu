package top.wkbin.taixu.feature.a2uipoc

import androidx.compose.foundation.layout.padding
import top.wkbin.taixu.ui.miuix.RuntimeTheme
import top.wkbin.taixu.ui.miuix.Surface
import top.yukonga.miuix.kmp.basic.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

@Composable
internal fun TaiXuA2uiMediaNotice(text: String, modifier: Modifier = Modifier) {
    Surface(
        shape = RuntimeTheme.shapes.small,
        color = RuntimeTheme.colorScheme.surfaceContainerHigh,
        modifier = modifier,
    ) {
        Text(
            text,
            modifier = Modifier.padding(12.dp),
            style = RuntimeTheme.typography.bodySmall,
            color = RuntimeTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
