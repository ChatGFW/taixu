package top.wkbin.taixu.ui.bot

import androidx.compose.runtime.Composable
import top.wkbin.taixu.core.model.bot.BotAssignment
import top.wkbin.taixu.ui.miuix.RuntimeTheme
import top.yukonga.miuix.kmp.basic.Text

/** 展示创建时的上限；实际准入继续核验最新成员配置和父任务。 */
@Composable
internal fun TaskCapabilityGrantSummary(task: BotAssignment, now: Long) {
    taskCapabilityGrantSummary(task, now)?.let { summary ->
        Text(summary, style = RuntimeTheme.typography.bodySmall, color = RuntimeTheme.colorScheme.onSurfaceVariant)
    }
}

internal fun taskCapabilityGrantSummary(task: BotAssignment, now: Long): String? {
    val grant = task.capabilityGrant ?: return null
    if (!grant.matches(task)) return "MCP 授权记录异常，调用已受限。"
    val policy = grant.policy
    val services = policy.servers?.let { "${it.size} 个服务" } ?: "全部服务"
    val deadline = policy.expiresAt?.let { "截止 ${formatCapabilityDeadline(it)}" } ?: "创建记录不限时"
    val state = when {
        policy.isExpired(now) -> "期限已到，MCP 调用已受限。"
        !task.status.isActive -> "任务处于非执行状态，MCP 调用已受限。"
        else -> "执行前还会核验最新成员配置与父任务状态。"
    }
    return "创建时的 MCP 权限上限\n$services，${policy.tools.size} 个服务设有工具限制，$deadline。\n$state"
}
