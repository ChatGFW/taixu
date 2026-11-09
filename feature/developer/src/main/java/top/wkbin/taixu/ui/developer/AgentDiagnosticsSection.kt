package top.wkbin.taixu.ui.developer

import androidx.compose.runtime.setValue

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import top.wkbin.taixu.ui.miuix.HorizontalDivider
import top.wkbin.taixu.ui.miuix.RuntimeTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import org.koin.compose.viewmodel.koinViewModel
import top.wkbin.taixu.core.database.AgentApprovalAuditEntity
import top.wkbin.taixu.core.database.AgentApprovalAuditRepository
import top.wkbin.taixu.core.database.AgentMessageFeedbackEntity
import top.wkbin.taixu.core.database.HarnessSessionRepository
import top.wkbin.taixu.core.database.MessageFeedbackService
import top.wkbin.taixu.ui.components.IconTile
import top.wkbin.taixu.ui.components.RuntimeCard
import top.wkbin.taixu.ui.components.RuntimeIconName
import top.wkbin.taixu.ui.components.SectionHeader
import top.wkbin.taixu.ui.components.StatusBadge
import top.wkbin.taixu.ui.components.previewOrNull
import top.wkbin.taixu.ui.components.previewStateOf
import top.wkbin.taixu.ui.developer.LocalizedText as Text
import top.wkbin.taixu.ui.components.RuntimeSwitch as Switch
import top.wkbin.taixu.ui.components.RuntimeOutlinedButton as OutlinedButton
import top.wkbin.taixu.ui.components.RuntimeTextButton as TextButton

/**
 * 智能体诊断区：本地调试日志 + 消息反馈只读视图。
 *
 * 反馈在聊天页写入（[top.wkbin.taixu.core.database.MessageFeedbackService.set]），
 * 这里补上读取面，开发者可按会话核对用户评价，不再只写不读。
 */
