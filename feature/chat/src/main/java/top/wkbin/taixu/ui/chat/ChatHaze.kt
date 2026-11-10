package top.wkbin.taixu.ui.chat

import androidx.compose.animation.core.LinearEasing
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.statusBars
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.HazeProgressive
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.blur.HazeBlurStyle
import dev.chrisbanes.haze.blur.hazeBlur
import top.wkbin.taixu.ui.theme.LocalChatBackgroundActive
import top.wkbin.taixu.ui.theme.chatBackdropChrome

/** 聊天毛玻璃的共享 HazeState：source 挂在背景图与消息列表上，效果挂在顶/底边缘渐隐上。 */
val LocalChatHazeState = staticCompositionLocalOf { HazeState() }

/** 对齐 DITING FloatingHeader：工具栏高度 + 底沿 overhang，再叠状态栏。 */
private val ChatTopToolbarHeight = 56.dp
private val ChatTopFadeOverhang = 20.dp

/** 顶边软渐隐带：状态栏实色段 + 工具栏溶开 + overhang 尾巴。 */
@Composable
internal fun chatTopEdgeFadeHeight(): Dp =
    WindowInsets.statusBars.asPaddingValues().calculateTopPadding() +
        ChatTopToolbarHeight + ChatTopFadeOverhang

/** 底边软渐隐额外上探：从输入区上方开始，一路溶到屏幕最底（含导航栏）。 */
internal val ChatBottomEdgeFadeExtra = 72.dp

/**
 * 边缘渐进模糊。
 *
 * 顶边按 DITING `fadeStart = status/(status+toolbar)`：上段强、过工具栏后迅速变弱，
 * overhang 段几乎只靠颜色溶出，避免整条雾砖硬切。
 */
@Composable
private fun Modifier.chatEdgeHaze(topStrong: Boolean, fadeStart: Float = 0f): Modifier {
    val hazeState = LocalChatHazeState.current
    val style = remember(topStrong, fadeStart) {
        val mask = if (topStrong) {
            val solid = fadeStart.coerceIn(0f, 0.55f)
            Brush.verticalGradient(
                0.00f to Color.Black.copy(alpha = 1f),
                solid to Color.Black.copy(alpha = 0.85f),
                (solid + (1f - solid) * 0.45f) to Color.Black.copy(alpha = 0.28f),
                0.88f to Color.Black.copy(alpha = 0.04f),
                1.00f to Color.Transparent,
            )
        } else {
            Brush.verticalGradient(
                0.00f to Color.Transparent,
                0.22f to Color.Black.copy(alpha = 0.04f),
                0.45f to Color.Black.copy(alpha = 0.18f),
                0.75f to Color.Black.copy(alpha = 0.55f),
                1.00f to Color.Black.copy(alpha = 1f),
            )
        }
        HazeBlurStyle {
            blurRadius(if (topStrong) 20.dp else 16.dp)
            backgroundColor(Color.Transparent)
            noiseFactor(0.015f)
            progressive(
                HazeProgressive.verticalGradient(
                    easing = LinearEasing,
                    startIntensity = if (topStrong) 1f else 0f,
                    endIntensity = if (topStrong) 0f else 1f,
                ),
            )
            mask(mask)
        }
    }
    return hazeBlur(input = HazeInput.Sources(hazeState), style = style)
}

/**
 * 顶部全宽软渐隐条。仿 DITING FloatingHeader：状态栏段更实，工具栏以下溶到透明；
 * 有无自定义背景都叠页面色渐隐（壁纸时略淡），再配 progressive haze。
 */
@Composable
internal fun ChatTopEdgeFade(modifier: Modifier = Modifier) {
    val status = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val height = chatTopEdgeFadeHeight()
    val fadeStart = if (height > 0.dp) {
        (status / height).coerceIn(0.12f, 0.45f)
    } else {
        0.2f
    }
    val bgActive = LocalChatBackgroundActive.current
    val chrome = chatBackdropChrome()
    val peak = if (bgActive) 0.72f else 0.94f
    Box(
        modifier
            .fillMaxWidth()
            .height(height)
            .chatEdgeHaze(topStrong = true, fadeStart = fadeStart)
            .background(
                Brush.verticalGradient(
                    0.00f to chrome.copy(alpha = peak),
                    fadeStart to chrome.copy(alpha = peak * 0.88f),
                    (fadeStart + (1f - fadeStart) * 0.55f) to chrome.copy(alpha = peak * 0.22f),
                    0.90f to chrome.copy(alpha = peak * 0.04f),
                    1.00f to Color.Transparent,
                ),
            ),
    )
}

/**
 * 底部软渐隐条：贴屏幕底边对齐。
 * 高度 = 底栏实测高度 + [ChatBottomEdgeFadeExtra]，上沿透明、下沿（导航栏一带）最强。
 */
@Composable
internal fun ChatBottomEdgeFade(height: Dp, modifier: Modifier = Modifier) {
    if (height <= 0.dp) return
    val bgActive = LocalChatBackgroundActive.current
    val chrome = chatBackdropChrome()
    Box(
        modifier
            .fillMaxWidth()
            .height(height)
            .chatEdgeHaze(topStrong = false)
            .then(
                if (bgActive) {
                    Modifier
                } else {
                    Modifier.background(
                        Brush.verticalGradient(
                            0.00f to Color.Transparent,
                            0.35f to chrome.copy(alpha = 0.04f),
                            0.70f to chrome.copy(alpha = 0.16f),
                            1.00f to chrome.copy(alpha = 0.42f),
                        ),
                    )
                },
            ),
    )
}
