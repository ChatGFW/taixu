package top.wkbin.taixu.ui.chat

import androidx.compose.foundation.layout.*
import top.wkbin.taixu.ui.miuix.RuntimeTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import top.wkbin.taixu.feature.chat.R
import top.wkbin.taixu.harness.AssistantText
import top.wkbin.taixu.ui.components.*

@Composable
internal fun AssistantMessageFooter(message: AssistantText, prominent: Boolean, showRegenerate: Boolean,
    onRegenerate: () -> Unit, onCreateBranch: () -> Unit, onCopy: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(top = 2.dp, bottom = if (prominent) 4.dp else 0.dp)) {
        AssistantNerdLine(message)
        Row(Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            RuntimeIconButton(onClick = onCopy, modifier = Modifier.size(32.dp),
                contentDescription = stringResource(R.string.chat_copy)) {
                RuntimeIcon(RuntimeIconName.Copy, Modifier.size(16.dp), RuntimeTheme.colorScheme.onSurfaceVariant)
            }
            RuntimeIconButton(onClick = onCreateBranch, modifier = Modifier.size(32.dp),
                contentDescription = stringResource(R.string.chat_branch_from_here)) {
                RuntimeIcon(RuntimeIconName.GitBranch, Modifier.size(16.dp), RuntimeTheme.colorScheme.onSurfaceVariant)
            }
            if (prominent) {
                if (showRegenerate) RuntimeIconButton(onClick = onRegenerate, modifier = Modifier.size(32.dp),
                    contentDescription = stringResource(R.string.chat_regenerate)) {
                    RuntimeIcon(RuntimeIconName.Refresh, Modifier.size(16.dp), RuntimeTheme.colorScheme.onSurfaceVariant)
                }
                MessageFeedbackAction(message.id)
            }
        }
    }
}
