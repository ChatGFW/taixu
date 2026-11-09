package top.wkbin.taixu.ui.home

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import top.wkbin.taixu.ui.miuix.HorizontalDivider
import top.wkbin.taixu.ui.miuix.RuntimeTheme
import top.yukonga.miuix.kmp.basic.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import top.wkbin.taixu.feature.home.R
import top.wkbin.taixu.ui.components.IconTile
import top.wkbin.taixu.ui.components.RuntimeButton as Button
import top.wkbin.taixu.ui.components.RuntimeCard
import top.wkbin.taixu.ui.components.RuntimeIcon
import top.wkbin.taixu.ui.components.RuntimeIconName
import top.wkbin.taixu.ui.components.RuntimeSwitch
import top.wkbin.taixu.runtime.webchat.WebChatServerStatus

/** WebChat 电脑大屏协作卡片 (Dashboard Bridge Card) */
@Composable
internal fun WebChatDashboardCard(
    status: WebChatServerStatus,
    onToggle: (Boolean) -> Unit,
) {
    val context = LocalContext.current
    val directUrl = "${status.accessUrl}?token=${status.pinCode}"

    RuntimeCard(
        modifier = Modifier.fillMaxWidth(), shape = RuntimeTheme.shapes.extraLarge,
        // 减强调：运行态仅保留容器色强调，不再叠加主色描边
        containerColor = if (status.isRunning) RuntimeTheme.colorScheme.primaryContainer.copy(alpha = 0.40f) else RuntimeTheme.colorScheme.surfaceContainer,
        contentPadding = PaddingValues(if (status.isRunning) 16.dp else 12.dp),
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f),
                ) {
                    IconTile(
                        icon = RuntimeIconName.Globe,
                        size = 36.dp,
                        color = if (status.isRunning) RuntimeTheme.colorScheme.primary else RuntimeTheme.colorScheme.onSurfaceVariant,
                    )
                    Column {
                        Text(
                            text = stringResource(R.string.home_webchat_title),
                            style = RuntimeTheme.typography.titleSmall,
                            color = RuntimeTheme.colorScheme.onSurface,
                        )
                        if (status.isRunning) Text(
                            text = stringResource(R.string.home_webchat_running),
                            style = RuntimeTheme.typography.bodySmall,
                            color = if (status.isRunning) RuntimeTheme.colorScheme.primary else RuntimeTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                RuntimeSwitch(
                    checked = status.isRunning,
                    onCheckedChange = onToggle,
                )
            }

            if (status.isRunning) {
                HorizontalDivider(color = RuntimeTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                // 访问地址独立成行：统一 Monospace 等宽字体、颜色与大小
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = stringResource(R.string.home_webchat_url_label),
                        style = RuntimeTheme.typography.labelSmall,
                        color = RuntimeTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = status.accessUrl,
                        style = RuntimeTheme.typography.bodyMedium.copy(
                            fontWeight = FontWeight.Medium,
                            fontFamily = FontFamily.Monospace,
                        ),
                        color = RuntimeTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                // 配对码独立成行显示
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = stringResource(R.string.home_webchat_pin_label),
                        style = RuntimeTheme.typography.labelSmall,
                        color = RuntimeTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = status.pinCode,
                        style = RuntimeTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Medium,
                            fontFamily = FontFamily.Monospace,
                            letterSpacing = 2.sp,
                        ),
                        color = RuntimeTheme.colorScheme.secondary,
                    )
                }

                Button(
                    onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("WebChat Direct URL", directUrl))
                        Toast.makeText(context, context.getString(R.string.home_webchat_copied), Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(vertical = 10.dp),
                ) {
                    RuntimeIcon(RuntimeIconName.Copy, Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.home_webchat_copy))
                }

                Text(
                    text = stringResource(R.string.home_webchat_hint, status.activeConnections),
                    style = RuntimeTheme.typography.labelSmall,
                    color = RuntimeTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
