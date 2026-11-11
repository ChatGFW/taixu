package top.wkbin.taixu.ui.chat

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import top.wkbin.taixu.ui.miuix.ModalBottomSheet
import top.wkbin.taixu.ui.miuix.RuntimeTheme
import top.wkbin.taixu.ui.miuix.SheetState
import top.wkbin.taixu.ui.miuix.rememberModalBottomSheetState

/**
 * 聊天页统一底栏抽屉：委托项目 miuix `ModalBottomSheet`（底层 WindowBottomSheet），
 * 默认跳过半展开态，容器色取 surfaceContainerLow。命名保留 PlainBackdrop 以兼容既有调用。
 */
@Composable
internal fun PlainBackdropModalBottomSheet(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    sheetState: SheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    containerColor: androidx.compose.ui.graphics.Color = RuntimeTheme.colorScheme.surfaceContainerLow,
    content: @Composable ColumnScope.() -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        modifier = modifier,
        sheetState = sheetState,
        containerColor = containerColor,
        content = content,
    )
}
