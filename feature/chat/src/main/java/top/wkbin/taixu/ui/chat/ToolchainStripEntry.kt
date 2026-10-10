package top.wkbin.taixu.ui.chat

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import top.wkbin.taixu.ui.components.RuntimeIconName

/**
 * 沙箱工具链入口项 —— 从 [CollapsibleChatWorkbenchStrip] 抽出，单独成文件。
 *
 * 抽出原因：architecture-policy.json 的行数棘轮只许缩减、不许增长，
 * 工具条本体不得因新增功能而膨胀。
 */
@Composable
internal fun ToolchainStripEntry(
    toolchainHighlight: Boolean,
    onOpenToolchain: () -> Unit,
) {
    StatusDivider()
    WorkbenchStatusItem(
        icon = RuntimeIconName.Wrench,
        label = if (toolchainHighlight) "工具链 •" else "工具链",
        tint = if (toolchainHighlight) Color(0xFFC62828) else MaterialTheme.colorScheme.onSurfaceVariant,
        highlight = toolchainHighlight,
        onClick = onOpenToolchain,
    )
}
