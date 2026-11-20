package top.wkbin.taixu.ui.miuix

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import kotlinx.coroutines.delay
import top.wkbin.taixu.ui.components.LocalRuntimeBackdrop
import top.wkbin.taixu.ui.components.runtimeOverlayBackdrop
import top.yukonga.miuix.kmp.glass.GlassBottomSheet
import top.yukonga.miuix.kmp.glass.GlassDialog
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
    val body: @Composable () -> Unit = {
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
                confirmButton,
                Modifier.padding(top = if (text != null) 12.dp else 0.dp),
            )
        }
        }
    }
    RuntimeDialog(onDismissRequest = onDismissRequest, modifier = modifier, content = body)
}

/** Shared container for dialogs that supply their own content and actions. */
@Composable
fun RuntimeDialog(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val backdrop = runtimeOverlayBackdrop()
    if (backdrop != null) {
        GlassDialog(
            show = true,
            backdrop = backdrop,
            renderInRootScaffold = false,
            onDismissRequest = onDismissRequest,
            modifier = modifier,
        ) {
            CompositionLocalProvider(LocalRuntimeBackdrop provides null, content = content)
        }
    } else {
        WindowDialog(show = true, onDismissRequest = onDismissRequest, modifier = modifier, content = content)
    }
}

/** miuix dialogs split the width evenly between actions; empty slots are dropped from the split. */
@Composable
private fun DialogActionRow(
    dismissButton: (@Composable () -> Unit)?,
    confirmButton: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    top.wkbin.taixu.ui.components.RuntimeDialogActions(
        modifier = modifier,
        dismissButton = dismissButton,
        confirmButton = confirmButton,
    )
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
    contentWindowInsets: @Composable () -> WindowInsets = {
        // 只垫底：systemBars 已含 captionBar；不含 ime（外层另 imePadding）；不含水平（sheet 底对齐限宽居中）。
        WindowInsets.systemBars.union(WindowInsets.displayCutout).only(WindowInsetsSides.Bottom)
    },
    content: @Composable ColumnScope.() -> Unit,
) {
    LaunchedEffect(sheetState) { sheetState.show() }
    var closing by remember { mutableStateOf(false) }
    val latestDismiss by rememberUpdatedState(onDismissRequest)
    // Compose WindowInsets 在 Scaffold 消费后常为 0；独立 sheet 窗口里同样不可靠。
    // 用 Activity 根窗口原始 inset 兜底，保证「新增自定义技能」等高表单不贴导航栏。
    val bottomInset = rememberSheetBottomInset(contentWindowInsets)
    val body: @Composable () -> Unit = {
        CompositionLocalProvider(
            LocalContentColor provides contentColor,
            LocalRaisedSurface provides true,
            LocalRuntimeBackdrop provides null,
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(bottom = bottomInset)
                    .imePadding(),
                content = content,
            )
        }
    }
    val show = sheetState.isVisible && !closing
    val dismiss = { if (sheetState.canDismiss()) closing = true }
    val dismissFinished = { if (closing) latestDismiss() }
    val insideMargin = androidx.compose.ui.unit.DpSize.Zero
    val backdrop = runtimeOverlayBackdrop()
    if (backdrop != null) {
        GlassBottomSheet(
            show = show,
            backdrop = backdrop,
            renderInRootScaffold = false,
            modifier = modifier,
            allowDismiss = sheetState.canDismiss(),
            onDismissRequest = dismiss,
            onDismissFinished = dismissFinished,
            insideMargin = insideMargin,
            containerColor = containerColor,
            dimColor = scrimColor,
            content = body,
        )
    } else {
        WindowBottomSheet(
            show = show,
            modifier = modifier,
            allowDismiss = sheetState.canDismiss(),
            onDismissRequest = dismiss,
            onDismissFinished = dismissFinished,
            insideMargin = insideMargin,
            content = body,
        )
    }
}

/** 取 Compose 声明 inset 与根窗口原始 systemBars∪displayCutout 底边的较大值。 */
@Composable
private fun rememberSheetBottomInset(
    contentWindowInsets: @Composable () -> WindowInsets,
): Dp {
    val density = LocalDensity.current
    val composeBottomPx = contentWindowInsets().getBottom(density)
    val view = LocalView.current
    val configuration = LocalConfiguration.current
    val rawBottomPx = remember(view, configuration.orientation, configuration.screenHeightDp) {
        ViewCompat.getRootWindowInsets(view)
            ?.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            ?.bottom
            ?: 0
    }
    return with(density) { maxOf(composeBottomPx, rawBottomPx).toDp() }
}
