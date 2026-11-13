package top.wkbin.taixu.ui.chat

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import top.wkbin.taixu.feature.components.R as ComponentsR
import top.wkbin.taixu.ui.components.RuntimeAdaptiveMotion
import top.wkbin.taixu.ui.miuix.RuntimeTheme
import top.wkbin.taixu.ui.miuix.Surface

internal val CompactTopBarClearance = 48.dp
internal val ResizeHandleWidth = 12.dp

/** Drag target between conversation and companion; reports the drag as a fraction of [innerWidth]. */
@Composable
internal fun CompanionResizeHandle(innerWidth: Dp, onDragFraction: (Float) -> Unit) {
    val innerPx = with(LocalDensity.current) { innerWidth.toPx() }.coerceAtLeast(1f)
    val direction = if (LocalLayoutDirection.current == LayoutDirection.Rtl) -1f else 1f
    val label = stringResource(ComponentsR.string.components_companion_resize)
    Box(
        Modifier.width(ResizeHandleWidth).fillMaxHeight()
            .semantics { contentDescription = label }
            .draggable(rememberDraggableState { delta -> onDragFraction(delta * direction / innerPx) }, Orientation.Horizontal),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier.width(4.dp).height(40.dp).clip(RuntimeTheme.shapes.hairline)
                .background(RuntimeTheme.colorScheme.outlineVariant),
        )
    }
}

/** 紧凑态伴随整页：无 scrim；alpha keep-alive。 */
@Composable
internal fun CompanionFullPage(
    visible: Boolean,
    keepAlive: Boolean,
    bottomInsets: WindowInsets,
    content: @Composable () -> Unit,
) {
    val progress by animateFloatAsState(
        if (visible) 1f else 0f,
        RuntimeAdaptiveMotion.float,
        label = "companionFullPage",
    )
    if (!keepAlive && !visible && progress <= 0.001f) return
    Surface(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer {
                alpha = progress
                if (progress < 0.001f) translationY = size.height
            },
        color = RuntimeTheme.colorScheme.background,
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(top = CompactTopBarClearance)
                .windowInsetsPadding(bottomInsets)
                .padding(horizontal = 8.dp, vertical = 4.dp),
        ) {
            content()
        }
    }
}

@Composable
internal fun CompanionOverlay(
    progress: Float,
    width: Dp,
    bottomInsets: WindowInsets,
    onDismiss: () -> Unit,
    content: @Composable () -> Unit,
) {
    val widthPx = with(LocalDensity.current) { width.toPx() }
    val direction = if (LocalLayoutDirection.current == LayoutDirection.Rtl) -1f else 1f
    val scrim = RuntimeTheme.colorScheme.scrim
    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier.fillMaxSize()
                .drawBehind { drawRect(scrim.copy(alpha = 0.32f * progress)) }
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClickLabel = stringResource(ComponentsR.string.components_close),
                    onClick = onDismiss,
                ),
        )
        Surface(
            modifier = Modifier.align(Alignment.CenterEnd).width(width).fillMaxHeight()
                .graphicsLayer { translationX = (1f - progress) * widthPx * direction },
            color = RuntimeTheme.colorScheme.background,
            shadowElevation = 6.dp,
        ) {
            Box(Modifier.fillMaxSize().statusBarsPadding().windowInsetsPadding(bottomInsets).padding(8.dp)) {
                content()
            }
        }
    }
}
