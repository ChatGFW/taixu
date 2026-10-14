package top.wkbin.taixu.ui.chat

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
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
import top.wkbin.taixu.core.database.AiModelEntity
import top.wkbin.taixu.feature.chat.R
import top.wkbin.taixu.ui.components.*

/** Common actions occupy one toolbar; the attachment menu keeps every action reachable. */
@Composable
internal fun ComposerQuickActions(
    activeModel: AiModelEntity?, compact: Boolean,
    onOpenModels: () -> Unit, onOpenSkillsMcp: () -> Unit, onOpenReasoning: () -> Unit,
    onPickImage: () -> Unit, onPickFile: () -> Unit,
) {
    var showMenu by rememberSaveable { mutableStateOf(false) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        RuntimeIconButton(onClick = onOpenModels, modifier = Modifier.size(48.dp),
            contentDescription = stringResource(R.string.chat_select_model)) {
            ProviderBadge(activeModel?.provider.orEmpty(), size = 28.dp)
        }
        if (!compact) {
            RuntimeIconButton(onClick = onOpenSkillsMcp, modifier = Modifier.size(48.dp),
                contentDescription = stringResource(R.string.chat_menu_capabilities_short)) {
                RuntimeIcon(RuntimeIconName.Wrench, Modifier.size(20.dp), MaterialTheme.colorScheme.onSurfaceVariant)
            }
            RuntimeIconButton(onClick = onOpenReasoning, modifier = Modifier.size(48.dp),
                contentDescription = stringResource(R.string.chat_reasoning_action)) {
                RuntimeIcon(RuntimeIconName.Sparkles, Modifier.size(20.dp), MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Box {
            RuntimeIconButton(onClick = { showMenu = true }, modifier = Modifier.size(48.dp),
                contentDescription = stringResource(R.string.chat_add_attachment)) {
                RuntimeIcon(RuntimeIconName.Plus, Modifier.size(20.dp), MaterialTheme.colorScheme.onSurfaceVariant)
            }
            DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                DropdownMenuItem(text = { Text(stringResource(R.string.chat_send_image)) },
                    leadingIcon = { RuntimeIcon(RuntimeIconName.Image) }, onClick = { showMenu = false; onPickImage() })
                DropdownMenuItem(text = { Text(stringResource(R.string.chat_send_file)) },
                    leadingIcon = { RuntimeIcon(RuntimeIconName.Document) }, onClick = { showMenu = false; onPickFile() })
                DropdownMenuItem(text = { Text(stringResource(R.string.chat_menu_capabilities_short)) },
                    leadingIcon = { RuntimeIcon(RuntimeIconName.Wrench) }, onClick = { showMenu = false; onOpenSkillsMcp() })
                DropdownMenuItem(text = { Text(stringResource(R.string.chat_reasoning_action)) },
                    leadingIcon = { RuntimeIcon(RuntimeIconName.Sparkles) }, onClick = { showMenu = false; onOpenReasoning() })
            }
        }
    }
}
