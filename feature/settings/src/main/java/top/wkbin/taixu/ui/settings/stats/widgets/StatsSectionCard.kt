package top.wkbin.taixu.ui.settings.stats.widgets

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import top.wkbin.taixu.ui.miuix.RuntimeTheme
import top.yukonga.miuix.kmp.basic.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

import top.wkbin.taixu.ui.components.RuntimeCard

@Composable
fun StatsSectionCard(
    title: String,
    modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null,
    child: @Composable () -> Unit,
) {
    RuntimeCard(
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = title,
                style = RuntimeTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Medium,
                    fontSize = 15.sp,
                ),
                color = RuntimeTheme.colorScheme.onSurface,
            )
            if (trailing != null) {
                trailing()
            }
        }
        child()
    }
}
