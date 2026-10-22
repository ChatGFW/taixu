package top.wkbin.taixu.ui.bot

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import top.wkbin.taixu.core.model.bot.Bot
import top.wkbin.taixu.core.model.bot.GroupTeam
import top.wkbin.taixu.feature.bot.R
import top.wkbin.taixu.ui.components.RuntimeAlertDialog
import top.wkbin.taixu.ui.components.RuntimeButton
import top.wkbin.taixu.ui.components.RuntimeCard
import top.wkbin.taixu.ui.components.RuntimeTextButton

@Composable
internal fun GroupTeamDialog(team: GroupTeam, members: List<Bot>, onDismiss: () -> Unit,
    onSave: suspend (Map<String, String>) -> Unit) {
    var draft by remember { mutableStateOf(team.supervisors) }
    var selecting by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val current = team.copy(supervisors = draft)
    val memberById = members.associateBy { it.id }
    RuntimeAlertDialog(
        onDismissRequest = { if (!saving) onDismiss() },
        title = { Text(stringResource(R.string.fbot_team_title)) },
        text = {
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.fbot_team_hint), style = MaterialTheme.typography.bodySmall)
                members.sortedBy { if (it.id == team.adminBotId) 0 else 1 }.forEach { member ->
                    RuntimeCard(modifier = Modifier.fillMaxWidth()) {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(member.name, style = MaterialTheme.typography.titleSmall)
                            if (member.id == team.adminBotId) Text(stringResource(R.string.fbot_team_root))
                            else RuntimeTextButton(onClick = { selecting = member.id }, enabled = !saving) {
                                Text(stringResource(R.string.fbot_team_supervisor,
                                    memberById[current.supervisorOf(member.id)]?.name.orEmpty()))
                            }
                        }
                    }
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            RuntimeButton(enabled = !saving, onClick = {
                saving = true
                scope.launch {
                    runCatching { current.validate(); onSave(draft) }
                        .onSuccess { onDismiss() }.onFailure { error = it.message }
                    saving = false
                }
            }) { Text(stringResource(R.string.fbot_save)) }
        },
        dismissButton = { RuntimeTextButton(onClick = onDismiss, enabled = !saving) { Text(stringResource(R.string.fbot_cancel)) } },
    )
    selecting?.let { botId ->
        RuntimeAlertDialog(
            onDismissRequest = { selecting = null },
            title = { Text(stringResource(R.string.fbot_team_select, memberById[botId]?.name.orEmpty())) },
            text = {
                Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
                    members.filter { candidate ->
                        candidate.id != botId && runCatching {
                            current.copy(supervisors = draft + (botId to candidate.id)).validate()
                        }.isSuccess
                    }.forEach { candidate ->
                        RuntimeTextButton(onClick = {
                            draft = draft + (botId to candidate.id)
                            error = null
                            selecting = null
                        }) { Text(candidate.name) }
                    }
                }
            },
            confirmButton = { RuntimeTextButton(onClick = { selecting = null }) { Text(stringResource(R.string.fbot_cancel)) } },
        )
    }
}
