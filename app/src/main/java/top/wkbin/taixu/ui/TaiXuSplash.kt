package top.wkbin.taixu.ui

import android.animation.ValueAnimator
import android.graphics.drawable.Animatable as DrawableAnimatable
import android.graphics.drawable.Animatable2
import android.graphics.drawable.Drawable
import android.widget.ImageView
import androidx.appcompat.content.res.AppCompatResources
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.doOnAttach
import kotlinx.coroutines.delay
import top.wkbin.taixu.R

/** 与 res/animator/taixu_splash_*_anim.xml 的最长时长（420ms）一致。 */
private const val SPLASH_PLAYBACK_GUARD_MS = 540L

internal fun splashPlaybackGuardDelayMs(durationScale: Float): Long =
    (SPLASH_PLAYBACK_GUARD_MS * durationScale.coerceAtLeast(1f)).toLong()

/** 穿透退场时长，与旧系统闪屏退出节奏保持一致。 */
private const val SPLASH_EXIT_DURATION_MS = 280

/** 系统闪屏图标容器尺寸，保证应用内 Logo 与系统闪屏视觉大小一致。 */
private const val SPLASH_ICON_SIZE_DP = 288

/**
 * 应用内启动页。
 *
 * 为什么不在系统 SplashScreen 里播动画（对齐 KernelSU-Next）：
 * Android 12+ 的启动图标动画由系统启动窗口持有，时长和生命周期都不归应用控制。
 * ROM 可能直接跳过动画、停在首帧，或在应用首帧绘制、闪屏退场时把动画截断；
 * `setOnExitAnimationListener` 拿到的只是一个即将被移除的图标视图，无法保证播完。
 *
 * 因此这里把系统闪屏退化成“纯色底 + 透明图标”的冷启动首帧兜底，
 * Logo 动画在应用内用 AnimatedVectorDrawable 播放，并且由动画结束回调驱动退场，
 * 任何设备、任何启动速度下都不会被提前切断（KernelSU-Next 用的是同一思路）。
 */
@Composable
fun TaiXuSplash(
    onFinished: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val animatorsEnabled = remember { ValueAnimator.areAnimatorsEnabled() }
    // 系统关闭动画时直接显示拼合完成的静态 Logo，避免停在不可见的首帧。
    val icon = remember(animatorsEnabled) {
        AppCompatResources.getDrawable(
            context,
            if (animatorsEnabled) R.drawable.taixu_splash_animated else R.drawable.taixu_splash,
        )
    }
    var playbackFinished by remember { mutableStateOf(!animatorsEnabled) }
    val exitProgress = remember { Animatable(0f) }

    // 动画播完才允许退场：注册 AVD 结束回调，避免用固定时长猜动画进度。
    DisposableEffect(icon) {
        val callback = object : Animatable2.AnimationCallback() {
            override fun onAnimationEnd(drawable: Drawable?) {
                playbackFinished = true
            }
        }
        val animatable = icon as? Animatable2
        animatable?.registerAnimationCallback(callback)
        onDispose { animatable?.unregisterAnimationCallback(callback) }
    }

    // 兜底：极少数 ROM 不上报动画结束回调，按已知时长放行，仍不会提前切断。
    LaunchedEffect(icon, animatorsEnabled) {
        if (!animatorsEnabled) return@LaunchedEffect
        delay(splashPlaybackGuardDelayMs(ValueAnimator.getDurationScale()))
        playbackFinished = true
    }

    LaunchedEffect(playbackFinished) {
        if (!playbackFinished) return@LaunchedEffect
        exitProgress.animateTo(
            targetValue = 1f,
            animationSpec = tween(
                durationMillis = SPLASH_EXIT_DURATION_MS,
                easing = CubicBezierEasing(0.2f, 0f, 0f, 1f),
            ),
        )
        onFinished()
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .graphicsLayer { alpha = 1f - exitProgress.value }
            .background(colorResource(R.color.taixu_splash_background))
            .blockSplashTouches(),
        contentAlignment = Alignment.Center,
    ) {
        AndroidView(
            factory = { ctx ->
                ImageView(ctx).apply {
                    scaleType = ImageView.ScaleType.FIT_CENTER
                    setImageDrawable(icon)
                    doOnAttach { (icon as? DrawableAnimatable)?.start() }
                }
            },
            modifier = Modifier
                .size(SPLASH_ICON_SIZE_DP.dp)
                .graphicsLayer {
                    val progress = exitProgress.value
                    scaleX = 1f + 0.3f * progress
                    scaleY = 1f + 0.3f * progress
                },
        )
    }
}

/** 启动页期间吞掉触摸事件，避免落到尚未揭幕的主界面上。 */
private fun Modifier.blockSplashTouches(): Modifier = pointerInput(Unit) {
    awaitPointerEventScope {
        while (true) {
            awaitPointerEvent(PointerEventPass.Initial).changes.forEach { it.consume() }
        }
    }
}
