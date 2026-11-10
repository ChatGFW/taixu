package top.wkbin.taixu.ui.chat

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import top.wkbin.taixu.feature.chat.R
import top.wkbin.taixu.harness.mcp.McpWorkspaceRecommender
import top.wkbin.taixu.ui.components.RuntimeActionRow
import top.wkbin.taixu.ui.components.RuntimeButton
import top.wkbin.taixu.ui.components.RuntimeTextButton as TextButton
import top.wkbin.taixu.ui.miuix.RuntimeTheme
import top.wkbin.taixu.ui.miuix.Surface
import top.yukonga.miuix.kmp.basic.Text
/** 工作区感知的 MCP 预设推荐横幅：每条推荐展示启用理由，可一键启用或忽略。 */
@Composable
internal fun McpRecommendationBanner(
    recommendations: List<McpWorkspaceRecommender.Recommendation>,
    onEnable: (String) -> Unit,
    onInstall: () -> Unit,
    onDismiss: (String) -> Unit,
) {
    if (recommendations.isEmpty()) return
    recommendations.forEach { recommendation ->
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            shape = RuntimeTheme.shapes.small,
            color = RuntimeTheme.colorScheme.secondaryContainer,
        ) {
            Column(
                Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Column {
                    Text(
                        recommendation.presetName,
                        color = RuntimeTheme.colorScheme.onSecondaryContainer,
                        style = RuntimeTheme.typography.labelLarge,
                    )
                    Text(
                        recommendation.reason,
                        color = RuntimeTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f),
                        style = RuntimeTheme.typography.bodySmall,
                    )
                }
                RuntimeActionRow {
                RuntimeButton(onClick = { if (recommendation.needsInstall) onInstall() else onEnable(recommendation.presetId) }) {
                    Text(stringResource(if (recommendation.needsInstall) R.string.chat_install_mcp else R.string.chat_enable), maxLines = 1)
                }
                TextButton(onClick = { onDismiss(recommendation.presetId) }) {
                    Text(stringResource(R.string.chat_ignore), maxLines = 1)
                }
                }
            }
        }
    }
}
