package top.wkbin.taixu.ui.workflow

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import top.wkbin.taixu.core.model.workflow.NodeRunStatus
import top.wkbin.taixu.core.model.workflow.WorkflowRunStatus
import top.wkbin.taixu.ui.components.RuntimeIconName
import top.wkbin.taixu.ui.miuix.RuntimeTheme

@Composable
internal fun statusColor(status: NodeRunStatus): Color = when (status) {
    NodeRunStatus.SUCCESS -> Color(0xFF2E7D32)
    NodeRunStatus.FAILED, NodeRunStatus.CANCELLED -> RuntimeTheme.colorScheme.error
    NodeRunStatus.RUNNING, NodeRunStatus.STREAMING, NodeRunStatus.WAITING_APPROVAL -> RuntimeTheme.colorScheme.primary
    NodeRunStatus.SKIPPED -> RuntimeTheme.colorScheme.tertiary
    else -> RuntimeTheme.colorScheme.outline
}

internal fun statusIcon(status: NodeRunStatus) = when (status) {
    NodeRunStatus.SUCCESS -> RuntimeIconName.Check
    NodeRunStatus.FAILED, NodeRunStatus.CANCELLED -> RuntimeIconName.Alert
    NodeRunStatus.RUNNING, NodeRunStatus.STREAMING -> RuntimeIconName.Play
    NodeRunStatus.WAITING_APPROVAL -> RuntimeIconName.Shield
    NodeRunStatus.SKIPPED -> RuntimeIconName.ArrowUp
    else -> RuntimeIconName.Hub
}

internal fun statusLabel(status: NodeRunStatus) = when (status) {
    NodeRunStatus.IDLE -> "空闲"
    NodeRunStatus.PENDING -> "等待"
    NodeRunStatus.RUNNING -> "运行中"
    NodeRunStatus.STREAMING -> "输出中"
    NodeRunStatus.WAITING_APPROVAL -> "待确认"
    NodeRunStatus.SUCCESS -> "成功"
    NodeRunStatus.FAILED -> "失败"
    NodeRunStatus.SKIPPED -> "已跳过"
    NodeRunStatus.CANCELLED -> "已取消"
}

internal fun workflowStatusLabel(status: WorkflowRunStatus) = when (status) {
    WorkflowRunStatus.IDLE -> "准备执行"
    WorkflowRunStatus.RUNNING -> "工作流运行中"
    WorkflowRunStatus.WAITING_APPROVAL -> "等待人工确认"
    WorkflowRunStatus.SUCCESS -> "工作流已完成"
    WorkflowRunStatus.FAILED -> "工作流执行失败"
    WorkflowRunStatus.CANCELLED -> "工作流已取消"
}
