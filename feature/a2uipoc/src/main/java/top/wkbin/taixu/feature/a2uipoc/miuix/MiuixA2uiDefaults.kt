package top.wkbin.taixu.feature.a2uipoc.miuix

import androidx.a2ui.model.protocol.A2uiException
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import top.wkbin.taixu.feature.a2uipoc.TaiXuA2uiSkeleton
import top.wkbin.taixu.feature.a2uipoc.TaiXuA2uiSkeletonGroup

internal object MiuixA2uiDefaults {
    @Composable
    fun LoadingIndicatorGroup(content: @Composable (Modifier) -> Unit) = TaiXuA2uiSkeletonGroup(content)

    @Composable
    fun LoadingIndicator(modifier: Modifier = Modifier, index: Int = 0) = TaiXuA2uiSkeleton(modifier, index)

    @Composable
    fun ErrorFallback(exception: A2uiException, modifier: Modifier = Modifier) {
        androidx.compose.foundation.layout.Box(modifier) { A2uiSurfaceDefaults.ErrorFallback(exception) }
    }

    @Composable
    fun <S> transitionSpec(): AnimatedContentTransitionScope<S>.() -> ContentTransform = {
        fadeIn() togetherWith fadeOut()
    }
}
