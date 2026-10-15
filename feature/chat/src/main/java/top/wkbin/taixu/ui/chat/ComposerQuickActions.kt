package top.wkbin.taixu.ui.chat

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.style.TextOverflow
import top.wkbin.taixu.core.database.AiModelEntity
import top.wkbin.taixu.core.model.ApprovalMode
import top.wkbin.taixu.feature.chat.R
import top.wkbin.taixu.ui.components.*

/** Common actions occupy one toolbar; the attachment menu keeps every action reachable. */
@Composable
internal fun ComposerQuickActions(
    activeModel: AiModelEntity?,
    onOpenModels: () -> Unit,
    onPickImage: () -> Unit,
    onPickFile: () -> Unit,
    approvalMode: ApprovalMode,
    onOpenApprovalModes: () -> Unit,
) {
    var showMenu by rememberSaveable { mutableStateOf(false) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        val modeLabel = stringResource(when (approvalMode) {
            ApprovalMode.FULL_ACCESS -> R.string.chat_approval_full_access
            ApprovalMode.ASSISTED -> R.string.chat_approval_assisted
            ApprovalMode.REQUEST -> R.string.chat_approval_request
        })
        val modeColor = if (approvalMode == ApprovalMode.FULL_ACCESS) MaterialTheme.colorScheme.error
            else MaterialTheme.colorScheme.onSurfaceVariant
        RuntimeTextButton(onClick = onOpenApprovalModes, modifier = Modifier.widthIn(max = 132.dp),
            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                RuntimeIcon(RuntimeIconName.Shield, Modifier.size(16.dp), modeColor)
                Text(modeLabel, style = MaterialTheme.typography.labelSmall, color = modeColor,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        Box {
            RuntimeIconButton(onClick = { showMenu = true }, modifier = Modifier.size(48.dp),
                contentDescription = stringResource(R.string.chat_add_attachment)) {
                RuntimeIcon(RuntimeIconName.Plus, Modifier.size(20.dp), MaterialTheme.colorScheme.onSurfaceVariant)
            }
            DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                DropdownMenuItem(text = { Text(stringResource(R.string.chat_select_model)) },
                    leadingIcon = { ProviderBadge(activeModel?.provider.orEmpty(), size = 20.dp) },
                    onClick = { showMenu = false; onOpenModels() })
                DropdownMenuItem(text = { Text(stringResource(R.string.chat_send_image)) },
                    leadingIcon = { RuntimeIcon(RuntimeIconName.Image) }, onClick = { showMenu = false; onPickImage() })
                DropdownMenuItem(text = { Text(stringResource(R.string.chat_send_file)) },
                    leadingIcon = { RuntimeIcon(RuntimeIconName.Document) }, onClick = { showMenu = false; onPickFile() })
            }
        }
    }
}
