package top.wkbin.taixu.ui.chat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import top.wkbin.taixu.ui.miuix.RuntimeTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import top.wkbin.taixu.ui.miuix.LocalTextStyle
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** Selection and inspection panels share a bottom sheet with fixed header/actions. */
@Composable
internal fun ChatBottomSheet(
    onDismissRequest: () -> Unit,
    title: @Composable () -> Unit,
    text: @Composable () -> Unit,
    confirmButton: @Composable () -> Unit = {},
    dismissButton: @Composable () -> Unit = {},
) {
    top.wkbin.taixu.ui.components.RuntimeModalBottomSheet(onDismissRequest = onDismissRequest,
        sheetState = top.wkbin.taixu.ui.miuix.rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = RuntimeTheme.shapes.extraLarge) {
        Column(Modifier.fillMaxWidth().imePadding().padding(horizontal = 24.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)) {
            CompositionLocalProvider(LocalTextStyle provides RuntimeTheme.typography.headlineSmall) { title() }
            Box(Modifier.fillMaxWidth().weight(1f, fill = false)) { text() }
            FlowRow(Modifier.fillMaxWidth().padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp, androidx.compose.ui.Alignment.End), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                dismissButton()
                confirmButton()
            }
        }
    }
}
