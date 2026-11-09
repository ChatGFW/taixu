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

/** 顶边软渐隐带：状态栏 + 更长过渡区，避免刚出顶栏就硬切成一块雾。 */
@Composable
internal fun chatTopEdgeFadeHeight(): Dp =
    WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 88.dp

/** 底边软渐隐额外上探：从输入区上方开始，一路溶到屏幕最底（含导航栏）。 */
internal val ChatBottomEdgeFadeExtra = 72.dp

/**
 * 边缘渐进模糊。
 *
 * 关键是 [mask]：清晰端大段保持全透明，即使设备把 progressive 退化成均匀模糊，
 * 也不会在条带内缘画出一道硬切线；模糊强度再由 progressive 由弱到强叠上去。
 *
 * 不要给 CompositionLocal 写默认参数——会触发 CONFLICTING_OVERLOADS。
 */
@Composable
private fun Modifier.chatEdgeHaze(topStrong: Boolean): Modifier {
    val hazeState = LocalChatHazeState.current
    val style = remember(topStrong) {
        // mask：清晰端 0~35% 完全不画效果，之后才慢慢显形
        val mask = if (topStrong) {
            Brush.verticalGradient(
                0.00f to Color.Black.copy(alpha = 1f),
                0.25f to Color.Black.copy(alpha = 0.55f),
                0.55f to Color.Black.copy(alpha = 0.18f),
                0.78f to Color.Black.copy(alpha = 0.04f),
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
            blurRadius(16.dp)
            backgroundColor(Color.Transparent)
            noiseFactor(0.02f)
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
 * 顶部全宽软渐隐条。有自定义背景时只做模糊渐变；无背景时再叠极淡页面色溶出。
 */
@Composable
internal fun ChatTopEdgeFade(modifier: Modifier = Modifier) {
    val height = chatTopEdgeFadeHeight()
    val bgActive = LocalChatBackgroundActive.current
    val chrome = chatBackdropChrome()
    Box(
        modifier
            .fillMaxWidth()
            .height(height)
            .chatEdgeHaze(topStrong = true)
            .then(
                if (bgActive) {
                    Modifier
                } else {
                    Modifier.background(
                        Brush.verticalGradient(
                            0.00f to chrome.copy(alpha = 0.42f),
                            0.40f to chrome.copy(alpha = 0.14f),
                            0.75f to chrome.copy(alpha = 0.03f),
                            1.00f to Color.Transparent,
                        ),
                    )
                },
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
