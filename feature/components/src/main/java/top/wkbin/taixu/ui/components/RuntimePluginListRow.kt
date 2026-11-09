package top.wkbin.taixu.ui.components

import top.wkbin.taixu.ui.miuix.RuntimeSpacing
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.basic.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/** A quiet inventory row inside a shared card; the whole row opens its details. */
@Composable
fun RuntimePluginListRow(title: String, subtitle: String, icon: RuntimeIconName,
    modifier: Modifier = Modifier, detail: String = "", status: String = "", onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null) {
    val interaction = if (onClick != null || onLongClick != null) {
        Modifier.combinedClickable(role = Role.Button, onClick = { onClick?.invoke() }, onLongClick = onLongClick)
    } else Modifier
    BasicComponent(
        modifier = modifier.then(interaction),
        startAction = {
            RuntimeIcon(icon, Modifier.padding(end = RuntimeSpacing.rowIconEnd).size(RuntimeSpacing.rowIconSize),
                MiuixTheme.colorScheme.onSurface)
        },
        endActions = {
            if (status.isNotBlank()) Text(status, Modifier.widthIn(max = 76.dp), style = MiuixTheme.textStyles.footnote1,
                color = MiuixTheme.colorScheme.onSurfaceVariantActions, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (onClick != null) RuntimeIcon(RuntimeIconName.ChevronRight, Modifier.size(18.dp), MiuixTheme.colorScheme.onSurfaceVariantActions)
        },
    ) {
        Text(title, style = MiuixTheme.textStyles.headline1, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(subtitle, style = MiuixTheme.textStyles.body2, color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (detail.isNotBlank()) Text(detail, style = MiuixTheme.textStyles.body2,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}
