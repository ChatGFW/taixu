package top.wkbin.taixu.ui.settings

import androidx.compose.foundation.layout.*
import top.wkbin.taixu.ui.miuix.RuntimeTheme
import top.yukonga.miuix.kmp.basic.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import top.wkbin.taixu.ui.components.*

/** 操作区允许自然换行，不让多个小按钮争抢标题所在的一行。 */
@Composable
internal fun McpManagementActions(count: Int, onRefresh: () -> Unit, onDiscover: () -> Unit, onAdd: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text("MCP 服务 · $count", Modifier.weight(1f), style = RuntimeTheme.typography.titleMedium)
        RuntimeIconButton(onRefresh, contentDescription = "刷新 MCP 服务") { RuntimeIcon(RuntimeIconName.Refresh, Modifier.size(20.dp)) }
        RuntimeIconButton(onDiscover, contentDescription = "探测本机") { RuntimeIcon(RuntimeIconName.Search, Modifier.size(20.dp)) }
        RuntimeIconButton(onAdd, contentDescription = "添加服务") { RuntimeIcon(RuntimeIconName.Plus, Modifier.size(20.dp)) }
    }
}
