package top.wkbin.taixu.ui.chat

import androidx.compose.runtime.setValue

import androidx.compose.runtime.getValue

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import top.wkbin.taixu.ui.miuix.RuntimeTheme
import top.yukonga.miuix.kmp.basic.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import top.wkbin.taixu.core.database.MessageSearchHitRow
import top.wkbin.taixu.feature.chat.R
import top.wkbin.taixu.ui.components.RuntimeCircularProgressIndicator
import top.wkbin.taixu.ui.components.RuntimeIcon
import top.wkbin.taixu.ui.components.RuntimeIconName
import top.wkbin.taixu.ui.components.RuntimeTextButton

/** Local, cross-session literal search; the currently visible query exclusively owns results. */
@Composable
internal fun SessionMessageHits(query: String, search: suspend (String) -> List<MessageSearchHitRow>, onSwitch: (String) -> Unit) {
    val scope = rememberCoroutineScope()
    val latestSearch by rememberUpdatedState(search)
    val controller = remember(scope) { SessionMessageSearch(scope, { latestSearch(it) }) }
    val state by controller.state.collectAsState()
    LaunchedEffect(query) { controller.submit(query) }
    DisposableEffect(controller) { onDispose { controller.cancel() } }
    val term = query.trim()
    val current = state.takeIf { it.query == term } ?: SessionMessageSearchState(term, loading = true)
    SessionMessageSearchContent(current, onSwitch, onRetry = { controller.submit(query) })
}

@Composable
internal fun SessionMessageSearchContent(state: SessionMessageSearchState, onSwitch: (String) -> Unit, onRetry: () -> Unit) {
    if (state.query.isBlank()) return
    val secondary = RuntimeTheme.colorScheme.onSurfaceVariant
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(stringResource(R.string.chat_drawer_message_hits), style = RuntimeTheme.typography.labelMedium,
            color = secondary, modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp))
        when {
            state.loading -> Row(Modifier.padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                RuntimeCircularProgressIndicator(Modifier.size(16.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.chat_message_search_loading), style = RuntimeTheme.typography.bodySmall, color = secondary)
            }
            state.failed -> Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.chat_message_search_failed), style = RuntimeTheme.typography.bodySmall,
                    color = RuntimeTheme.colorScheme.error, modifier = Modifier.weight(1f))
                RuntimeTextButton(onClick = onRetry) { Text(stringResource(R.string.chat_message_search_retry)) }
            }
            state.hits.isEmpty() -> Text(stringResource(R.string.chat_message_search_empty),
                style = RuntimeTheme.typography.bodySmall, color = secondary, modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp))
            else -> state.hits.forEach { hit ->
                Row(Modifier.fillMaxWidth().clickable { onSwitch(hit.sessionId) }.padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    RuntimeIcon(if (hit.isUser) RuntimeIconName.Chat else RuntimeIconName.Bot, Modifier.width(24.dp))
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        val title = hit.sessionTitle?.takeIf { it.isNotBlank() } ?: stringResource(R.string.chat_drawer_untitled)
                        Text(title, style = RuntimeTheme.typography.labelMedium, fontWeight = FontWeight.Medium,
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                        val excerpt = hit.excerpt.trim()
                        val ranges = remember(excerpt, state.query) { literalSearchRanges(excerpt, state.query) }
                        val highlight = SpanStyle(color = RuntimeTheme.colorScheme.primary, fontWeight = FontWeight.Medium)
                        val annotated = buildAnnotatedString {
                            append(excerpt)
                            ranges.forEach { addStyle(highlight, it.first, it.last + 1) }
                        }
                        Text(annotated, style = RuntimeTheme.typography.bodySmall, color = secondary,
                            maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}
