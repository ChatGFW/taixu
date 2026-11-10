package top.wkbin.taixu.ui.home

import androidx.compose.foundation.layout.*
import top.wkbin.taixu.ui.miuix.RuntimeTheme
import top.yukonga.miuix.kmp.basic.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import top.wkbin.taixu.ui.components.*

@Composable
internal fun ResourceMetricCard(
    modifier: Modifier = Modifier,
    title: String,
    primaryValue: String,
    secondaryValue: String,
    progress: Float,
    progressText: String,
    extraInfo: String,
    accentColor: Color,
    icon: RuntimeIconName,
) {
    val effectiveAccent = when {
        progress >= 0.9f -> RuntimeTheme.colorScheme.error
        progress >= 0.8f -> RuntimeTheme.colorScheme.tertiary
        else -> accentColor
    }
    RuntimeCard(
        modifier = modifier,
        containerColor = RuntimeTheme.colorScheme.surfaceContainer,
        shape = RuntimeTheme.shapes.extraLarge,
        contentPadding = PaddingValues(16.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxHeight(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = title,
                    style = RuntimeTheme.typography.labelMedium.copy(fontWeight = FontWeight.Medium),
                    color = RuntimeTheme.colorScheme.onSurfaceVariant,
                )
                RuntimeIcon(
                    name = icon,
                    modifier = Modifier.size(16.dp),
                    tint = effectiveAccent,
                )
            }

            Text(
                text = primaryValue,
                style = RuntimeTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.Medium,
                ),
                color = RuntimeTheme.colorScheme.onSurface,
            )

            RuntimeLinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(10.dp)
                    .clip(RuntimeTheme.shapes.tiny),
                color = effectiveAccent,
                trackColor = RuntimeTheme.colorScheme.surfaceContainerHighest,
                wavy = true,
            )

            Text(
                text = "$progressText · $secondaryValue",
                style = RuntimeTheme.typography.bodySmall,
                color = if (progress >= 0.8f) effectiveAccent else RuntimeTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )

            Text(
                text = extraInfo,
                style = RuntimeTheme.typography.bodySmall,
                color = RuntimeTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

