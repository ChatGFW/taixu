package top.wkbin.taixu.ui.components

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import top.wkbin.taixu.ui.miuix.LocalDialogAction
import top.wkbin.taixu.ui.miuix.RuntimeSpacing

/** Equal action cells, with consistent heights and wrapping for narrow windows and large text. */
@Composable
fun RuntimeActionRow(
    modifier: Modifier = Modifier,
    spacing: Dp = RuntimeSpacing.actionGap,
    scrollable: Boolean = false,
    maxColumns: Int = Int.MAX_VALUE,
    content: @Composable () -> Unit,
) {
    val fontScale = LocalDensity.current.fontScale.coerceAtLeast(1f)
    CompositionLocalProvider(LocalDialogAction provides true) {
        val scrolling = if (scrollable) Modifier.verticalScroll(rememberScrollState()) else Modifier
        Layout(content = content, modifier = modifier.then(scrolling)) { actions, constraints ->
            if (actions.isEmpty()) return@Layout layout(constraints.minWidth, constraints.minHeight) {}
            val gap = spacing.roundToPx()
            val minWidth = (RuntimeSpacing.actionMinWidth * fontScale).roundToPx()
            val width = if (constraints.hasBoundedWidth) constraints.maxWidth else {
                actions.maxOf { it.maxIntrinsicWidth(androidx.compose.ui.unit.Constraints.Infinity) }
                    .coerceAtLeast(minWidth) * actions.size + gap * (actions.size - 1)
            }
            val columns = ((width + gap) / (minWidth + gap))
                .coerceIn(1, minOf(actions.size, maxColumns.coerceAtLeast(1)))
            val cellWidth = ((width - gap * (columns - 1)) / columns).coerceAtLeast(0)
            val cellHeight = actions.maxOf { it.minIntrinsicHeight(cellWidth) }
                .coerceAtLeast(RuntimeSpacing.buttonMinHeight.roundToPx())
            val cells = actions.map {
                it.measure(androidx.compose.ui.unit.Constraints.fixed(cellWidth, cellHeight))
            }
            val rows = (cells.size + columns - 1) / columns
            layout(width, rows * cellHeight + (rows - 1) * gap) {
                cells.forEachIndexed { index, cell ->
                    val row = index / columns
                    val rowCount = minOf(columns, cells.size - row * columns)
                    val rowWidth = rowCount * cellWidth + (rowCount - 1) * gap
                    val start = (width - rowWidth) / 2
                    cell.placeRelative(start + (index % columns) * (cellWidth + gap), row * (cellHeight + gap))
                }
            }
        }
    }
}
