package top.wkbin.taixu.ui.chat

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.statusBars
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** 对齐 FloatingHeader：工具栏高度 + 底沿 overhang，再叠状态栏。 */
private val ChatTopToolbarHeight = 56.dp
private val ChatTopFadeOverhang = 20.dp

/** 顶边软渐隐带：状态栏 + 工具栏溶开 + overhang 尾巴。 */
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
 * 顶部内容渐隐：在列表最上 [fadeHeight] 做与底部同构的溶解（alpha mask），
 * 不叠 haze / 毛玻璃。越靠近屏幕顶 alpha 越低。
 */
internal fun Modifier.chatTopContentFade(fadeHeight: Dp): Modifier {
    if (fadeHeight <= 0.dp) return this
    return this
        .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
        .drawWithCache {
            val fadePx = fadeHeight.toPx().coerceAtMost(size.height)
            if (fadePx <= 0f) {
                return@drawWithCache onDrawWithContent { drawContent() }
            }
            // 与底边 stops 同构，方向反过来：带内从上到下 0 → 1。
            val brush = Brush.verticalGradient(
                colorStops = arrayOf(
                    0.00f to Color.Transparent,
                    0.12f to Color.White.copy(alpha = 0.04f),
                    0.55f to Color.White.copy(alpha = 0.28f),
                    0.88f to Color.White.copy(alpha = 0.85f),
                    1.00f to Color.White,
                ),
                startY = 0f,
                endY = fadePx,
            )
            onDrawWithContent {
                drawContent()
                drawRect(brush = brush, blendMode = BlendMode.DstIn)
            }
        }
}

/**
 * 底部内容渐隐：在列表最底 [fadeHeight] 做溶解（通常=输入框高+导航栏），
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
