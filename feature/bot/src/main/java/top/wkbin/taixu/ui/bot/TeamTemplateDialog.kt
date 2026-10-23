package top.wkbin.taixu.ui.bot

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import top.wkbin.taixu.feature.bot.R
import top.wkbin.taixu.harness.bot.market.GroupTeamTemplates
import top.wkbin.taixu.ui.components.*

@Composable
internal fun TeamTemplateDialog(existingGroupName: String? = null, onDismiss: () -> Unit,
    onImport: suspend (String, String) -> Unit) {
    var selected by rememberSaveable { mutableStateOf(GroupTeamTemplates.all.first().id) }
    var name by rememberSaveable { mutableStateOf(existingGroupName ?: GroupTeamTemplates.all.first().name) }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val template = GroupTeamTemplates.all.single { it.id == selected }
    RuntimeAlertDialog(
        onDismissRequest = { if (!saving) onDismiss() },
        title = { Text(stringResource(R.string.fbot_team_import)) },
        text = {
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(if (existingGroupName == null) R.string.fbot_template_hint else R.string.fbot_template_existing_hint))
                GroupTeamTemplates.all.forEach { candidate ->
                    RuntimeCard(modifier = Modifier.fillMaxWidth(), onClick = {
                        if (!saving) { selected = candidate.id; if (existingGroupName == null) name = candidate.name }
                    }) {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text((if (selected == candidate.id) "✓ " else "") + candidate.name, style = MaterialTheme.typography.titleSmall)
                            Text(candidate.description, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
                if (existingGroupName == null) RuntimeFilledTextField(value = name, onValueChange = { name = it.take(40) },
                    label = { Text(stringResource(R.string.fbot_group_name)) }, modifier = Modifier.fillMaxWidth(), singleLine = true, enabled = !saving)
                Text(stringResource(R.string.fbot_template_members), style = MaterialTheme.typography.titleSmall)
                template.members.forEach { member ->
                    Text("${templateMemberName(member.key)} → ${templateMemberName(member.supervisorKey ?: template.adminKey)}" +
                        if (member.key == template.adminKey) " (${stringResource(R.string.fbot_team_root)})" else "",
                        style = MaterialTheme.typography.bodySmall)
                }
                template.rules.forEach { rule ->
                    Text("${templateMemberName(rule.authorId)} → ${templateMemberName(rule.reviewerId)} → ${templateMemberName(rule.executorId)}",
                        style = MaterialTheme.typography.bodyMedium)
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = { RuntimeButton(enabled = !saving && name.isNotBlank(), onClick = {
            saving = true
            scope.launch {
                try { onImport(selected, name); onDismiss() } catch (failure: Exception) { error = failure.message }
                finally { saving = false }
            }
        }) { Text(stringResource(if (saving) R.string.fbot_template_importing else R.string.fbot_team_import)) } },
        dismissButton = { RuntimeTextButton(enabled = !saving, onClick = onDismiss) { Text(stringResource(R.string.fbot_cancel)) } },
    )
}

private fun templateMemberName(key: String): String = mapOf("taizi" to "太子", "zhongshu" to "中书省", "menxia" to "门下省",
    "shangshu" to "尚书省", "libu-hr" to "吏部", "hubu" to "户部", "libu" to "礼部", "bingbu" to "兵部",
    "xingbu" to "刑部", "gongbu" to "工部", "chief" to "交付主控", "product" to "产品", "review" to "测试审议",
    "lead" to "研发协调", "frontend" to "前端", "backend" to "后端").getValue(key)
