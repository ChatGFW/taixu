package top.wkbin.taixu.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import top.wkbin.taixu.ui.miuix.RuntimeTheme
import top.yukonga.miuix.kmp.basic.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.wkbin.taixu.core.model.McpToolInfo
import top.wkbin.taixu.core.model.McpToolPolicy
import top.wkbin.taixu.ui.components.RuntimeDropdownField
import top.wkbin.taixu.ui.components.RuntimeSwitch as Switch

private val RISK_OPTIONS = listOf(
    null to "继承",
    "low" to "低",
    "medium" to "中",
    "high" to "高",
    "critical" to "严重",
)

/** 单个 MCP 工具的紧凑策略行：名称/描述 + 风险覆盖 + 启停。 */
@Composable
internal fun McpToolPolicyRow(
    tool: McpToolInfo,
    policy: McpToolPolicy,
    onEnabledChange: (Boolean) -> Unit,
    onRiskChange: (String?) -> Unit,
) {
    val selectedIndex = RISK_OPTIONS.indexOfFirst { it.first == policy.riskOverride }.let { if (it < 0) 0 else it }
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(
                text = tool.name,
                style = MiuixTheme.textStyles.body2,
                fontFamily = FontFamily.Monospace,
                color = if (policy.enabled) RuntimeTheme.colorScheme.onSurface else RuntimeTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (tool.description.isNotBlank()) {
                Text(
                    text = tool.description.replace('\n', ' '),
                    style = MiuixTheme.textStyles.body2,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        RuntimeDropdownField(
            title = "风险",
            items = RISK_OPTIONS.map { it.second },
            selectedIndex = selectedIndex,
            onSelectedIndexChange = { onRiskChange(RISK_OPTIONS[it].first) },
            showValue = true,
            modifier = Modifier.weight(0.85f),
        )
        Switch(checked = policy.enabled, onCheckedChange = onEnabledChange)
    }
}
