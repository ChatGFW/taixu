package top.wkbin.taixu.ui.bot

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import top.wkbin.taixu.ui.miuix.RuntimeTheme
import top.yukonga.miuix.kmp.basic.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import top.wkbin.taixu.feature.bot.R
import top.wkbin.taixu.ui.components.*
import top.yukonga.miuix.kmp.basic.DropdownEntry
import top.yukonga.miuix.kmp.basic.DropdownItem

@Composable
internal fun GroupChatTopBar(
    viewMode: GroupChatViewMode,
    groupName: String,
    statusText: String,
    busy: Boolean,
    workSummary: GroupWorkSummary,
    exporting: Boolean,
    onBack: () -> Unit,
    onSwitchMode: (GroupChatViewMode) -> Unit,
    onExportDiagnostics: () -> Unit,
    onShowInfo: () -> Unit,
) {
    if (viewMode == GroupChatViewMode.STUDIO) {
        RuntimeTopBar(
            title = stringResource(R.string.fbot_cyber_studio),
            onBack = { onSwitchMode(GroupChatViewMode.CHAT) },
            statusText = groupName,
            actions = {
                RuntimeTextButton(
                    onClick = { onSwitchMode(GroupChatViewMode.CHAT) },
                    modifier = Modifier.testTag("topbar-back-to-chat-btn"),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        RuntimeIcon(RuntimeIconName.Chat, Modifier.size(16.dp))
                        Text(stringResource(R.string.fbot_cyber_studio_back_chat))
                    }
                }
                RuntimeIconButton(
                    onClick = onExportDiagnostics,
                    enabled = !exporting,
                    contentDescription = stringResource(R.string.fbot_export_diagnostics),
                ) {
                    if (exporting) {
                        RuntimeCircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    } else {
                        RuntimeIcon(RuntimeIconName.FolderDownload, Modifier.size(20.dp))
                    }
                }
                RuntimeIconButton(
                    onClick = onShowInfo,
                    contentDescription = stringResource(R.string.fbot_group_info),
                ) {
                    RuntimeIcon(RuntimeIconName.Info, Modifier.size(21.dp))
                }
            },
        )
    } else {
        RuntimeTopBar(
            title = groupName.ifBlank { stringResource(R.string.fbot_loading) },
            onBack = onBack,
            statusText = statusText,
            actions = {
                RuntimeIconButton(onClick = { onSwitchMode(GroupChatViewMode.STUDIO) },
                    modifier = Modifier.testTag("topbar-cyber-studio-btn"),
                    contentDescription = stringResource(R.string.fbot_cyber_studio)) {
                    Box {
                        RuntimeIcon(RuntimeIconName.Workspace, Modifier.size(22.dp))
                        if (busy || workSummary.attention > 0) Box(Modifier.align(Alignment.TopEnd).size(6.dp)
                            .background(if (workSummary.attention > 0) RuntimeTheme.colorScheme.error else RuntimeTheme.colorScheme.primary, CircleShape))
                    }
                }
                RuntimeIconMenu(
                    entries = listOf(
                        DropdownEntry(
                            items = listOf(
                                DropdownItem(
                                    text = stringResource(R.string.fbot_group_info),
                                    icon = { mod -> RuntimeIcon(RuntimeIconName.Info, mod.size(20.dp)) },
                                    onClick = onShowInfo,
                                ),
                                DropdownItem(
                                    text = stringResource(R.string.fbot_export_diagnostics),
                                    icon = { mod -> RuntimeIcon(RuntimeIconName.FolderDownload, mod.size(20.dp)) },
                                    onClick = { if (!exporting) onExportDiagnostics() },
                                ),
                            ),
                        ),
                    ),
                ) {
                    RuntimeIcon(RuntimeIconName.More, Modifier.size(22.dp))
                }
            },
        )
    }
}
