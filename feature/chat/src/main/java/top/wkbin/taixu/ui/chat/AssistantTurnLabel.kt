package top.wkbin.taixu.ui.chat

import androidx.compose.foundation.layout.*
import top.wkbin.taixu.ui.miuix.RuntimeTheme
import top.yukonga.miuix.kmp.basic.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import top.wkbin.taixu.harness.AssistantText
import top.wkbin.taixu.harness.ToolCall
import top.wkbin.taixu.harness.UserMessage
import top.wkbin.taixu.ui.components.LocalChatIdentity
import top.wkbin.taixu.ui.components.RuntimeChatIdentityAvatar

internal fun assistantTurnLabelKeys(items: List<ChatRenderItem>): Set<String> = buildSet {
    var needsLabel = true
    items.forEach { item ->
        val message = (item as? ChatRenderItem.MessageItem)?.message
        if (message is UserMessage) needsLabel = true
        else if (needsLabel && (message is AssistantText || message is ToolCall ||
                item is ToolStepsItem || item is ChatRenderItem.ReasoningItem)) {
            add(item.stableKey)
            needsLabel = false
        }
    }
}

@Composable
internal fun AssistantTurnLabel() {
    Row(Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        RuntimeChatIdentityAvatar(20.dp)
        Text(LocalChatIdentity.current.name, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis,
            style = RuntimeTheme.typography.titleSmall,
            color = RuntimeTheme.colorScheme.onSurfaceVariant)
    }
}
