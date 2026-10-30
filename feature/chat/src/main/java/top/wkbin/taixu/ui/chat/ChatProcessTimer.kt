package top.wkbin.taixu.ui.chat

import android.os.SystemClock
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.util.Locale
import kotlinx.coroutines.delay
import top.wkbin.taixu.feature.chat.R

internal fun formatProcessDuration(ms: Long): String =
    String.format(Locale.US, "%.1fs", (ms.coerceAtLeast(0L) / 100) / 10.0)

/** Tick only the small time label, without recomposing the reasoning Markdown every 100 ms. */
@Composable
internal fun ChatProcessTimer(id: String, running: Boolean, startedAtMs: Long? = null, durationMs: Long? = null) {
    var anchor by rememberSaveable(id) { mutableStateOf<Long?>(null) }
    var elapsed by rememberSaveable(id) { mutableLongStateOf(0L) }
    LaunchedEffect(id, running, startedAtMs) {
        if (running) {
            if (anchor == null) anchor = SystemClock.elapsedRealtime() -
                (startedAtMs?.let { (System.currentTimeMillis() - it).coerceAtLeast(0) } ?: 0L)
            while (true) {
                elapsed = (SystemClock.elapsedRealtime() - checkNotNull(anchor)).coerceAtLeast(0)
                delay(100)
            }
        }
    }
    if (running || durationMs != null || anchor != null) Text(
        formatProcessDuration(if (running) elapsed else durationMs ?: elapsed),
        // Avoid announcing ten updates a second inside the first-response live region.
        modifier = Modifier.clearAndSetSemantics { },
        style = MaterialTheme.typography.labelSmall.copy(fontFeatureSettings = "tnum"),
        color = chatProcessColor(), maxLines = 1,
    )
}

@Composable
internal fun ChatReasoningLabel(id: String, live: Boolean, durationMs: Long?, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(if (live) R.string.chat_deep_reasoning else R.string.chat_reasoning_process),
            modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall,
            color = chatProcessColor(), maxLines = 1, overflow = TextOverflow.Ellipsis)
        ChatProcessTimer(id, live, durationMs = durationMs)
    }
}
