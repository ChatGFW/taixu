package top.wkbin.taixu.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import top.wkbin.taixu.ui.miuix.RuntimeTheme
import top.yukonga.miuix.kmp.basic.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.compose.viewmodel.koinViewModel
import top.wkbin.taixu.core.model.RemoteServer
import top.wkbin.taixu.ui.components.*

@Composable
internal fun SystemEnvironmentPicker(localCount: Int, onInstallLocal: () -> Unit, viewModel: RemoteServerViewModel? = previewOrNull { koinViewModel() }) {
    if (viewModel == null) {
        Text("运行后端", style = RuntimeTheme.typography.titleMedium)
        return
    }
    val servers by viewModel.servers.collectAsStateWithLifecycle()
    val configurationError by viewModel.configurationError.collectAsStateWithLifecycle()
    val connection by viewModel.connectionState.collectAsStateWithLifecycle()
    var addRemote by remember { mutableStateOf(false) }
    var remove by remember { mutableStateOf<RemoteServer?>(null) }
    var editing by remember { mutableStateOf<RemoteServer?>(null) }
    var choose by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        configurationError?.let { NoticeBanner(text = it, isError = true) }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Text("运行后端", style = RuntimeTheme.typography.titleMedium)
            RuntimeButton(onClick = { choose = true }) { Text("添加系统") }
        }
        NoticeBanner(text = "首页、工坊与系统状态始终使用本机 PRoot。SSH 连接档案出现在聊天侧边栏，可按服务器新建对话，无需切换主系统。")
        BackendChoiceCard(
            title = "本地 PRoot",
            detail = if (localCount > 0) "本机主系统（已安装 $localCount 套）" else "在手机上运行 Linux，使用本机工作区。",
            icon = RuntimeIconName.Linux,
            selected = true,
            onClick = { viewModel.select(null) },
        )
        connection.message?.let { Text(it, color = if (connection.tested) RuntimeTheme.colorScheme.primary else RuntimeTheme.colorScheme.error) }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Text("连接档案 (${servers.size})", style = RuntimeTheme.typography.titleSmall, color = RuntimeTheme.colorScheme.primary)
            RuntimeTextButton(onClick = { viewModel.clear(); addRemote = true }) { Text("新增连接") }
        }
        if (servers.isEmpty()) {
            Text(
                "添加 SSH 连接后，可在聊天侧边栏按服务器新建对话。",
                style = RuntimeTheme.typography.bodySmall,
                color = RuntimeTheme.colorScheme.onSurfaceVariant,
            )
        }
        servers.forEach { server ->
            ConnectionProfileCard(
                server = server,
                onEdit = { viewModel.clear(); editing = server },
                onRemove = { remove = server },
            )
        }
    }
    if (choose) RuntimeAlertDialog(
        onDismissRequest = { choose = false }, title = { Text("选择系统来源") },
        text = { Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            RuntimeOutlinedButton(modifier = Modifier.fillMaxWidth(), onClick = { choose = false; onInstallLocal() }) { Text("安装本地 Linux") }
            RuntimeOutlinedButton(modifier = Modifier.fillMaxWidth(), onClick = { choose = false; viewModel.clear(); addRemote = true }) { Text("连接服务器") }
        } },
        confirmButton = { RuntimeTextButton(onClick = { choose = false }) { Text("取消") } },
    )
    if (addRemote) RemoteServerDialog(viewModel) { addRemote = false }
    editing?.let { target -> key(target.id) { RemoteServerDialog(viewModel, target) { editing = null } } }
    remove?.let { server -> RuntimeAlertDialog(
        onDismissRequest = { remove = null }, title = { Text("移除 ${server.name}？") },
        text = { Text("仅移除手机保存的连接与凭证，服务器文件保留。已绑定该服务器的会话需结束后重新选择环境。") },
        confirmButton = { RuntimeTextButton(onClick = { viewModel.remove(server.id); remove = null }) { Text("移除") } },
        dismissButton = { RuntimeTextButton(onClick = { remove = null }) { Text("取消") } },
    ) }
}
