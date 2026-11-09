package top.wkbin.taixu.ui.chat

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import top.wkbin.taixu.ui.miuix.RuntimeTheme
import top.wkbin.taixu.ui.components.RuntimeFilledTextField
import top.wkbin.taixu.ui.miuix.Surface
import top.yukonga.miuix.kmp.basic.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import top.wkbin.taixu.feature.chat.R
import top.wkbin.taixu.harness.diagnostics.RequestContextSnapshot
import top.wkbin.taixu.harness.diagnostics.RequestContextDiff
import top.wkbin.taixu.harness.diagnostics.RequestArchiveStatus
import top.wkbin.taixu.ui.components.RuntimeTextButton
import java.text.DateFormat
import java.util.Date

@Composable
fun RequestContextDialog(requests: List<RequestContextSnapshot>, archiveStatus: RequestArchiveStatus, onDismiss: () -> Unit) {
    var selected by remember(requests) { mutableStateOf(requests.lastIndex.coerceAtLeast(0)) }
    val snapshot = requests.getOrNull(selected)
    val previous = requests.getOrNull(selected - 1)
    val diff = remember(previous, snapshot) {
        if (previous != null && snapshot != null) RequestContextDiff.between(previous, snapshot) else null
    }
    var query by remember { mutableStateOf("") }
    ChatBottomSheet(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.chat_request_context_title)) },
        confirmButton = {
            RuntimeTextButton(onClick = onDismiss) { Text(stringResource(R.string.chat_request_context_close)) }
        },
        text = {
            Column(
                Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(stringResource(R.string.chat_request_context_notice), style = RuntimeTheme.typography.bodySmall)
                Text(stringResource(when (archiveStatus) {
                    RequestArchiveStatus.MEMORY_ONLY -> R.string.chat_request_archive_memory
                    RequestArchiveStatus.LOADING -> R.string.chat_request_archive_loading
                    RequestArchiveStatus.SAVING -> R.string.chat_request_archive_saving
                    RequestArchiveStatus.SAVED -> R.string.chat_request_archive_saved
                    RequestArchiveStatus.ERROR -> R.string.chat_request_archive_error
                }), style = RuntimeTheme.typography.bodySmall,
                    color = if (archiveStatus == RequestArchiveStatus.ERROR) RuntimeTheme.colorScheme.error else RuntimeTheme.colorScheme.onSurfaceVariant)
                if (snapshot == null) {
                    Text(stringResource(R.string.chat_request_context_empty))
                } else {
                    requests.forEachIndexed { index, request ->
                        RuntimeTextButton(onClick = { selected = index }, modifier = Modifier.fillMaxWidth()) {
                            Text(
                                stringResource(
                                    R.string.chat_request_context_attempt,
                                    request.operationId.take(8), request.round, request.attempt,
                                    DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.MEDIUM).format(Date(request.capturedAt)),
                                ),
                                maxLines = 1, overflow = TextOverflow.Ellipsis,
                                color = if (selected == index) RuntimeTheme.colorScheme.primary else RuntimeTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    Text(
                        stringResource(R.string.chat_request_context_metadata, snapshot.protocol, snapshot.bodyBytes),
                        style = RuntimeTheme.typography.labelMedium,
                    )
                    if (diff != null) {
                        Text(
                            stringResource(R.string.chat_request_context_diff_summary,
                                diff.added.size, diff.removed.size, diff.changed.size, diff.unchangedCount),
                            style = RuntimeTheme.typography.labelMedium,
                        )
                        Text(stringResource(R.string.chat_request_context_diff_notice), style = RuntimeTheme.typography.bodySmall)
                        if (!diff.complete) Text(stringResource(R.string.chat_request_context_diff_partial),
                            style = RuntimeTheme.typography.bodySmall, color = RuntimeTheme.colorScheme.error)
                        if (diff.added.isNotEmpty()) Text(stringResource(R.string.chat_request_context_diff_added, diff.added.joinToString(", ")))
                        if (diff.removed.isNotEmpty()) Text(stringResource(R.string.chat_request_context_diff_removed, diff.removed.joinToString(", ")))
                        if (diff.changed.isNotEmpty()) Text(stringResource(R.string.chat_request_context_diff_changed, diff.changed.joinToString(", ")))
                    }
                    if (snapshot.previewTruncated) {
                        Text(
                            stringResource(R.string.chat_request_context_clipped),
                            color = RuntimeTheme.colorScheme.onSurfaceVariant,
                            style = RuntimeTheme.typography.bodySmall,
                        )
                    }
                    RuntimeFilledTextField(
                        value = query, onValueChange = { query = it },
                        labelText = stringResource(R.string.chat_request_context_search),
                        singleLine = true, modifier = Modifier.fillMaxWidth(),
                    )
                    val matches = remember(snapshot, query) {
                        snapshot.sections.filter { it.label.contains(query, true) || it.preview.contains(query, true) }
                    }
                    if (matches.isEmpty()) Text(stringResource(R.string.chat_request_context_no_matches))
                    matches.forEach { section ->
                        var expanded by remember(snapshot, section.label) { mutableStateOf(false) }
                        Surface(color = RuntimeTheme.colorScheme.surfaceContainerHighest, shape = RuntimeTheme.shapes.medium) {
                            Column(Modifier.fillMaxWidth().padding(8.dp)) {
                                RuntimeTextButton(onClick = { expanded = !expanded }, modifier = Modifier.fillMaxWidth()) {
                                    Text(
                                        stringResource(
                                            if (expanded) R.string.chat_request_context_collapse else R.string.chat_request_context_expand,
                                            section.label,
                                        ),
                                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                                    )
                                }
                                if (expanded) SelectionContainer {
                                    Column {
                                        val previousSection = previous?.sections?.firstOrNull { it.label == section.label }
                                            ?.takeIf { section.label in diff?.changed.orEmpty() }
                                        if (previousSection != null) {
                                            Text(stringResource(R.string.chat_request_context_previous), style = RuntimeTheme.typography.labelMedium)
                                            Text(previousSection.preview, style = RuntimeTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
                                            if (previousSection.previewTruncated) Text(stringResource(R.string.chat_request_context_clipped),
                                                style = RuntimeTheme.typography.bodySmall)
                                            Text(stringResource(R.string.chat_request_context_current), style = RuntimeTheme.typography.labelMedium)
                                        }
                                        Text(section.preview, style = RuntimeTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
                                        if (section.previewTruncated) Text(stringResource(R.string.chat_request_context_clipped),
                                            style = RuntimeTheme.typography.bodySmall)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
    )
}
