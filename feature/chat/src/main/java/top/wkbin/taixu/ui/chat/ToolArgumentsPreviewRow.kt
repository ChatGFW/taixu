package top.wkbin.taixu.ui.chat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import top.wkbin.taixu.feature.chat.R
import top.wkbin.taixu.harness.ToolCallStreamProgress
import top.wkbin.taixu.harness.displayTarget
import top.wkbin.taixu.ui.components.RuntimeIconName
import top.wkbin.taixu.ui.miuix.RuntimeTheme
import top.yukonga.miuix.kmp.basic.Text

/**
 * 工具参数流式生成期间的临时任务行：模型还在产出 write/edit JSON 时，
 * 就用已到达的 path/content 片段显示「写入文件 · xxx +N -N」，
 * 正式 ToolCall 发布后由状态镜像清空本行，避免与正式工具行重复。
 */
@Composable
internal fun ToolArgumentsPreviewRow(progress: ToolCallStreamProgress) {
    val write = progress.name == "write"
    val title = stringResource(if (write) R.string.chat_action_write else R.string.chat_action_edit)
    val stateText = stringResource(R.string.chat_step_running)
    Row(
        Modifier.fillMaxWidth().heightIn(min = 28.dp).padding(horizontal = 2.dp, vertical = 4.dp)
            .semantics { stateDescription = stateText },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        ChatProcessIcon(if (write) RuntimeIconName.WriteDocument else RuntimeIconName.Edit, running = true)
        val target = progress.displayTarget()
        Text(
            if (target == null) title else "$title · $target",
            Modifier.weight(1f).then(chatProcessPulse(true)),
            style = RuntimeTheme.typography.bodySmall,
            color = chatProcessColor(),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        DiffStatBadge(progress.addedLines, progress.deletedLines)
        ChatProcessTimer(progress.name, running = true, startedAtMs = progress.startedAtMs)
    }
}
