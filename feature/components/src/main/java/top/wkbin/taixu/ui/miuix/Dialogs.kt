package top.wkbin.taixu.ui.miuix

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import top.wkbin.taixu.ui.components.RuntimeActionRow
import top.yukonga.miuix.kmp.theme.LocalContentColor
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.window.WindowBottomSheet
import top.yukonga.miuix.kmp.window.WindowDialog

/** True inside a dialog's confirm slot, where default-colored buttons take the primary role. */
internal val LocalDialogPrimaryAction = staticCompositionLocalOf { false }

/** True inside a dialog's action row, where text buttons become the miuix filled pair. */
internal val LocalDialogAction = staticCompositionLocalOf { false }

/**
 * True on dialog and sheet bodies. Those already sit on the card colour, so cards nested inside
 * swap to the miuix inset fill instead of vanishing white-on-white.
 */
internal val LocalRaisedSurface = staticCompositionLocalOf { false }

@Composable
fun AlertDialog(
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    dismissButton: (@Composable () -> Unit)? = null,
    title: (@Composable () -> Unit)? = null,
    text: (@Composable () -> Unit)? = null,
    icon: (@Composable () -> Unit)? = null,
) {
    // WindowDialog does not bound its content height; keep the action row on screen with the keyboard up.
    val windowHeight = LocalWindowInfo.current.containerDpSize.height
    val imeHeight = WindowInsets.ime.asPaddingValues().calculateBottomPadding()
    val maxHeight = ((windowHeight - imeHeight) * 0.8f).coerceAtLeast(240.dp)
    WindowDialog(show = true, onDismissRequest = onDismissRequest, modifier = modifier) {
        CompositionLocalProvider(LocalRaisedSurface provides true) {
        Column(Modifier.heightIn(max = maxHeight)) {
            if (icon != null) {
                Box(Modifier.fillMaxWidth().padding(bottom = 12.dp), contentAlignment = Alignment.Center) { icon() }
            }
            if (title != null) {
                val styles = MiuixTheme.textStyles
                val titleStyle = styles.title4.copy(fontWeight = FontWeight.Medium, textAlign = TextAlign.Center)
                MiuixTheme(colors = MiuixTheme.colorScheme, textStyles = styles.copy(main = titleStyle)) {
                    CompositionLocalProvider(LocalTextStyle provides titleStyle) {
                        Box(Modifier.fillMaxWidth().padding(bottom = 12.dp), contentAlignment = Alignment.Center) { title() }
                    }
                }
            }
            if (text != null) Box(Modifier.weight(1f, fill = false)) { text() }
            DialogActionRow(
                dismissButton,
                { CompositionLocalProvider(LocalDialogPrimaryAction provides true, content = confirmButton) },
                Modifier.padding(top = if (text != null) 12.dp else 0.dp),
            )
        }
        }
    }
}

/** miuix dialogs split the width evenly between actions; empty slots are dropped from the split. */
@Composable
private fun DialogActionRow(
    dismissButton: (@Composable () -> Unit)?,
    confirmButton: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    RuntimeActionRow(modifier, spacing = RuntimeSpacing.dialogActionGap, scrollable = true) {
        dismissButton?.invoke()
        confirmButton()
    }
}

enum class SheetValue { Hidden, PartiallyExpanded, Expanded }

@Stable
class SheetState internal constructor(private val confirmValueChange: (SheetValue) -> Boolean) {
    var isVisible by mutableStateOf(true)
        private set
    internal fun canDismiss() = confirmValueChange(SheetValue.Hidden)
    suspend fun hide() {
        if (canDismiss()) {
            isVisible = false
            delay(300)
        }
    }
    suspend fun show() { isVisible = true }
}

@Composable
fun rememberModalBottomSheetState(
    skipPartiallyExpanded: Boolean = false,
    confirmValueChange: (SheetValue) -> Boolean = { true },
): SheetState {
    val confirm by rememberUpdatedState(confirmValueChange)
    return remember { SheetState { confirm(it) } }
}

object BottomSheetDefaults {
    val ScrimColor = Color.Black.copy(alpha = 0.4f)
    @Composable fun DragHandle() = Unit
}

@Composable
fun ModalBottomSheet(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    sheetState: SheetState = rememberModalBottomSheetState(),
    shape: Shape = RuntimeTheme.shapes.extraLarge,
    containerColor: Color = RuntimeTheme.colorScheme.surface,
    contentColor: Color = RuntimeTheme.colorScheme.onSurface,
    tonalElevation: Dp = 0.dp,
    scrimColor: Color = BottomSheetDefaults.ScrimColor,
    dragHandle: (@Composable () -> Unit)? = null,
    contentWindowInsets: @Composable () -> WindowInsets = { WindowInsets.systemBars },
    content: @Composable ColumnScope.() -> Unit,
) {
    LaunchedEffect(sheetState) { sheetState.show() }
    var closing by remember { mutableStateOf(false) }
    val latestDismiss by rememberUpdatedState(onDismissRequest)
    // Every sheet shares the miuix sheet fill; callers already pad their own content (M3 sheets had none).
    WindowBottomSheet(
        show = sheetState.isVisible && !closing,
        modifier = modifier,
        allowDismiss = sheetState.canDismiss(),
        onDismissRequest = { if (sheetState.canDismiss()) closing = true },
        onDismissFinished = { if (closing) latestDismiss() },
        insideMargin = androidx.compose.ui.unit.DpSize(0.dp, 0.dp),
    ) {
        CompositionLocalProvider(LocalContentColor provides contentColor, LocalRaisedSurface provides true) {
            Column(Modifier.fillMaxWidth().imePadding(), content = content)
        }
    }
}
