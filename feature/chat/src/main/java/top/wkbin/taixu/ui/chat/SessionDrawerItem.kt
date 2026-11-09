package top.wkbin.taixu.ui.chat

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import top.wkbin.taixu.ui.miuix.RuntimeTheme
import top.wkbin.taixu.ui.miuix.Surface
import top.yukonga.miuix.kmp.basic.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import top.wkbin.taixu.core.database.HarnessSessionEntity
import top.wkbin.taixu.core.model.SessionRunState
import top.wkbin.taixu.feature.chat.R
import top.wkbin.taixu.ui.components.RuntimeIcon
import top.wkbin.taixu.ui.components.RuntimeIconMenu
import top.wkbin.taixu.ui.components.RuntimeIconName
import top.wkbin.taixu.ui.theme.semanticColors
import top.yukonga.miuix.kmp.basic.DropdownEntry
import top.yukonga.miuix.kmp.basic.DropdownItem

/** 单条会话项：只保留标题一行；重命名/删除走长按，当前与执行中的行额外给出 ⋮。 */
@Composable
internal fun SessionDrawerItem(
    session: HarnessSessionEntity, isCurrent: Boolean, runState: SessionRunState,
    onSwitch: () -> Unit, onRename: () -> Unit, onDelete: () -> Unit,
    onHide: (() -> Unit)? = null,
    pinned: Boolean = false, onTogglePin: (() -> Unit)? = null,
) {
    val moreLabel = stringResource(R.string.chat_more_controls)
    val statusLabel = stringResource(when (runState) {
        SessionRunState.RUNNING -> R.string.chat_step_running
        SessionRunState.WAITING_APPROVAL -> R.string.chat_step_waiting
        SessionRunState.FAILED -> R.string.chat_step_failed
        else -> R.string.chat_sidebar_idle
    })
    val isBusy = runState == SessionRunState.RUNNING || runState == SessionRunState.WAITING_APPROVAL ||
        runState == SessionRunState.FAILED
    val statusColor = when (runState) {
        SessionRunState.RUNNING -> semanticColors().warning
        SessionRunState.WAITING_APPROVAL -> semanticColors().agent
        SessionRunState.FAILED -> RuntimeTheme.colorScheme.error
        else -> Color.Transparent
    }
    Surface(
        shape = RuntimeTheme.shapes.large,
        color = if (isCurrent) RuntimeTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f) else Color.Transparent,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 44.dp).clip(RuntimeTheme.shapes.large)
                .clickable(onClick = onSwitch)
                .semantics { selected = isCurrent; stateDescription = statusLabel }
                .padding(start = 12.dp, end = 10.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (pinned) {
                RuntimeIcon(RuntimeIconName.Bookmark, Modifier.size(14.dp), RuntimeTheme.colorScheme.primary)
            }
            Text(
                text = session.title,
                style = RuntimeTheme.typography.bodyMedium,
                fontWeight = FontWeight.Normal,
                color = if (isCurrent) RuntimeTheme.colorScheme.onSecondaryContainer else RuntimeTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            RuntimeIconMenu(
                entries = listOf(
                    DropdownEntry(
                        items = buildList {
                            add(DropdownItem(text = stringResource(R.string.chat_rename_session), icon = { mod -> RuntimeIcon(RuntimeIconName.Edit, mod.size(18.dp)) }, onClick = onRename))
                            if (onTogglePin != null) {
                                add(DropdownItem(text = stringResource(if (pinned) R.string.chat_unpin_session else R.string.chat_pin_session), icon = { mod -> RuntimeIcon(RuntimeIconName.Bookmark, mod.size(18.dp)) }, onClick = onTogglePin))
                            }
                            if (onHide != null) {
                                add(DropdownItem(text = stringResource(R.string.chat_hide_session), icon = { mod -> RuntimeIcon(RuntimeIconName.VisibilityOff, mod.size(18.dp)) }, onClick = onHide))
                            }
                            add(DropdownItem(text = stringResource(R.string.chat_delete_session), icon = { mod -> RuntimeIcon(RuntimeIconName.Trash, mod.size(18.dp), RuntimeTheme.colorScheme.error) }, onClick = onDelete))
                        },
                    ),
                ),
                modifier = Modifier
                    .size(24.dp)
                    .clip(RuntimeTheme.shapes.small)
                    .semantics { contentDescription = moreLabel },
            ) {
                RuntimeIcon(
                    RuntimeIconName.More,
                    Modifier.size(16.dp),
                    if (isBusy) statusColor else RuntimeTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
