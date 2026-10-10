package top.wkbin.taixu.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import top.wkbin.taixu.core.model.ToolchainGroup
import top.wkbin.taixu.core.model.ToolchainProbeResult
import top.wkbin.taixu.core.model.ToolchainReport
import top.wkbin.taixu.core.model.ToolchainStatus
import top.wkbin.taixu.ui.components.RuntimeIcon
import top.wkbin.taixu.ui.components.RuntimeIconName

/**
 * 🧰 沙箱工具链面板
 *
 * 回答一个此前 App 里完全没有答案的问题：**沙箱里到底装了哪些逆向/构建工具，缺什么、版本够不够新。**
 *
 * 交互：
 *  - 打开即自动全量检测；
 *  - 「一键补齐」把所有缺失与落后项合并安装（走国内镜像），安装完自动重新检测刷新；
 *  - 需要 root 才能驱动的工具单独高亮标注，避免用户以为装完就能跑。
 *
 * 纯 UI 层，不含业务判定 —— 判定全部来自 core/model 的 [ToolchainReport]。
 */
@Composable
internal fun ToolchainPanel(
    viewModel: ToolchainViewModel,
    modifier: Modifier = Modifier,
) {
    val report by viewModel.report.collectAsState()
    val logs by viewModel.logs.collectAsState()
    val busy by viewModel.busy.collectAsState()
    val failed by viewModel.failed.collectAsState()

    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(16.dp),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // ---------- 标题区 ----------
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                RuntimeIcon(
                    name = RuntimeIconName.Wrench,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = "沙箱工具链",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    text = "逆向 · 构建 · 调试 全量探针",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            val current = report

            // ---------- 状态区 ----------
            when {
                current == null && failed -> StatusBanner(
                    text = "检测失败：沙箱可能未启动，请先在仪表盘初始化 Linux 沙箱",
                    color = Color(0xFFC62828),
                )

                current == null -> Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                    Text("正在检测沙箱工具…", style = MaterialTheme.typography.bodySmall)
                }

                else -> SummaryRow(current)
            }

            // ---------- 一键补齐 ----------
            val repairables = current?.repairable.orEmpty()
            if (repairables.isNotEmpty()) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Button(onClick = { viewModel.repairMissing() }, enabled = !busy) {
                        if (busy) {
                            CircularProgressIndicator(modifier = Modifier.size(12.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(6.dp))
                            Text("补齐中…", fontSize = 12.sp)
                        } else {
                            RuntimeIcon(
                                name = RuntimeIconName.Download,
                                modifier = Modifier.size(13.dp),
                                tint = Color.White,
                            )
                            Spacer(Modifier.width(6.dp))
                            Text("一键补齐 ${repairables.size} 项", fontSize = 12.sp)
                        }
                    }
                    if (!busy) {
                        TextButton(onClick = { viewModel.refresh() }) {
                            Text("重新检测", fontSize = 12.sp)
                        }
                    }
                }
            } else if (current != null && current.results.isNotEmpty() && !busy) {
                TextButton(onClick = { viewModel.refresh() }) {
                    Text("重新检测", fontSize = 12.sp)
                }
            }

            // ---------- root 依赖提示 ----------
            val rootTools = current?.rootRequired.orEmpty()
            if (rootTools.isNotEmpty()) {
                StatusBanner(
                    text = "以下工具已装好，但实际驱动需要 root 权限：" +
                        rootTools.joinToString(", ") { it.probe.displayName },
                    color = Color(0xFF6A1B9A),
                )
            }

            // ---------- 安装日志 ----------
            if (logs.isNotEmpty()) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    shape = RoundedCornerShape(8.dp),
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        logs.takeLast(12).forEach {
                            Text(
                                text = it,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }

            // ---------- 分组列表 ----------
            if (current != null && current.results.isNotEmpty()) {
                ToolchainGroup.entries.forEach { group ->
                    val items = current.results.filter { it.probe.group == group }
                    if (items.isEmpty()) return@forEach

                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = group.displayName,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold,
                        )
                        items.forEach { item -> ToolchainRow(item) }
                    }
                }
            }
        }
    }
}

@Composable
private fun SummaryRow(report: ToolchainReport) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        CountChip("就绪 ${report.readyCount}", Color(0xFF2E7D32))
        if (report.missingCount > 0) CountChip("缺失 ${report.missingCount}", Color(0xFFC62828))
        if (report.outdatedCount > 0) CountChip("版本落后 ${report.outdatedCount}", Color(0xFFE65100))
        if (report.needsRootCount > 0) CountChip("需 root ${report.needsRootCount}", Color(0xFF6A1B9A))
        if (report.unknownCount > 0) CountChip("待确认 ${report.unknownCount}", Color(0xFF546E7A))
    }
}

@Composable
private fun CountChip(text: String, color: Color) {
    Surface(color = color.copy(alpha = 0.12f), shape = RoundedCornerShape(6.dp)) {
        Text(
            text = text,
            fontSize = 11.sp,
            color = color,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
        )
    }
}

@Composable
private fun StatusBanner(text: String, color: Color) {
    Surface(color = color.copy(alpha = 0.10f), shape = RoundedCornerShape(8.dp)) {
        Text(
            text = text,
            fontSize = 12.sp,
            color = color,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
        )
    }
}

@Composable
private fun ToolchainRow(item: ToolchainProbeResult) {
    val (dotColor, statusText) = when (item.status) {
        ToolchainStatus.READY -> Color(0xFF2E7D32) to "就绪"
        ToolchainStatus.OUTDATED -> Color(0xFFE65100) to "落后"
        ToolchainStatus.MISSING -> Color(0xFFC62828) to "缺失"
        ToolchainStatus.NEEDS_ROOT -> Color(0xFF6A1B9A) to "需root"
        ToolchainStatus.UNKNOWN -> Color(0xFF546E7A) to "待确认"
    }

    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.5f),
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(7.dp)
                        .background(dotColor, CircleShape),
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = item.probe.displayName,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = statusText,
                    fontSize = 10.sp,
                    color = dotColor,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Spacer(Modifier.height(3.dp))
            Text(
                text = item.probe.purpose,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 15.sp,
            )
            if (item.summary.isNotBlank() && item.summary != item.probe.purpose) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = item.summary,
                    style = MaterialTheme.typography.labelSmall,
                    color = dotColor.copy(alpha = 0.85f),
                )
            }
        }
    }
}
