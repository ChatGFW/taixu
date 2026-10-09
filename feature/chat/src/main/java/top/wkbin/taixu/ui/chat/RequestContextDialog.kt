package top.wkbin.taixu.ui.chat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import top.wkbin.taixu.feature.chat.R
import top.wkbin.taixu.harness.diagnostics.RequestContextSnapshot
import top.wkbin.taixu.ui.components.RuntimeAlertDialog
import top.wkbin.taixu.ui.components.RuntimeTextButton
import java.text.DateFormat
import java.util.Date

@Composable
fun RequestContextDialog(requests: List<RequestContextSnapshot>, onDismiss: () -> Unit) {
    var selected by remember(requests) { mutableStateOf(requests.lastIndex.coerceAtLeast(0)) }
    val snapshot = requests.getOrNull(selected)
    var query by remember { mutableStateOf("") }
    RuntimeAlertDialog(
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
                Text(stringResource(R.string.chat_request_context_notice), style = MaterialTheme.typography.bodySmall)
                if (snapshot == null) {
                    Text(stringResource(R.string.chat_request_context_empty))
                } else {
                    requests.forEachIndexed { index, request ->
                        RuntimeTextButton(onClick = { selected = index }, modifier = Modifier.fillMaxWidth()) {
                            Text(
                                stringResource(
                                    R.string.chat_request_context_attempt,
                                    request.round, request.attempt,
                                    DateFormat.getTimeInstance(DateFormat.MEDIUM).format(Date(request.capturedAt)),
                                ),
                                maxLines = 1, overflow = TextOverflow.Ellipsis,
                                color = if (selected == index) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    Text(
                        stringResource(R.string.chat_request_context_metadata, snapshot.protocol, snapshot.bodyBytes),
                        style = MaterialTheme.typography.labelMedium,
                    )
                    if (snapshot.previewTruncated) {
                        Text(
                            stringResource(R.string.chat_request_context_clipped),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    OutlinedTextField(
                        value = query, onValueChange = { query = it },
                        label = { Text(stringResource(R.string.chat_request_context_search)) },
                        singleLine = true, modifier = Modifier.fillMaxWidth(),
                    )
                    val matches = remember(snapshot, query) {
                        snapshot.sections.filter { it.label.contains(query, true) || it.preview.contains(query, true) }
                    }
                    if (matches.isEmpty()) Text(stringResource(R.string.chat_request_context_no_matches))
                    matches.forEach { section ->
                        var expanded by remember(snapshot, section.label) { mutableStateOf(false) }
                        Surface(color = MaterialTheme.colorScheme.surfaceContainerHighest, shape = MaterialTheme.shapes.medium) {
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
                                    Text(section.preview, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
                                }
                            }
                        }
                    }
                }
            }
        },
    )
}
