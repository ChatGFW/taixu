package top.wkbin.taixu.ui.chat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import top.wkbin.taixu.ui.components.RuntimeCircularProgressIndicator
import top.wkbin.taixu.ui.components.RuntimeIcon
import top.wkbin.taixu.ui.components.RuntimeIconName
import top.wkbin.taixu.ui.miuix.RuntimeTheme
import top.wkbin.taixu.ui.miuix.Surface
import top.yukonga.miuix.kmp.basic.Text

/**
 * "+" 附件弹窗：顶部附件宫格（相册图片 / 草图 / 文件），
 * 下方分段入口（扩展管理、压缩对话）。拍照/音视频暂不提供，需要时按格扩展。
 */
@Composable
internal fun ChatAttachSheet(
    canCompress: Boolean,
    compacting: Boolean,
    onCamera: () -> Unit,
    onPickImage: () -> Unit,
    onSketch: () -> Unit,
    onPickFile: () -> Unit,
    onOpenSkillsMcp: () -> Unit,
    onCompact: () -> Unit,
    onDismiss: () -> Unit,
) {
    val actionsEnabled = !compacting
    ChatBottomSheet(
        onDismissRequest = onDismiss,
        dismissEnabled = actionsEnabled,
        title = { Text("添加内容", fontWeight = FontWeight.Medium) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                // 附件宫格
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    AttachGridCell("拍照", RuntimeIconName.Camera, Modifier.weight(1f), enabled = actionsEnabled, onClick = onCamera)
                    AttachGridCell("图片", RuntimeIconName.Image, Modifier.weight(1f), enabled = actionsEnabled, onClick = onPickImage)
                    AttachGridCell("草图", RuntimeIconName.Edit, Modifier.weight(1f), enabled = actionsEnabled, onClick = onSketch)
                    AttachGridCell("文件", RuntimeIconName.Document, Modifier.weight(1f), enabled = actionsEnabled, onClick = onPickFile)
                }

                // 分段入口
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    AttachListEntry(
                        icon = RuntimeIconName.Prompt,
                        title = "扩展管理",
                        subtitle = "技能与 MCP 工具挂载",
                        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
                        enabled = actionsEnabled,
                        onClick = onOpenSkillsMcp,
                    )
                    AttachListEntry(
                        icon = RuntimeIconName.Compress,
                        title = if (compacting) "正在压缩…" else "压缩对话",
                        subtitle = when {
                            compacting -> "正在生成结构化摘要，请稍候"
                            canCompress -> "把较早的历史折叠为结构化摘要，原文可回读"
                            else -> "当前会话还没有可折叠的历史"
                        },
                        enabled = canCompress && actionsEnabled,
                        loading = compacting,
                        shape = RoundedCornerShape(bottomStart = 16.dp, bottomEnd = 16.dp),
                        onClick = onCompact,
                    )
                }
            }
        },
    )
}

@Composable
private fun AttachGridCell(
    label: String,
    icon: RuntimeIconName,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val content: @Composable () -> Unit = {
        Column(
            modifier = Modifier.padding(vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            RuntimeIcon(
                icon,
                Modifier.size(24.dp),
                if (enabled) RuntimeTheme.colorScheme.onSurface else RuntimeTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
            )
            Text(
                label,
                style = RuntimeTheme.typography.labelMedium,
                color = if (enabled) RuntimeTheme.colorScheme.onSurface else RuntimeTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
    if (enabled) {
        Surface(
            onClick = onClick,
            modifier = modifier.heightIn(min = 84.dp),
            shape = RuntimeTheme.shapes.medium,
            color = RuntimeTheme.colorScheme.surfaceContainerHigh,
            content = content,
        )
    } else {
        Surface(
            modifier = modifier.heightIn(min = 84.dp),
            shape = RuntimeTheme.shapes.medium,
            color = RuntimeTheme.colorScheme.surfaceContainerLowest,
            content = content,
        )
    }
}

@Composable
private fun AttachListEntry(
    icon: RuntimeIconName,
    title: String,
    subtitle: String,
    shape: Shape,
    onClick: () -> Unit,
    enabled: Boolean = true,
    loading: Boolean = false,
) {
    val container = if (enabled || loading) RuntimeTheme.colorScheme.surfaceContainerHigh
    else RuntimeTheme.colorScheme.surfaceContainerLowest
    val body: @Composable () -> Unit = {
        AttachListEntryContent(icon, title, subtitle, enabled = enabled || loading, loading = loading)
    }
    // 走 miuix Surface(onClick) 而不是外挂 clickable：条件禁用时也保持可访问语义，避免点了无涟漪/无回调。
    if (enabled) {
        Surface(
            onClick = onClick,
            modifier = Modifier.fillMaxWidth(),
            shape = shape,
            color = container,
            content = body,
        )
    } else {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = shape,
            color = container,
            content = body,
        )
    }
}

@Composable
private fun AttachListEntryContent(
    icon: RuntimeIconName,
    title: String,
    subtitle: String,
    enabled: Boolean,
    loading: Boolean = false,
) {
    Row(
        modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (loading) {
            RuntimeCircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                strokeWidth = 2.dp,
                color = RuntimeTheme.colorScheme.primary,
            )
        } else {
            RuntimeIcon(
                icon,
                Modifier.size(20.dp),
                if (enabled) RuntimeTheme.colorScheme.onSurface else RuntimeTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                title,
                style = RuntimeTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                color = if (enabled) RuntimeTheme.colorScheme.onSurface else RuntimeTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                subtitle,
                style = RuntimeTheme.typography.labelSmall,
                color = RuntimeTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
