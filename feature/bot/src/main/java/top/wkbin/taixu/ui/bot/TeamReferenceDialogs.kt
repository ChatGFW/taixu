package top.wkbin.taixu.ui.bot

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import top.wkbin.taixu.core.model.bot.*
import top.wkbin.taixu.ui.components.*

@Composable
internal fun TeamReferenceDialog(groups: List<BotMemberGroup>, members: List<Bot>, currentId: String?, onDismiss: () -> Unit,
    onSelect: suspend (String) -> Unit) {
    var selected by remember { mutableStateOf(currentId) }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val available = groups.filter { it.definition != null }
    RuntimeAlertDialog(onDismissRequest = { if (!saving) onDismiss() }, title = { Text("引用团队组") }, text = {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("群聊引用团队的成员、主控与规则。引用后，成员和架构统一在团队组中维护。", style = MaterialTheme.typography.bodySmall)
            if (available.isEmpty()) Text("请先到成员页创作团队组，或从市场导入团队模板。")
            available.forEach { group ->
                RuntimeCard(modifier = Modifier.fillMaxWidth(), onClick = { if (!saving) selected = group.id }) {
                    Text((if (selected == group.id) "✓ " else "") + group.name, style = MaterialTheme.typography.titleSmall)
                    Text("${group.memberIds.size} 名成员 · ${group.definition?.governance?.rules?.size ?: 0} 条审议规则")
                }
            }
            available.firstOrNull { it.id == selected }?.let { group ->
                group.definition?.let { TeamArchitectureGraph(it.team(group.memberIds), it.governance, members) }
            }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }
    }, confirmButton = { RuntimeButton(enabled = !saving && available.any { it.id == selected }, onClick = {
        saving = true
        scope.launch {
            try { onSelect(requireNotNull(selected)); onDismiss() } catch (failure: Exception) { error = failure.message }
            finally { saving = false }
        }
    }) { Text(if (saving) "引用中…" else "引用团队") } },
        dismissButton = { RuntimeTextButton(enabled = !saving, onClick = onDismiss) { Text("取消") } })
}

@Composable
internal fun TeamGraphDialog(team: GroupTeam, governance: GroupGovernance, members: List<Bot>, onDismiss: () -> Unit) {
    RuntimeAlertDialog(onDismissRequest = onDismiss, title = { Text("团队架构（只读）") }, text = {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("组织关系和审议规则由引用的团队组提供，请到成员页的团队组中修改。")
            TeamArchitectureGraph(team, governance, members)
        }
    }, confirmButton = { RuntimeTextButton(onClick = onDismiss) { Text("关闭") } })
}

@Composable
internal fun ProposalRecordsDialog(proposals: List<GroupProposal>, onDismiss: () -> Unit) {
    RuntimeAlertDialog(onDismissRequest = onDismiss, title = { Text("本群方案审议记录") }, text = {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (proposals.isEmpty()) Text("暂无方案记录。规则由团队组提供，各群的方案和审核结果独立保存。")
            proposals.take(40).forEach { proposal -> RuntimeCard(modifier = Modifier.fillMaxWidth()) {
                Column {
                    Text("${proposal.rule.name} · v${proposal.version} · ${proposal.decision}", style = MaterialTheme.typography.titleSmall)
                    Text(proposal.reason.ifBlank { proposal.content.take(1000) }, style = MaterialTheme.typography.bodySmall)
                }
            } }
        }
    }, confirmButton = { RuntimeTextButton(onClick = onDismiss) { Text("关闭") } })
}
