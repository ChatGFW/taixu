package top.wkbin.taixu.ui.chat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import top.wkbin.taixu.ui.components.RuntimeDialogActions
import top.wkbin.taixu.ui.components.RuntimeModalBottomSheet
import top.wkbin.taixu.ui.miuix.LocalTextStyle
import top.wkbin.taixu.ui.miuix.RuntimeTheme
import top.wkbin.taixu.ui.miuix.SheetValue
import top.wkbin.taixu.ui.miuix.rememberModalBottomSheetState

/** Selection and inspection panels share a bottom sheet with fixed header/actions. */
@Composable
internal fun ChatBottomSheet(
    onDismissRequest: () -> Unit,
    title: @Composable () -> Unit,
    text: @Composable () -> Unit,
    confirmButton: @Composable () -> Unit = {},
    dismissButton: (@Composable () -> Unit)? = null,
    dismissEnabled: Boolean = true,
) {
    val canDismiss by rememberUpdatedState(dismissEnabled)
    RuntimeModalBottomSheet(
        onDismissRequest = { if (canDismiss) onDismissRequest() },
        sheetState = rememberModalBottomSheetState(
            skipPartiallyExpanded = true,
            confirmValueChange = { canDismiss || it != SheetValue.Hidden },
        ),
        shape = RuntimeTheme.shapes.extraLarge,
    ) {
        // 底 inset / ime 由 ModalBottomSheet 统一处理，这里只留内容边距。
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            CompositionLocalProvider(LocalTextStyle provides RuntimeTheme.typography.headlineSmall) { title() }
            Box(Modifier.fillMaxWidth().weight(1f, fill = false)) { text() }
            RuntimeDialogActions(
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                dismissButton = dismissButton,
                confirmButton = confirmButton,
            )
        }
    }
}
