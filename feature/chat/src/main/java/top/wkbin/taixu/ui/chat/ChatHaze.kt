package top.wkbin.taixu.ui.chat

import androidx.compose.animation.core.LinearEasing
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.statusBars
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.HazeProgressive
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.blur.HazeBlurStyle
import dev.chrisbanes.haze.blur.hazeBlur

/** 聊天毛玻璃的共享 HazeState：source 挂在背景图与消息列表上，效果挂在顶边渐隐上。 */
val LocalChatHazeState = staticCompositionLocalOf { HazeState() }

/** 对齐 DITING FloatingHeader：工具栏高度 + 底沿 overhang，再叠状态栏。 */
private val ChatTopToolbarHeight = 56.dp
private val ChatTopFadeOverhang = 20.dp

/** 顶边软渐隐带：状态栏强模糊段 + 工具栏溶开 + overhang 尾巴。 */
@Composable
internal fun chatTopEdgeFadeHeight(): Dp =
    WindowInsets.statusBars.asPaddingValues().calculateTopPadding() +
        ChatTopToolbarHeight + ChatTopFadeOverhang

/**
 * 底边导航栏 inset：渐隐下沿贴屏幕底时用，与输入框高度相加构成完整渐隐带。
 */
@Composable
internal fun chatBottomNavFadeHeight(): Dp =
    WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

/**
 * 顶边渐进模糊：渐隐只靠 progressive haze，不叠实色页面色遮罩。
 * 按 DITING `fadeStart = status/(status+toolbar)`：上段强、过工具栏后迅速变弱。
 */
@Composable
private fun Modifier.chatTopEdgeHaze(solid: Float): Modifier {
    val hazeState = LocalChatHazeState.current
    val style = remember(solid) {
        val s = solid.coerceIn(0f, 0.55f)
        val shoulder = s + (1f - s) * 0.45f
        val mask = Brush.verticalGradient(
            0.00f to Color.Black.copy(alpha = 1f),
            s to Color.Black.copy(alpha = 0.85f),
            shoulder to Color.Black.copy(alpha = 0.28f),
            0.88f to Color.Black.copy(alpha = 0.04f),
            1.00f to Color.Transparent,
        )
        HazeBlurStyle {
            blurRadius(20.dp)
            backgroundColor(Color.Transparent)
            noiseFactor(0.015f)
            progressive(
                HazeProgressive.verticalGradient(
                    easing = LinearEasing,
                    startIntensity = 1f,
                    endIntensity = 0f,
                ),
            )
            mask(mask)
        }
    }
    return hazeBlur(input = HazeInput.Sources(hazeState), style = style)
}

/**
 * 顶部全宽软渐隐条。仿 DITING FloatingHeader：纯 progressive haze 溶开，
 * 状态栏段模糊最实，工具栏以下溶到透明；不叠页面色，避免压出实色块。
 */
@Composable
internal fun ChatTopEdgeFade(modifier: Modifier = Modifier) {
    val status = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val height = chatTopEdgeFadeHeight()
    val solid = if (height > 0.dp) {
        (status / height).coerceIn(0.12f, 0.45f)
    } else {
        0.2f
    }
    Box(
        modifier
            .fillMaxWidth()
            .height(height)
            .chatTopEdgeHaze(solid = solid),
    )
}

/**
 * 底部内容渐隐：在列表最底 [fadeHeight] 做与顶部同构的溶解（通常=输入框高+导航栏），
 * 越靠近屏幕底 alpha 越低。不绘制叠加模糊层；调用方勿让 [fadeHeight] 超过输入框顶沿。
 */
internal fun Modifier.chatBottomContentFade(fadeHeight: Dp): Modifier {
    if (fadeHeight <= 0.dp) return this
    return this
        .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
        .drawWithCache {
            val fadePx = fadeHeight.toPx().coerceAtMost(size.height)
            if (fadePx <= 0f) {
                return@drawWithCache onDrawWithContent { drawContent() }
            }
            // 与顶边 stops 同构：带内从上到下 1 → 0，即越靠底越渐隐。
            val brush = Brush.verticalGradient(
                colorStops = arrayOf(
                    0.00f to Color.White,
                    0.12f to Color.White.copy(alpha = 0.85f),
                    0.45f to Color.White.copy(alpha = 0.28f),
                    0.88f to Color.White.copy(alpha = 0.04f),
                    1.00f to Color.Transparent,
                ),
                startY = size.height - fadePx,
                endY = size.height,
            )
            onDrawWithContent {
                drawContent()
                drawRect(brush = brush, blendMode = BlendMode.DstIn)
            }
        }
}
