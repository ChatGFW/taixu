package top.wkbin.taixu.ui.settings.stats.widgets

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import top.wkbin.taixu.core.model.StatsLedger
import top.wkbin.taixu.ui.components.RuntimeIcon
import top.wkbin.taixu.ui.components.RuntimeIconButton as IconButton
import top.wkbin.taixu.ui.components.RuntimeIconName
import java.util.Locale

/**
 * 用量账本分区。
 *
 * 数据来自 `harness_usage` 这张 append-only 账本，是**真实计量值**，
 * 与同页「核心指标概览」的估算口径并行展示、互为参照。
 *
 * 交互沿用本页既有约定：
 *  - 右上角图标 → 明细弹窗（与 [StatsRankSection] 的「查看全部」同构）；
 *  - 区块内是紧凑指标格 + 构成条，趋势交给页面上的趋势图承接，
 *    这样「点击立柱看详情 / 左右滑动看趋势」两个既有手感不会被破坏。
 */
@Composable
fun StatsLedgerSection(
    ledger: StatsLedger,
    modifier: Modifier = Modifier,
) {
    var showDetail by remember { mutableStateOf(false) }

    StatsSectionCard(
        title = "用量账本（真实计量）",
        modifier = modifier,
        trailing = {
            IconButton(
                onClick = { showDetail = true },
                modifier = Modifier.size(24.dp),
            ) {
                RuntimeIcon(
                    name = RuntimeIconName.OpenInNew,
                    modifier = Modifier.size(15.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                )
            }
        },
    ) {
        LedgerMetricGrid(ledger = ledger)
        Spacer(modifier = Modifier.height(12.dp))
        LedgerCompositionBar(ledger = ledger)
    }

    if (showDetail) {
        LedgerDetailDialog(ledger = ledger, onDismiss = { showDetail = false })
    }
}

@Composable
private fun LedgerMetricGrid(ledger: StatsLedger) {
    val items = listOf(
        LedgerMetric(RuntimeIconName.Speed, "LLM 调用", "${ledger.calls} 次", "账本行数，已剔除修正行"),
        LedgerMetric(RuntimeIconName.Sparkles, "Token 合计", ledgerCompact(ledger.totalTokens), "输入 + 输出 + 推理"),
        LedgerMetric(RuntimeIconName.Chat, "输入 Token", ledgerCompact(ledger.inputTokens), "不含缓存命中读取"),
        LedgerMetric(RuntimeIconName.NavMessage, "输出 Token", ledgerCompact(ledger.outputTokens), "completion"),
        LedgerMetric(RuntimeIconName.Brain, "推理 Token", ledgerCompact(ledger.reasoningTokens), "估算口径下拿不到"),
        LedgerMetric(RuntimeIconName.Sparkles, "缓存命中率", ledgerPercent(ledger.cacheHitRate), "命中读取 ÷ (输入 + 命中读取)"),
        LedgerMetric(RuntimeIconName.Chat, "缓存读取", ledgerCompact(ledger.cacheReadTokens), "cacheRead"),
        LedgerMetric(RuntimeIconName.Sparkles, "缓存写入", ledgerCompact(ledger.cacheWriteTokens), "cacheWrite"),
    )

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        for (i in items.indices step 2) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                LedgerMetricTile(item = items[i], modifier = Modifier.weight(1f))
                if (i + 1 < items.size) {
                    LedgerMetricTile(item = items[i + 1], modifier = Modifier.weight(1f))
                } else {
                    Box(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

private data class LedgerMetric(
    val icon: RuntimeIconName,
    val label: String,
    val value: String,
    val hint: String,
)

@Composable
private fun LedgerMetricTile(
    item: LedgerMetric,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .height(76.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f))
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                RuntimeIcon(
                    name = item.icon,
                    modifier = Modifier.size(15.dp),
                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
                )
                Text(
                    text = item.label,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                text = item.value,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = item.hint,
                style = MaterialTheme.typography.bodySmall.copy(
                    fontSize = 9.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** Token 构成条：把账本四类真实 token 按比例铺开；缓存读取单列在图例中（不计入合计）。 */
@Composable
private fun LedgerCompositionBar(ledger: StatsLedger) {
    val segments = listOf(
        Triple("输入", ledger.inputTokens, MaterialTheme.colorScheme.primary),
        Triple("输出", ledger.outputTokens, MaterialTheme.colorScheme.secondary),
        Triple("推理", ledger.reasoningTokens, MaterialTheme.colorScheme.tertiary),
        Triple("缓存写入", ledger.cacheWriteTokens, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)),
    ).filter { (_, value, _) -> value > 0L }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = "Token 构成",
            style = MaterialTheme.typography.bodySmall.copy(
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            ),
        )

        if (segments.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "所选时间范围内没有账本记录",
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    ),
                )
            }
            return@Column
        }

        val total = segments.sumOf { it.second }.coerceAtLeast(1L)

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(22.dp)
                .clip(RoundedCornerShape(7.dp)),
        ) {
            for ((_, value, color) in segments) {
                Box(
                    modifier = Modifier
                        .weight(value.toFloat() / total.toFloat())
                        .fillMaxHeight()
                        .background(color),
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            for ((label, value, color) in segments) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(color),
                    )
                    Text(
                        text = "$label ${ledgerCompact(value)}",
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        ),
                        maxLines = 1,
                    )
                }
            }
        }

        Text(
            text = "缓存命中读取 ${ledgerCompact(ledger.cacheReadTokens)} 不计入合计：它是输入前缀的复用部分，计入会与输入双重计算。",
            style = MaterialTheme.typography.bodySmall.copy(
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
            ),
        )
    }
}

internal fun ledgerPercent(ratio: Double): String {
    if (ratio <= 0.0) return "0%"
    return String.format(Locale.US, "%.1f%%", ratio * 100.0)
}

internal fun ledgerCompact(value: Long): String {
    if (value < 1000L) return value.toString()
    if (value < 1_000_000L) {
        val k = value / 1000.0
        return String.format(Locale.US, if (k < 10) "%.1fk" else "%.0fk", k)
    }
    val m = value / 1_000_000.0
    return String.format(Locale.US, if (m < 10) "%.1fM" else "%.0fM", m)
}
