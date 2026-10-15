package top.wkbin.taixu.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.AnchoredDraggableDefaults
import androidx.compose.foundation.gestures.AnchoredDraggableState
import androidx.compose.foundation.gestures.DraggableAnchors
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.anchoredDraggable
import androidx.compose.foundation.gestures.animateTo
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import top.wkbin.taixu.feature.components.R
import kotlin.math.abs

private enum class PushDrawerValue { Closed, Open }

/** Same-window push drawer, using Compose's velocity-aware drag and anchor settling. */
@Composable
fun RuntimePushNavigationDrawer(
    visible: Boolean,
    onDismiss: () -> Unit,
    onOpen: (() -> Unit)?,
    drawerContent: @Composable () -> Unit,
    content: @Composable () -> Unit,
) {
    BoxWithConstraints(Modifier.fillMaxSize().clipToBounds()) {
        val drawerWidth = min(maxWidth * 0.78f, 320.dp)
        val widthPx = with(LocalDensity.current) { drawerWidth.toPx() }
        val direction = if (LocalLayoutDirection.current == LayoutDirection.Rtl) -1f else 1f
        val state = remember {
            AnchoredDraggableState(
                initialValue = if (visible) PushDrawerValue.Open else PushDrawerValue.Closed,
                anchors = DraggableAnchors {
                    PushDrawerValue.Closed at 0f
                    PushDrawerValue.Open at widthPx
                },
            )
        }
        SideEffect {
            state.updateAnchors(
                DraggableAnchors {
                    PushDrawerValue.Closed at 0f
                    PushDrawerValue.Open at widthPx
                },
                newTarget = state.targetValue,
            )
        }
        val motion = remember {
            spring<Float>(
                dampingRatio = Spring.DampingRatioNoBouncy,
                stiffness = Spring.StiffnessMediumLow,
            )
        }
        val interactions = remember { MutableInteractionSource() }
        val dragging by interactions.collectIsDraggedAsState()
        val fling = AnchoredDraggableDefaults.flingBehavior(state, animationSpec = motion)
        val latestVisible by rememberUpdatedState(visible)
        val latestDismiss by rememberUpdatedState(onDismiss)
        val latestOpen by rememberUpdatedState(onOpen)
        val scope = rememberCoroutineScope()
        fun progress() = (state.offset / widthPx).takeIf { it.isFinite() }?.coerceIn(0f, 1f) ?: 0f
        val revealed by remember { derivedStateOf { state.offset > 0.5f } }

        LaunchedEffect(visible, widthPx) {
            state.animateTo(if (visible) PushDrawerValue.Open else PushDrawerValue.Closed, motion)
        }
        // Synchronize gestures only after release and settling, including interrupted openings.
        LaunchedEffect(state) {
            snapshotFlow {
                state.settledValue.takeIf {
                    !dragging && !state.isAnimationRunning &&
                        abs(state.offset - state.anchors.positionOf(it)) < 0.5f
                }
            }.filterNotNull().collect { settled ->
                if (settled == PushDrawerValue.Open && !latestVisible) latestOpen?.invoke()
                if (settled == PushDrawerValue.Closed && latestVisible) latestDismiss()
            }
        }
        val close: () -> Unit = {
            scope.launch {
                state.animateTo(PushDrawerValue.Closed, motion)
                latestDismiss()
            }
        }
        BackHandler(enabled = visible || revealed, onBack = close)
        val dragModifier = Modifier.anchoredDraggable(
            state = state,
            orientation = Orientation.Horizontal,
            interactionSource = interactions,
            flingBehavior = fling,
        )

        Box(
            Modifier.fillMaxSize().then(
                if (visible || state.settledValue == PushDrawerValue.Open) dragModifier else Modifier,
            ),
        ) {
            // Keep the drawer composed so the first drag does not build the session list mid-frame.
            Box(
                Modifier.align(Alignment.CenterStart).width(drawerWidth).fillMaxHeight()
                    .graphicsLayer { translationX = (progress() - 1f) * widthPx * 0.22f * direction }
                    .then(if (!revealed) Modifier.clearAndSetSemantics {} else Modifier),
            ) { drawerContent() }

            Box(
                Modifier.fillMaxSize().graphicsLayer {
                    translationX = state.offset.takeIf { it.isFinite() }?.times(direction) ?: 0f
                    shape = RoundedCornerShape(
                        topStart = 16.dp * progress(), bottomStart = 16.dp * progress(),
                    )
                    clip = progress() > 0f
                    shadowElevation = 8.dp.toPx() * progress()
                }.background(MaterialTheme.colorScheme.background),
            ) {
                Box(
                    Modifier.fillMaxSize()
                        .then(if (revealed) Modifier.clearAndSetSemantics {} else Modifier),
                ) { content() }
                if (revealed) {
                    val scrim = MaterialTheme.colorScheme.scrim
                    Box(
                        Modifier.fillMaxSize()
                            .drawBehind { drawRect(scrim.copy(alpha = 0.16f * progress())) }
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClickLabel = stringResource(R.string.components_close),
                                onClick = close,
                            ),
                    )
                }
            }
        }
        // This node stays mounted for the whole opening drag, even after the scrim appears.
        Box(
            Modifier.align(Alignment.CenterStart).width(32.dp).fillMaxHeight()
                .anchoredDraggable(
                    state = state,
                    orientation = Orientation.Horizontal,
                    enabled = onOpen != null && !visible && state.settledValue == PushDrawerValue.Closed,
                    interactionSource = interactions,
                    flingBehavior = fling,
                ),
        )
    }
}
