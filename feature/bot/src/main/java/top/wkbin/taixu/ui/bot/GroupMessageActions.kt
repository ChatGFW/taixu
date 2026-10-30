package top.wkbin.taixu.ui.bot

import android.content.ClipData
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import top.wkbin.taixu.core.model.bot.GroupMessage
import top.wkbin.taixu.feature.bot.R
import top.wkbin.taixu.ui.components.*

@Composable
internal fun GroupMessageActions(message: GroupMessage) {
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        RuntimeIconButton(onClick = { scope.launch {
            clipboard.setClipEntry(ClipEntry(ClipData.newPlainText("taixu-group-message", message.content)))
        } }, modifier = Modifier.size(32.dp), contentDescription = stringResource(R.string.fbot_copy)) {
            RuntimeIcon(RuntimeIconName.Copy, Modifier.size(16.dp), MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
