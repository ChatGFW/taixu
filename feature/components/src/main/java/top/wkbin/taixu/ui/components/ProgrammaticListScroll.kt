package top.wkbin.taixu.ui.components

import androidx.compose.foundation.interaction.DragInteraction
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import java.util.WeakHashMap
import kotlin.coroutines.coroutineContext

/**
 * 程序化滚动串行器：消除 Compose Foundation 懒加载列表的
 * "entered drag with non-zero pending scroll" 闪退诱因。
 *
 * 框架在 [LazyListState] 的拖拽滚动分派进入时，会校验上一段滚动是否已消费完毕
 * （scrollToBeConsumed 绝对值 ≤ 0.5px）；若惯性滑动或 animateScroll 动画尚未结束
 * 就开始拖拽，该校验直接抛出 IllegalStateException。异常发生在框架手势协程内部，
 * 应用层无法捕获，只能从应用侧消除诱因。
 *
 * 应用侧可控的诱因是程序化滚动动画：多路动画叠加、动画被拖拽打断后残留。
 * 本组件从三个层面消除诱因：
 * 1. [runProgrammaticScroll] 串行化：同一列表只保留一个程序化滚动动画，新动画取消旧动画；
 * 2. 拖拽开始时（[rememberProgrammaticScrollGuard] 监听 [DragInteraction.Start]）立即
 *    取消进行中的程序化动画；
 * 3. 拖拽期间拒绝启动新的动画滚动，避免程序化滚动与手势滚动分派交错。
 *
 * 使用：每个持有 [LazyListState] 并会启动程序化滚动的页面，在组合中调用一次
 * [rememberProgrammaticScrollGuard]；所有动画滚动经 [runProgrammaticScroll] 启动。
 */
class ProgrammaticScrollSequencer internal constructor() {
    internal var job: Job? = null
    internal var dragActive: Boolean = false
}

// 仅主线程访问（组合、手势回调、点击回调均在主线程）；键弱引用，
// 列表状态随组合销毁后可被回收，不阻止 GC。
private val sequencers = WeakHashMap<LazyListState, ProgrammaticScrollSequencer>()

/** 获取 [this] 对应的程序化滚动串行器（同一列表状态全局唯一）。 */
fun LazyListState.programmaticScrollSequencer(): ProgrammaticScrollSequencer =
    sequencers.getOrPut(this) { ProgrammaticScrollSequencer() }

/**
 * 监听用户拖拽：拖拽开始时取消该列表进行中的程序化滚动动画并标记拖拽中，
 * 拖拽结束后清除标记。每个使用程序化滚动的页面在组合中调用一次。
 */
@Composable
fun rememberProgrammaticScrollGuard(listState: LazyListState) {
    val sequencer = remember(listState) { listState.programmaticScrollSequencer() }
    LaunchedEffect(listState) {
        listState.interactionSource.interactions.collect { interaction ->
            when (interaction) {
                is DragInteraction.Start -> {
                    sequencer.dragActive = true
                    sequencer.job?.cancel()
                }
                is DragInteraction.Stop, is DragInteraction.Cancel -> sequencer.dragActive = false
            }
        }
    }
}

/**
 * 以串行化方式启动程序化滚动动画：取消同列表前一个未完成的动画滚动，并登记当前协程，
 * 使拖拽开始时可以取消它；拖拽期间直接跳过，避免与手势滚动交错。
 * 必须在协程中调用，[block] 内为 animateScrollToItem / animateScrollBy 等挂起滚动。
 */
suspend fun LazyListState.runProgrammaticScroll(block: suspend LazyListState.() -> Unit) {
    val sequencer = programmaticScrollSequencer()
    if (sequencer.dragActive) return
    sequencer.job?.cancel()
    sequencer.job = coroutineContext[Job]
    block()
}
