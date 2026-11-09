package top.wkbin.taixu.ui.settings

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import top.wkbin.taixu.ui.miuix.HorizontalDivider
import top.wkbin.taixu.ui.miuix.RuntimeTheme
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import top.wkbin.taixu.core.datastore.AgentPreferences
import top.wkbin.taixu.harness.text.HashlineEditStats
import top.wkbin.taixu.ui.components.RuntimeIconName
import top.wkbin.taixu.ui.components.RuntimeTextButton as TextButton
import top.wkbin.taixu.ui.settings.LocalizedText as Text

/**
 * Hashline 编辑闸门与累计观测计数（自 [AgentSettingsScreen] 抽出）。
 * 偏好经 [AgentPreferences] 直连；计数来自 [HashlineEditStats]（DataStore 跨启动保留）。
 * 不自动改默认；观测启发式仅软提示（关 / 临时开 legacy）。
 */
internal fun LazyListScope.hashlineSettingsItems(agentPrefs: AgentPreferences) {
    item { AgentBlockTitle("Hashline 编辑") }
    item {
        val scope = rememberCoroutineScope()
        val readAnchors by agentPrefs.hashlineReadAnchorsDefault.collectAsStateWithLifecycle(initialValue = true)
        val opsEnabled by agentPrefs.hashlineEditOpsEnabled.collectAsStateWithLifecycle(initialValue = true)
        val legacyEnabled by agentPrefs.hashlineLegacyEditEnabled.collectAsStateWithLifecycle(initialValue = false)
        AgentSettingsGroup {
            AgentToggleRow(
                icon = RuntimeIconName.Code,
                title = "read 默认挂行锚",
                subtitle = "关闭后 read 默认不再输出 n:h| 前缀；工具参数仍可显式传 anchors=true",
                checked = readAnchors,
                onCheckedChange = { scope.launch { agentPrefs.setHashlineReadAnchorsDefault(it) } },
            )
            AgentToggleRow(
                icon = RuntimeIconName.Edit,
                title = "允许 Hashline ops 编辑",
                subtitle = "关闭后 edit 拒绝 ops，只能用 oldText/newText",
                checked = opsEnabled,
                onCheckedChange = { scope.launch { agentPrefs.setHashlineEditOpsEnabled(it) } },
            )
            AgentToggleRow(
                icon = RuntimeIconName.History,
                title = "允许 legacy oldText 编辑",
                subtitle = "默认关闭；开启后才接受 oldText/newText（兼容弱模型）",
                checked = legacyEnabled,
                onCheckedChange = { scope.launch { agentPrefs.setHashlineLegacyEditEnabled(it) } },
            )
            HashlineStatsRow(
                legacyEnabled = legacyEnabled,
                onDisableLegacy = { scope.launch { agentPrefs.setHashlineLegacyEditEnabled(false) } },
                onEnableLegacy = { scope.launch { agentPrefs.setHashlineLegacyEditEnabled(true) } },
            )
        }
    }
}

@androidx.compose.runtime.Composable
private fun HashlineStatsRow(
    legacyEnabled: Boolean,
    onDisableLegacy: () -> Unit,
    onEnableLegacy: () -> Unit,
) {
    var tick by remember { mutableIntStateOf(0) }
    val stats = remember(tick) { HashlineEditStats.snapshot() }
    val suggestDisableLegacy = remember(tick, legacyEnabled) {
        legacyEnabled && HashlineEditStats.shouldSuggestDisableLegacy()
    }
    val suggestEnableLegacy = remember(tick, legacyEnabled) {
        !legacyEnabled && HashlineEditStats.shouldSuggestEnableLegacy()
    }
    val opsOk = stats["ops_success"] ?: 0
    val opsStale = stats["ops_stale"] ?: 0
    val opsFail = stats["ops_other_failure"] ?: 0
    val legacyOk = stats["legacy_success"] ?: 0
    val legacyFail = stats["legacy_failure"] ?: 0
    val legacyRejected = stats["legacy_disabled"] ?: 0
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text("累计编辑计数（跨启动保留）", style = RuntimeTheme.typography.titleSmall)
        Text(
            "ops 成功 $opsOk · 锚点过期 $opsStale · 其他失败 $opsFail\n" +
                "legacy 成功 $legacyOk · legacy 失败 $legacyFail · legacy 被拒 $legacyRejected",
            style = RuntimeTheme.typography.bodySmall,
            color = RuntimeTheme.colorScheme.onSurfaceVariant,
        )
        if (suggestDisableLegacy) {
            Text(
                "观测提示：ops 已较稳且 legacy 仍有失败，可考虑关闭「允许 legacy oldText 编辑」",
                style = RuntimeTheme.typography.bodySmall,
                color = RuntimeTheme.colorScheme.tertiary,
            )
            TextButton(onClick = onDisableLegacy) {
                Text("一键关闭 legacy 编辑")
            }
        }
        if (suggestEnableLegacy) {
            Text(
                "观测提示：模型仍大量提交 oldText 被拒，且 ops 尚未明显跑通，可临时开启「允许 legacy oldText 编辑」",
                style = RuntimeTheme.typography.bodySmall,
                color = RuntimeTheme.colorScheme.tertiary,
            )
            TextButton(onClick = onEnableLegacy) {
                Text("一键临时开启 legacy 编辑")
            }
        }
        top.wkbin.taixu.ui.components.RuntimeActionRow(modifier = Modifier.fillMaxWidth()) {
            TextButton(onClick = {
                HashlineEditStats.flush()
                tick++
            }) { Text("刷新") }
            TextButton(onClick = { HashlineEditStats.reset(); tick++ }) {
                Text("清零", color = RuntimeTheme.colorScheme.error)
            }
        }
    }
}
