package top.wkbin.taixu.ui.chat

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import top.wkbin.taixu.ui.miuix.RuntimeTheme
import top.yukonga.miuix.kmp.basic.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import top.wkbin.taixu.feature.chat.R
import top.wkbin.taixu.harness.AssistantText
import top.wkbin.taixu.harness.HarnessMessage
import top.wkbin.taixu.harness.UserMessage
import top.wkbin.taixu.ui.components.RuntimeIcon
import top.wkbin.taixu.ui.components.RuntimeIconName
import top.wkbin.taixu.ui.components.RuntimeSearchBar

private data class OutlineEntry(val id: String, val fromUser: Boolean, val summary: String, val createdAt: Long)

/**
 * 会话内大纲：把消息流折叠成「用户提问 / 助手正文」摘要列，
 * 支持即时过滤与点击定位回主列表。长会话导航入口，纯 UI，不改数据。
 */
@Composable
internal fun ChatOutlineSheet(
    messages: List<HarnessMessage>,
    onJump: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    val entries = remember(messages) { messages.mapNotNull(::outlineEntryOf) }
    val filtered = if (query.isBlank()) entries else entries.filter { it.summary.contains(query, ignoreCase = true) }
    val timeFormat = remember { SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()) }
    PlainBackdropModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.chat_outline_title), style = RuntimeTheme.typography.titleLarge)
            RuntimeSearchBar(
                query = query,
                onQueryChange = { query = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = stringResource(R.string.chat_outline_search_hint),
            )
            if (filtered.isEmpty()) {
                Text(
                    stringResource(R.string.chat_outline_empty),
                    style = RuntimeTheme.typography.bodyMedium,
                    color = RuntimeTheme.colorScheme.onSurfaceVariant,
                )
            }
            LazyColumn(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                items(filtered, key = { it.id }) { entry ->
                    Row(
                        Modifier.fillMaxWidth().clickable { onJump(entry.id) }.padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RuntimeIcon(if (entry.fromUser) RuntimeIconName.Chat else RuntimeIconName.Bot, Modifier.width(28.dp))
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(entry.summary, style = RuntimeTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Text(
                                timeFormat.format(Date(entry.createdAt)),
                                style = RuntimeTheme.typography.labelSmall,
                                color = RuntimeTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}

/** 大纲只收录用户提问与助手正文；多行取首行，超长截断。 */
private fun outlineEntryOf(message: HarnessMessage): OutlineEntry? {
    val (fromUser, text) = when (message) {
        is UserMessage -> true to message.text
        is AssistantText -> false to message.text
        else -> return null
    }
    val summary = text.lineSequence().firstOrNull { it.isNotBlank() }?.take(OUTLINE_SUMMARY_MAX) ?: return null
    return OutlineEntry(message.id, fromUser, summary, message.createdAt)
}

private const val OUTLINE_SUMMARY_MAX = 120
