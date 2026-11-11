package top.wkbin.taixu.ui.components

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

import android.widget.Toast
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import top.wkbin.taixu.ui.components.RuntimeIconButton as IconButton
import top.wkbin.taixu.ui.miuix.Surface
import top.yukonga.miuix.kmp.basic.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.res.stringResource
import top.wkbin.taixu.feature.components.R
import androidx.compose.ui.unit.dp
import coil3.compose.SubcomposeAsyncImage
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs
import top.wkbin.taixu.ui.components.RuntimeCircularProgressIndicator as CircularProgressIndicator
import top.wkbin.taixu.ui.components.RuntimeIcon
import top.wkbin.taixu.ui.components.RuntimeIconName
import top.wkbin.taixu.core.markdown.*

/**
 * Full-screen generated-image viewer with pinch zoom, pan, and Save As support.
 *
 * Platform [Dialog] is intentional for an immersive viewer; do not migrate to
 * RuntimeAlertDialog / WindowDialog card chrome.
 */
@Composable
internal fun ImagePreviewDialog(block: MdRemoteMedia, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val hostRoots = LocalSandboxHostRoots.current
    val mediaModel = remember(block.url, hostRoots) { resolveMediaSource(block.url, hostRoots) }
    val mimeType = remember(block.url) { imageMimeType(block.url) }
    var saving by remember(block.url) { mutableStateOf(false) }
    var scale by remember(block.url) { mutableFloatStateOf(1f) }
    var offsetX by remember(block.url) { mutableFloatStateOf(0f) }
    var panOffsetY by remember(block.url) { mutableFloatStateOf(0f) }
    // This state is read only inside graphicsLayer lambdas below, so drag frames invalidate the
    // render layers rather than recomposing the Coil image and the entire full-screen dialog.
    var dismissOffsetY by remember(block.url) { mutableFloatStateOf(0f) }
    val density = LocalDensity.current
    val dismissThresholdPx = with(density) { 140.dp.toPx() }
    val fadeDistancePx = with(density) { 360.dp.toPx() }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        Surface(color = Color.Transparent, modifier = Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxSize()) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            val progress = (abs(dismissOffsetY) / fadeDistancePx).coerceIn(0f, 1f)
                            alpha = 1f - progress * 0.82f
                        }
                        .background(Color.Black),
                )
                SubcomposeAsyncImage(
                    model = remember(mediaModel) { imageRequest(context, mediaModel) },
                    contentDescription = block.description.ifBlank { stringResource(R.string.components_md_image_preview) },
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(12.dp)
                        .graphicsLayer {
                            val progress = (abs(dismissOffsetY) / fadeDistancePx).coerceIn(0f, 1f)
                            val dismissScale = 1f - progress * 0.14f
                            scaleX = scale * dismissScale
                            scaleY = scale * dismissScale
                            translationX = offsetX
                            translationY = panOffsetY + dismissOffsetY
                            alpha = 1f - progress * 0.72f
                        }
                        .pointerInput(block.url) {
                            awaitEachGesture {
                                awaitFirstDown(requireUnconsumed = false)
                                var pointersStillPressed: Boolean
                                do {
                                    val event = awaitPointerEvent()
                                    val zoomChange = event.calculateZoom()
                                    val panChange = event.calculatePan()
                                    val pressedPointers = event.changes.count { it.pressed }
                                    val nextScale = (scale * zoomChange).coerceIn(1f, 6f)
                                    val isZooming = pressedPointers > 1 || abs(zoomChange - 1f) > 0.001f

                                    if (isZooming || scale > 1.01f || nextScale > 1.01f) {
                                        scale = nextScale
                                        dismissOffsetY = 0f
                                        if (scale <= 1.01f) {
                                            scale = 1f
                                            offsetX = 0f
                                            panOffsetY = 0f
                                        } else {
                                            offsetX += panChange.x
                                            panOffsetY += panChange.y
                                        }
                                    } else if (abs(panChange.y) >= abs(panChange.x)) {
                                        // At 1x, a one-finger vertical drag dismisses the viewer.
                                        dismissOffsetY += panChange.y
                                    }
                                    event.changes.forEach { change ->
                                        if (change.positionChanged()) change.consume()
                                    }
                                    pointersStillPressed = event.changes.any { it.pressed }
                                } while (pointersStillPressed)

                                if (scale <= 1.01f && abs(dismissOffsetY) >= dismissThresholdPx) {
                                    onDismiss()
                                } else if (dismissOffsetY != 0f) {
                                    val startOffset = dismissOffsetY
                                    scope.launch {
                                        Animatable(startOffset).animateTo(0f, spring()) {
                                            dismissOffsetY = value
                                        }
                                    }
                                }
                            }
                        },
                ) {
                    when (painter.state.collectAsState().value) {
                        is coil3.compose.AsyncImagePainter.State.Error -> Text(
                            text = stringResource(R.string.components_md_image_load_failed),
                            color = Color.White,
                            modifier = Modifier.align(Alignment.Center),
                        )
                        is coil3.compose.AsyncImagePainter.State.Success -> androidx.compose.foundation.Image(
                            painter = painter,
                            contentDescription = null,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxSize(),
                        )
                        else -> CircularProgressIndicator(
                            modifier = Modifier.align(Alignment.Center).size(32.dp),
                            color = Color.White,
                        )
                    }
                }

                Row(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(16.dp)
                        .graphicsLayer {
                            val progress = (abs(dismissOffsetY) / fadeDistancePx).coerceIn(0f, 1f)
                            alpha = 1f - progress
                        },
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    IconButton(
                        onClick = {
                            if (!saving) scope.launch {
                                saving = true
                                val saved = withContext(Dispatchers.IO) {
                                    saveImageToGallery(context, mediaModel, mimeType)
                                }
                                saving = false
                                Toast.makeText(
                                    context,
                                    context.getString(if (saved) R.string.components_md_image_saved else R.string.components_md_image_save_failed),
                                    Toast.LENGTH_SHORT,
                                ).show()
                            }
                        },
                        enabled = !saving,
                        contentDescription = stringResource(R.string.components_md_save_image),
                    ) {
                        if (saving) {
                            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = Color.White)
                        } else {
                            RuntimeIcon(RuntimeIconName.Download, Modifier.size(22.dp), Color.White)
                        }
                    }
                    IconButton(onClick = onDismiss, contentDescription = stringResource(R.string.components_close)) {
                        RuntimeIcon(RuntimeIconName.Close, Modifier.size(22.dp), Color.White)
                    }
                }
            }
        }
    }
}
