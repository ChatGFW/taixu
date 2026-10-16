package top.wkbin.taixu.harness

import java.util.concurrent.ConcurrentHashMap

/**
 * 「取消中」会话标记。
 *
 * 原先是直接在 [HarnessLoop] 里用裸并发集合就地增删：置位点与唯一的摘除点被两次
 * `withSessionMutex` 划进了不同代码块，中间还夹着多次可失败的数据库写入与
 * `cancelAndJoin()`。任一步抛出、或协程在两次加锁之间被取消，标记就会永久驻留。
 * 此后 `isSessionBusy()` 恒为 true，会话表现为「分支无法切换、消息无法发送」，
 * 界面上分支卡片全部置灰且点击毫无反馈——现象与原因相隔太远，极难定位。
 *
 * 这里把标记的读、写与自愈收敛到一处：
 *  - [guarded]：置位块一旦失败就立刻摘除标记，从源头堵住泄漏；
 *  - [isCancelling]：在没有存活任务、也没有待批审批时，把标记判定为陈旧残留并
 *    就地清理；这样即使还有未覆盖到的中断路径，也不会把会话永久锁死。
 */
internal class CancellingFlags {

    private val ids = ConcurrentHashMap.newKeySet<String>()

    /** 置位。调用方须保证最终会有配对的 [clear]（或用 [guarded]）。 */
    fun mark(sessionId: String) {
        ids += sessionId
    }

    /** 摘除。幂等。 */
    fun clear(sessionId: String) {
        ids -= sessionId
    }

    /** 只读判断，不做自愈。用于旁路统计等不应改变状态的场景。 */
    fun isSet(sessionId: String): Boolean = ids.contains(sessionId)

    /**
     * 是否会话正处于「取消中」。
     *
     * [hold] 为 true 表示仍有存活任务或待批审批，标记有效，照常视为取消中；
     * 为 false 表示已无任何在途工作，该标记属于陈旧残留，就地清理并按未在取消返回，
     * 让本次判断即可放行，避免出现「第一次点击无效、再点一次才行」的诡异手感。
     */
    fun isCancelling(sessionId: String, hold: Boolean): Boolean {
        if (!ids.contains(sessionId)) return false
        if (hold) return true
        ids -= sessionId
        return false
    }

    /**
     * 先置位，再执行 [block]；块内抛出时立刻摘除标记后原样抛出。
     *
     * 这样置位块（清理队列、标记任务取消、获取会话互斥等）中途失败不会留下粘性标记。
     * 注意成功路径**不**摘除：标记要一直保留到取消的第二阶段收尾完成。
     */
    internal inline fun <T> guarded(sessionId: String, block: () -> T): T {
        mark(sessionId)
        return try {
            block()
        } catch (failure: Throwable) {
            clear(sessionId)
            throw failure
        }
    }
}