@Composable
internal fun AgentDiagnosticsSection(
    agentLoggingEnabled: Boolean,
    agentLogSize: Long,
    agentLogLocation: String,
    onToggleLogging: (Boolean) -> Unit,
    onViewLogs: () -> Unit,
    onCopyLogs: () -> Unit,
    onClearLogs: () -> Unit,
    diagnostics: SessionDiagnosticsViewModel? = previewOrNull { koinViewModel() },
) {
    SectionHeader("智能体诊断", "智能体执行流、工具调用与错误详情本地持久化")
    RuntimeCard(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            IconTile(RuntimeIconName.Shield)
            Column(Modifier.weight(1f)) {
                Text("智能体本地调试日志", style = RuntimeTheme.typography.titleMedium)
                Text(
                    if (agentLoggingEnabled) "已启用（文件大小：${agentLogSize.toReadableSize()}）" else "已关闭（不写入本地日志文件）",
                    style = RuntimeTheme.typography.bodySmall,
                    color = RuntimeTheme.colorScheme.onSurfaceVariant,
                )
                if (agentLogLocation.isNotBlank()) {
                    Text(
                        "日志文件：$agentLogLocation",
                        style = RuntimeTheme.typography.labelSmall,
                        color = RuntimeTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Switch(checked = agentLoggingEnabled, onCheckedChange = onToggleLogging)
        }
        if (agentLoggingEnabled || agentLogSize > 0L) {
            Spacer(Modifier.height(12.dp))
            top.wkbin.taixu.ui.components.RuntimeActionRow() {
                OutlinedButton(onClick = onViewLogs, modifier = Modifier) {
                    Text("查看日志")
                }
                OutlinedButton(onClick = onCopyLogs, modifier = Modifier) {
                    Text("复制全部")
                }
                TextButton(onClick = onClearLogs) {
                    Text("清空", color = RuntimeTheme.colorScheme.error)
                }
            }
        }
    }

    Spacer(Modifier.height(10.dp))
    UnattendedApprovalDiagnosticsCard(diagnostics)
    Spacer(Modifier.height(10.dp))
    BrowserControlDiagnosticsCard(diagnostics)
    Spacer(Modifier.height(10.dp))
    MessageFeedbackDiagnosticsCard(diagnostics)
}

@Composable
private fun UnattendedApprovalDiagnosticsCard(diagnostics: SessionDiagnosticsViewModel?) {
    val groups by diagnostics?.unattendedGroups?.collectAsStateWithLifecycle()
        ?: previewStateOf<List<UnattendedAuditSessionGroup>>(emptyList())
    val formatter = remember { SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()) }
    RuntimeCard(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            IconTile(RuntimeIconName.Shield)
            Column(Modifier.weight(1f)) {
                Text("无人盯着 · 审批旁路", style = RuntimeTheme.typography.titleMedium)
                Text(
                    "排期 / 委派 / 工作流下的放行决策、失败与免审旁路（最多 50 条）",
                    style = RuntimeTheme.typography.bodySmall,
                    color = RuntimeTheme.colorScheme.onSurfaceVariant,
                )
            }
            StatusBadge("${groups.sumOf { it.entries.size }} 条旁路")
        }
        if (groups.isEmpty()) {
            Spacer(Modifier.height(10.dp))
            Text("暂无无人值守旁路记录", style = RuntimeTheme.typography.bodyMedium, color = RuntimeTheme.colorScheme.onSurfaceVariant)
            return@RuntimeCard
        }
        groups.forEach { group ->
            Spacer(Modifier.height(14.dp))
            Spacer(Modifier.height(10.dp))
            Text(
                group.sessionTitle,
                style = RuntimeTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            group.entries.forEach { entry ->
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatusBadge(unattendedInitiatorLabel(entry.initiatorKind), RuntimeTheme.colorScheme.tertiary)
                    StatusBadge(unattendedAuditKindLabel(entry.kind))
                    Text(
                        formatter.format(Date(entry.createdAt)),
                        style = RuntimeTheme.typography.labelSmall,
                        color = RuntimeTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    buildString {
                        append(entry.toolName)
                        entry.initiatorId?.takeIf { it.isNotBlank() }?.let { append(" · ").append(it) }
                    },
                    style = RuntimeTheme.typography.bodySmall,
                    color = RuntimeTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun BrowserControlDiagnosticsCard(diagnostics: SessionDiagnosticsViewModel?) {
    val entries by diagnostics?.controlEntries?.collectAsStateWithLifecycle()
        ?: previewStateOf<List<AgentApprovalAuditEntity>>(emptyList())
    val formatter = remember { SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()) }
    RuntimeCard(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            IconTile(RuntimeIconName.Globe)
            Column(Modifier.weight(1f)) {
                Text("浏览器接管交接", style = RuntimeTheme.typography.titleMedium)
                Text(
                    "共浏览关闭/恢复与人接管期间 Bot 求助（control_* · help_requested，最多 50 条）",
                    style = RuntimeTheme.typography.bodySmall,
                    color = RuntimeTheme.colorScheme.onSurfaceVariant,
                )
            }
            StatusBadge("${entries.size} 条交接")
        }
        if (entries.isEmpty()) {
            Spacer(Modifier.height(10.dp))
            Text("暂无接管记录", style = RuntimeTheme.typography.bodyMedium, color = RuntimeTheme.colorScheme.onSurfaceVariant)
            return@RuntimeCard
        }
        entries.forEach { entry ->
            Spacer(Modifier.height(10.dp))
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatusBadge(unattendedAuditKindLabel(entry.kind))
                Text(
                    formatter.format(Date(entry.createdAt)),
                    style = RuntimeTheme.typography.labelSmall,
                    color = RuntimeTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(
                buildString {
                    append(entry.toolName)
                    entry.detail?.takeIf { it.isNotBlank() }?.let { append(" · ").append(it) }
                },
                style = RuntimeTheme.typography.bodySmall,
                color = RuntimeTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun MessageFeedbackDiagnosticsCard(diagnostics: SessionDiagnosticsViewModel?) {
    val groups by diagnostics?.groups?.collectAsStateWithLifecycle()
        ?: previewStateOf<List<FeedbackSessionGroup>>(emptyList())
    val formatter = remember { SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()) }
    RuntimeCard(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            IconTile(RuntimeIconName.Chat)
            Column(Modifier.weight(1f)) {
                Text("消息反馈", style = RuntimeTheme.typography.titleMedium)
                Text(
                    "用户对助手消息的评价，只读（最多 50 条）",
                    style = RuntimeTheme.typography.bodySmall,
                    color = RuntimeTheme.colorScheme.onSurfaceVariant,
                )
            }
            StatusBadge("${groups.sumOf { it.entries.size }} 条反馈")
        }
        if (groups.isEmpty()) {
            Spacer(Modifier.height(10.dp))
            Text("暂无反馈", style = RuntimeTheme.typography.bodyMedium, color = RuntimeTheme.colorScheme.onSurfaceVariant)
            return@RuntimeCard
        }
        groups.forEach { group ->
            Spacer(Modifier.height(14.dp))
            Spacer(Modifier.height(10.dp))
            Text(
                group.sessionTitle,
                style = RuntimeTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            group.entries.forEach { entry ->
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    val positive = entry.rating == AgentMessageFeedbackEntity.RATING_UP
                    StatusBadge(
                        if (positive) "有帮助" else "有问题",
                        if (positive) RuntimeTheme.colorScheme.secondary else RuntimeTheme.colorScheme.error,
                    )
                    Text(
                        formatter.format(Date(entry.updatedAt)),
                        style = RuntimeTheme.typography.labelSmall,
                        color = RuntimeTheme.colorScheme.onSurfaceVariant,
                    )
                }
                entry.note?.let { note ->
                    Spacer(Modifier.height(4.dp))
                    Text(note, style = RuntimeTheme.typography.bodySmall, color = RuntimeTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

/** 一个会话下的反馈条目，保留查询返回的倒序。 */
internal data class FeedbackSessionGroup(
    val sessionId: String,
    val sessionTitle: String,
    val entries: List<AgentMessageFeedbackEntity>,
)

/** 一个会话下的无人盯着审批旁路条目（倒序来自查询）。 */
internal data class UnattendedAuditSessionGroup(
    val sessionId: String,
    val sessionTitle: String,
    val entries: List<AgentApprovalAuditEntity>,
)

/**
 * 按会话聚合最近反馈：分组顺序取反馈首次出现顺序（即最近反馈所属会话在前）；
 * 会话已不存在时退回占位标题，不隐藏记录。
 */
internal fun groupFeedbackBySession(
    entries: List<AgentMessageFeedbackEntity>,
    sessionTitles: Map<String, String>,
): List<FeedbackSessionGroup> = entries.groupBy { it.sessionId }.map { (sessionId, items) ->
    FeedbackSessionGroup(
        sessionId = sessionId,
        sessionTitle = sessionTitles[sessionId]?.takeIf { it.isNotBlank() } ?: "未知会话",
        entries = items,
    )
}

/** 同 [groupFeedbackBySession]：保查询倒序下的会话首次出现顺序。 */
internal fun groupUnattendedAuditsBySession(
    entries: List<AgentApprovalAuditEntity>,
    sessionTitles: Map<String, String>,
): List<UnattendedAuditSessionGroup> {
    val order = entries.map { it.sessionId }.distinct()
    val bySession = entries.groupBy { it.sessionId }
    return order.map { sessionId ->
        UnattendedAuditSessionGroup(
            sessionId = sessionId,
            sessionTitle = sessionTitles[sessionId]?.takeIf { it.isNotBlank() } ?: "未知会话",
            entries = bySession.getValue(sessionId),
        )
    }
}

internal fun unattendedInitiatorLabel(kind: String): String = when (kind) {
    "schedule" -> "排期"
    "handoff" -> "委派"
    "workflow" -> "工作流"
    else -> kind
}

internal fun unattendedAuditKindLabel(kind: String): String = when (kind) {
    AgentApprovalAuditEntity.KIND_GRANT_WRITTEN -> "会话授权"
    AgentApprovalAuditEntity.KIND_GRANT_EXEMPTED -> "授权豁免"
    AgentApprovalAuditEntity.KIND_DEFERRED -> "Lane 交接"
    AgentApprovalAuditEntity.KIND_POLICY_DENIED -> "策略否决"
    AgentApprovalAuditEntity.KIND_DECISION_FORWARDED -> "放行决策"
    AgentApprovalAuditEntity.KIND_ACTION_FAILED -> "执行失败"
    AgentApprovalAuditEntity.KIND_CONTROL_TAKEN -> "人接管"
    AgentApprovalAuditEntity.KIND_CONTROL_RELEASED -> "人交还"
    AgentApprovalAuditEntity.KIND_HELP_REQUESTED -> "Bot 求助"
    else -> kind
}

/** 开发者诊断数据源：最近反馈 / 无人盯着审批旁路 × 会话标题。 */
internal class SessionDiagnosticsViewModel(
    feedback: MessageFeedbackService,
    sessions: HarnessSessionRepository,
    approvalAudits: AgentApprovalAuditRepository,
) : ViewModel() {

    val groups: StateFlow<List<FeedbackSessionGroup>> = combine(
        feedback.observeRecent(),
        sessions.observeAll(),
    ) { entries, allSessions ->
        groupFeedbackBySession(entries, allSessions.associate { it.id to it.title })
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val unattendedGroups: StateFlow<List<UnattendedAuditSessionGroup>> = combine(
        approvalAudits.observeUnattendedRecent(),
        sessions.observeAll(),
    ) { entries, allSessions ->
        groupUnattendedAuditsBySession(entries, allSessions.associate { it.id to it.title })
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val controlEntries: StateFlow<List<AgentApprovalAuditEntity>> =
        approvalAudits.observeControlRecent()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
}

private fun Long.toReadableSize(): String {
    if (this < 1024L) return "$this B"
    val units = listOf("KB", "MB", "GB", "TB")
    var value = this.toDouble()
    var index = -1
    while (value >= 1024 && index < units.lastIndex) { value /= 1024; index += 1 }
    return "%.1f %s".format(value, units[index])
}
