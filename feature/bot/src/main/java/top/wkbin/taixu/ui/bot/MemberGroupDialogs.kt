package top.wkbin.taixu.ui.bot

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import top.wkbin.taixu.ui.miuix.RuntimeTheme
import top.yukonga.miuix.kmp.basic.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import java.util.UUID
import top.wkbin.taixu.core.model.bot.Bot
import top.wkbin.taixu.core.model.bot.BotMemberGroup
import top.wkbin.taixu.feature.bot.R
import top.wkbin.taixu.ui.components.RuntimeAlertDialog
import top.wkbin.taixu.ui.components.RuntimeArrowPreference
import top.wkbin.taixu.ui.components.RuntimeFilledTonalButton
import top.wkbin.taixu.ui.components.RuntimeIcon
import top.wkbin.taixu.ui.components.RuntimeIconButton
import top.wkbin.taixu.ui.components.RuntimeIconName
import top.wkbin.taixu.ui.components.RuntimeTextButton
import top.wkbin.taixu.core.model.bot.TeamPublishedVersion
import top.wkbin.taixu.core.model.bot.TeamUpgradeImpact

@Composable
internal fun MemberGroupsDialog(
    groups: List<BotMemberGroup>,
    bots: List<Bot>,
    onDismiss: () -> Unit,
    onSave: suspend (BotMemberGroup) -> Unit,
    onDelete: (BotMemberGroup) -> Unit,
    initialGroupId: String? = null,
    loadImpact: suspend (String) -> List<TeamUpgradeImpact> = { emptyList() },
    loadVersions: suspend (String) -> List<TeamPublishedVersion> = { emptyList() },
    onPublish: suspend (String, Int, Set<String>) -> Int = { _, _, _ -> error("发布不可用") },
    onUpgrade: suspend (String, Int, Set<String>) -> Unit = { _, _, _ -> error("升级不可用") },
) {
    var publicationId by rememberSaveable { mutableStateOf<String?>(null) }
    var editingId by rememberSaveable { mutableStateOf<String?>(null) }
    var creatingId by rememberSaveable { mutableStateOf<String?>(null) }
    var createdAt by rememberSaveable { mutableStateOf(0L) }
    var pendingDelete by remember { mutableStateOf<BotMemberGroup?>(null) }

    var openedInitial by rememberSaveable(initialGroupId) { mutableStateOf(false) }
    androidx.compose.runtime.LaunchedEffect(groups, initialGroupId) {
        if (!openedInitial && initialGroupId != null) groups.firstOrNull { it.id == initialGroupId }?.let {
            openedInitial = true; editingId = it.id
        }
    }
    val current = groups.firstOrNull { it.id == editingId } ?: creatingId?.takeIf { it == editingId }?.let { BotMemberGroup(it, "", createdAt) }
    if (current == null) {
        RuntimeAlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(stringResource(R.string.fbot_member_groups)) },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        stringResource(R.string.fbot_member_groups_hint),
                        style = RuntimeTheme.typography.bodySmall,
                        color = RuntimeTheme.colorScheme.onSurfaceVariant,
                    )
                    if (groups.isEmpty()) {
                        Text(
                            stringResource(R.string.fbot_member_group_empty),
                            style = RuntimeTheme.typography.bodySmall,
                            color = RuntimeTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    groups.forEach { group ->
                        RuntimeArrowPreference(
                            title = group.name,
                            summary = stringResource(
                                R.string.fbot_member_group_member_count,
                                group.memberIds.size,
                            ) + " · " + if (group.publishedVersion == 0) {
                                "尚未发布"
                            } else {
                                "v${group.publishedVersion}"
                            },
                            onClick = {
                                creatingId = null
                                editingId = group.id
                            },
                            endActions = {
                                RuntimeTextButton(onClick = { publicationId = group.id }) {
                                    Text("发布")
                                }
                                RuntimeIconButton(
                                    onClick = { pendingDelete = group },
                                    contentDescription = stringResource(R.string.fbot_delete),
                                ) {
                                    RuntimeIcon(
                                        RuntimeIconName.Trash,
                                        Modifier.size(18.dp),
                                        tint = RuntimeTheme.colorScheme.error,
                                    )
                                }
                            },
                            insideMargin = PaddingValues(horizontal = 0.dp, vertical = 12.dp),
                        )
                    }
                    RuntimeFilledTonalButton(
                        onClick = {
                            createdAt = System.currentTimeMillis()
                            creatingId = UUID.randomUUID().toString()
                            editingId = creatingId
                        },
                    ) {
                        Text(stringResource(R.string.fbot_member_group_new))
                    }
                }
            },
            confirmButton = {
                RuntimeTextButton(onClick = onDismiss) { Text(stringResource(R.string.fbot_close)) }
            },
        )
    } else {
        TeamGroupEditorDialog(
            group = current,
            bots = bots,
            onDismiss = { editingId = null; creatingId = null },
            onSave = { saved ->
                onSave(saved)
                editingId = null; creatingId = null
            },
        )
    }
    groups.firstOrNull { it.id == publicationId }?.let { group ->
        TeamPublicationDialog(group, { publicationId = null }, loadImpact, loadVersions, onPublish, onUpgrade)
    }
    pendingDelete?.let { group ->
        RuntimeAlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text(stringResource(R.string.fbot_member_group_delete_title)) },
            text = { Text(stringResource(R.string.fbot_member_group_delete_confirm, group.name)) },
            confirmButton = {
                RuntimeTextButton(onClick = {
                    onDelete(group)
                    pendingDelete = null
                }) { Text(stringResource(R.string.fbot_delete)) }
            },
            dismissButton = {
                RuntimeTextButton(onClick = { pendingDelete = null }) {
                    Text(stringResource(R.string.fbot_cancel))
                }
            },
        )
    }
}
