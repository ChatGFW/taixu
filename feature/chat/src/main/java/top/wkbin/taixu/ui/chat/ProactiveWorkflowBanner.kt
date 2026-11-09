package top.wkbin.taixu.ui.chat

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import top.wkbin.taixu.harness.workflow.ProactiveWorkflowSuggestion
import top.wkbin.taixu.ui.components.RuntimeButton as Button
import top.wkbin.taixu.ui.components.RuntimeIcon
import top.wkbin.taixu.ui.components.RuntimeIconButton as IconButton
import top.wkbin.taixu.ui.components.RuntimeIconName
import top.wkbin.taixu.ui.miuix.RuntimeTheme
import top.wkbin.taixu.ui.miuix.Surface
import top.yukonga.miuix.kmp.basic.Text

/** Event-driven workflow actions shown directly above the composer as interactive pills. */
@Composable
internal fun ProactiveWorkflowBanner(
    suggestions: List<ProactiveWorkflowSuggestion>,
    onLaunch: (ProactiveWorkflowSuggestion) -> Unit,
    onDismiss: (String) -> Unit,
) {
    if (suggestions.isEmpty()) return
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        suggestions.forEach { suggestion ->
            Surface(
                modifier = Modifier.clip(CircleShape),
                shape = CircleShape,
                color = RuntimeTheme.colorScheme.tertiaryContainer,
            ) {
                Row(
                    modifier = Modifier.padding(start = 12.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    RuntimeIcon(RuntimeIconName.Hub, Modifier.size(16.dp), tint = RuntimeTheme.colorScheme.primary)
                    Column(
                        modifier = Modifier
                            .clickable { onLaunch(suggestion) }
                            .padding(end = 4.dp),
                    ) {
                        Text(
                            text = if (suggestion.projectName.isNotBlank()) {
                                "${suggestion.projectName} · ${suggestion.title}"
                            } else {
                                suggestion.title
                            },
                            style = RuntimeTheme.typography.labelMedium,
                            fontWeight = FontWeight.Medium,
                            color = RuntimeTheme.colorScheme.onTertiaryContainer,
                            maxLines = 1,
                        )
                        if (suggestion.description.isNotBlank()) {
                            Text(
                                text = suggestion.description,
                                style = RuntimeTheme.typography.bodySmall,
                                color = RuntimeTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.75f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                    Button(
                        onClick = { onLaunch(suggestion) },
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    ) {
                        Text("运行", style = RuntimeTheme.typography.labelSmall)
                    }
                    IconButton(
                        onClick = { onDismiss(suggestion.workflowId) },
                        modifier = Modifier.size(24.dp),
                    ) {
                        RuntimeIcon(
                            RuntimeIconName.Close,
                            Modifier.size(12.dp),
                            tint = RuntimeTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.65f),
                        )
                    }
                }
            }
        }
    }
}
