package top.wkbin.taixu.ui.bot

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
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
import top.wkbin.taixu.ui.components.RuntimeFilterChip
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
    onIssue: (label: String, expiresAt: Long?) -> Unit,
    onRevoke: (tokenId: String) -> Unit,
    onRevokeExpired: () -> Unit,
    onRestoreLocal: () -> Unit,
    onToggleServer: (Boolean) -> Unit,
    onClearIssued: () -> Unit,
) {
    if (bot.isMainBot) return
    val context = LocalContext.current
    var label by remember(bot.id) { mutableStateOf("laptop") }
    /** null = unlimited; otherwise days until expiry. */
    var expiryDays by remember(bot.id) { mutableStateOf<Int?>(null) }
    val now = System.currentTimeMillis()
    val openTokens = tokens.filter { it.revokedAt == null }
    val activeTokens = openTokens.filter { it.isActive(now) }
    val expiredTokens = openTokens.filter { !it.isActive(now) }
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
        if (bot.runtimeMode == BotRuntimeMode.EXTERNAL && activeTokens.isEmpty()) {
            Text(
                "当前没有可用运行时令牌：本地泵不会领取该成员收件。请签发新令牌，或切回手机内执行。",
                style = RuntimeTheme.typography.labelSmall,
                color = RuntimeTheme.colorScheme.error,
            )
        }
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
                    Text(
                        "claim ${serverStatus.claimUrl}",
                        style = RuntimeTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                        color = RuntimeTheme.colorScheme.onSurfaceVariant,
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
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("令牌有效期", style = RuntimeTheme.typography.bodyMedium)
            Row(
                Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                RuntimeFilterChip(
                    selected = expiryDays == null,
                    onClick = { expiryDays = null },
                    enabled = !busy,
                    label = { Text("不限时") },
                )
                listOf(7 to "7 天", 30 to "30 天").forEach { (days, chip) ->
                    RuntimeFilterChip(
                        selected = expiryDays == days,
                        onClick = { expiryDays = days },
                        enabled = !busy,
                        label = { Text(chip) },
                    )
                }
            }
            val previewExpiresAt = expiryDays?.let { System.currentTimeMillis() + it * 24L * 60L * 60L * 1000L }
            Text(
                previewExpiresAt?.let { "截止约 ${formatCapabilityDeadline(it)}，过期后令牌立即失效。" }
                    ?: "不设截止时间；可随时撤销或切回手机内执行。",
                style = RuntimeTheme.typography.labelSmall,
                color = RuntimeTheme.colorScheme.onSurfaceVariant,
            )
        }
        RuntimeButton(
            onClick = {
                val expiresAt = expiryDays?.let { System.currentTimeMillis() + it * 24L * 60L * 60L * 1000L }
                onIssue(label, expiresAt)
            },
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
                    RuntimeOutlinedButton(onClick = {
                        copyText(
                            context,
                            "太墟外部运行时环境变量",
                            "TAIXU_RUNTIME_BASE=${serverStatus.accessUrl}\nTAIXU_RUNTIME_TOKEN=$token\n",
                        )
                    }) { Text("复制环境变量") }
                }
            }
        }
        openTokens.forEach { token ->
            val expired = !token.isActive(now)
            val expiresAt = token.expiresAt
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(token.label, style = RuntimeTheme.typography.bodyMedium)
                        if (expired) StatusBadge("已过期")
                    }
                    Text(
                        buildString {
                            append(token.id.take(8))
                            when {
                                expired -> append(" · 已失效，可清理")
                                expiresAt != null -> {
                                    append(" · 截止 ")
                                    append(formatCapabilityDeadline(expiresAt))
                                }
                                else -> append(" · 不限时")
                            }
                        },
                        style = RuntimeTheme.typography.labelSmall,
                        color = RuntimeTheme.colorScheme.onSurfaceVariant,
                    )
                }
                RuntimeOutlinedButton(onClick = { onRevoke(token.id) }, enabled = !busy) {
                    Text(if (expired) "清理" else "撤销")
                }
            }
        }
        if (expiredTokens.isNotEmpty()) {
            RuntimeOutlinedButton(
                onClick = onRevokeExpired,
                enabled = !busy,
                modifier = Modifier.fillMaxWidth(),
            ) { Text("清理全部过期令牌") }
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
