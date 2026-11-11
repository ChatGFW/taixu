package top.wkbin.taixu.ui.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import top.wkbin.taixu.ui.components.RuntimeCard
import top.wkbin.taixu.ui.components.RuntimeIcon
import top.wkbin.taixu.ui.components.RuntimeIconName
import top.wkbin.taixu.ui.miuix.RuntimeSpacing
import top.wkbin.taixu.ui.miuix.RuntimeTheme
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** 编辑页折叠段：标题行走 BasicComponent，展开内容不包第二层卡片。 */
@Composable
internal fun ModelEditorExpandableSection(
    title: String,
    subtitle: String,
    icon: RuntimeIconName,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    RuntimeCard(
        modifier = Modifier.fillMaxWidth(),
        containerColor = RuntimeTheme.colorScheme.surfaceContainerLow,
        shape = RuntimeTheme.shapes.medium,
        contentPadding = PaddingValues(0.dp),
    ) {
        BasicComponent(
            startAction = {
                RuntimeIcon(
                    icon,
                    Modifier.padding(end = RuntimeSpacing.rowIconEnd).size(RuntimeSpacing.rowIconSize),
                    MiuixTheme.colorScheme.onSurface,
                )
            },
            endActions = {
                RuntimeIcon(
                    RuntimeIconName.ChevronDown,
                    Modifier.size(18.dp).rotate(if (expanded) 180f else 0f),
                    MiuixTheme.colorScheme.onSurfaceVariantActions,
                )
            },
            onClick = { onExpandedChange(!expanded) },
        ) {
            Text(
                title,
                fontSize = MiuixTheme.textStyles.headline1.fontSize,
                fontWeight = FontWeight.Medium,
                color = MiuixTheme.colorScheme.onBackground,
            )
            Text(
                subtitle,
                fontSize = MiuixTheme.textStyles.body2.fontSize,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )
        }
        AnimatedVisibility(visible = expanded) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                content = content,
            )
        }
    }
}
