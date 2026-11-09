package top.wkbin.taixu.ui.bot

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import top.wkbin.taixu.core.model.bot.Bot
import top.wkbin.taixu.core.model.bot.BotRuntimeMode
import top.wkbin.taixu.core.model.bot.ExternalAgentRuntimeToken
import top.wkbin.taixu.runtime.webchat.ExternalAgentRuntimeServerStatus
import top.wkbin.taixu.ui.components.RuntimeButton
import top.wkbin.taixu.ui.components.RuntimeFilledTextField
import top.wkbin.taixu.ui.components.RuntimeOutlinedButton
import top.wkbin.taixu.ui.components.RuntimeSwitch
import top.wkbin.taixu.ui.components.StatusBadge
import top.wkbin.taixu.ui.miuix.RuntimeTheme
import top.wkbin.taixu.ui.miuix.RuntimeSpacing
import top.yukonga.miuix.kmp.basic.Text

@Composable
internal fun BotExternalRuntimePanel(
    bot: Bot,
    busy: Boolean,
    tokens: List<ExternalAgentRuntimeToken>,
    issuedPlaintext: String?,
    serverStatus: ExternalAgentRuntimeServerStatus,
    onIssue: (label: String) -> Unit,
    onRevoke: (tokenId: String) -> Unit,
    onRestoreLocal: () -> Unit,
    onToggleServer: (Boolean) -> Unit,
    onClearIssued: () -> Unit,
) {
    if (bot.isMainBot) return
    val context = LocalContext.current
    var label by remember(bot.id) { mutableStateOf("laptop") }
    Column(verticalArrangement = Arrangement.spacedBy(RuntimeSpacing.cardGap)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("外部运行时", style = RuntimeTheme.typography.bodyMedium)
            StatusBadge(if (bot.runtimeMode == BotRuntimeMode.EXTERNAL) "EXTERNAL" else "LOCAL")
        }
        Text(
            "电脑或服务器上的执行器用运行时令牌领取本成员群收件。鉴权只用令牌，不使用 Web 协作台 PIN。",
            style = RuntimeTheme.typography.labelSmall,
            color = RuntimeTheme.colorScheme.onSurfaceVariant,
        )
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("局域网运行时服务", style = RuntimeTheme.typography.bodyMedium)
                if (serverStatus.isRunning) {
                    Text(
                        serverStatus.accessUrl,
                        style = RuntimeTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                        color = RuntimeTheme.colorScheme.primary,
                    )
                }
            }
            RuntimeSwitch(
                checked = serverStatus.isRunning,
                onCheckedChange = onToggleServer,
                enabled = !busy,
            )
        }
        RuntimeFilledTextField(
            value = label,
            onValueChange = { label = it.take(40) },
            labelText = "令牌备注",
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        RuntimeButton(
            onClick = { onIssue(label) },
            enabled = !busy && label.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
        ) { Text("签发运行时令牌") }
        issuedPlaintext?.let { token ->
            Text("明文只显示一次，请立即复制到外部执行器。", style = RuntimeTheme.typography.labelSmall,
                color = RuntimeTheme.colorScheme.onSurfaceVariant)
            Text(
                token,
                style = RuntimeTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Medium),
                color = RuntimeTheme.colorScheme.primary,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                RuntimeOutlinedButton(onClick = {
                    copyText(context, "太墟外部运行时令牌", token)
                    onClearIssued()
                }) { Text("复制令牌") }
                if (serverStatus.isRunning) {
                    RuntimeOutlinedButton(onClick = {
                        copyText(context, "太墟外部运行时地址", serverStatus.accessUrl)
                    }) { Text("复制地址") }
                }
            }
        }
        tokens.filter { it.revokedAt == null }.forEach { token ->
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(token.label, style = RuntimeTheme.typography.bodyMedium)
                    Text(
                        token.id.take(8),
                        style = RuntimeTheme.typography.labelSmall,
                        color = RuntimeTheme.colorScheme.onSurfaceVariant,
                    )
                }
                RuntimeOutlinedButton(onClick = { onRevoke(token.id) }, enabled = !busy) { Text("撤销") }
            }
        }
        if (bot.runtimeMode == BotRuntimeMode.EXTERNAL) {
            RuntimeOutlinedButton(
                onClick = onRestoreLocal,
                enabled = !busy,
                modifier = Modifier.fillMaxWidth(),
            ) { Text("切回手机内执行") }
        }
    }
}

private fun copyText(context: Context, label: String, value: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText(label, value))
    Toast.makeText(context, "已复制", Toast.LENGTH_SHORT).show()
}
