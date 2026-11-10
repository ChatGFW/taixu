package top.wkbin.taixu.ui.miuix

import androidx.compose.ui.unit.dp

/** Page and card spacing matching the native Miuix preference layout. */
object RuntimeSpacing {
    val pageHorizontal = 12.dp
    val pageTop = 12.dp
    val pageBottom = 24.dp
    val cardGap = 12.dp
    val cardContent = 16.dp
    val rowIconSize = 24.dp
    val rowIconEnd = 6.dp
    val buttonMinHeight = 48.dp
    // 88dp：常见卡片内容宽可排下 3 个等分按钮，避免 2+1 折行。
    val actionMinWidth = 88.dp
    val actionGap = 12.dp
    val dialogActionGap = 20.dp
}
