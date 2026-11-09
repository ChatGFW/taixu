package top.wkbin.taixu.ui.settings

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import top.wkbin.taixu.ui.miuix.RuntimeTheme
import top.wkbin.taixu.ui.settings.LocalizedText as Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
internal fun McpSectionHeader(title: String, subtitle: String, count: Int) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 10.dp, bottom = 2.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            "$title ($count)",
            style = RuntimeTheme.typography.titleSmall,
            color = RuntimeTheme.colorScheme.primary,
        )
        Text(
            subtitle,
            style = RuntimeTheme.typography.bodyMedium,
            color = RuntimeTheme.colorScheme.onSurfaceVariant,
        )
    }
}

