package top.wkbin.taixu.ui.settings

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import top.wkbin.taixu.ui.miuix.RuntimeTheme
import top.yukonga.miuix.kmp.basic.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import top.wkbin.taixu.ui.components.RuntimeSelectionSheet

/** 只读查看默认系统提示词。develop 的弹窗清理改调用此表，但漏了定义。 */
@Composable
internal fun AgentPromptSheet(
    title: String,
    prompt: String,
    onDismiss: () -> Unit,
) {
    RuntimeSelectionSheet(title = title, onDismissRequest = onDismiss) {
        Text(
            text = prompt,
            modifier = Modifier.fillMaxWidth().heightIn(max = 420.dp).verticalScroll(rememberScrollState()),
            style = RuntimeTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
            color = RuntimeTheme.colorScheme.onSurfaceVariant,
        )
    }
}
