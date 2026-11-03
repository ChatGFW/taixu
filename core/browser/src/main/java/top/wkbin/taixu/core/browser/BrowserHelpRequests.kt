package top.wkbin.taixu.core.browser

import java.util.concurrent.CopyOnWriteArrayList

/**
 * Bot 在人接管期间尝试页面动作被拒时的求助信号（对齐 OpenBot `computer.help_requested`）。
 *
 * 同工具名 [DEBOUNCE_MS] 内只通知一次，避免模型重试刷屏审计。
 */
object BrowserHelpRequests {
    fun interface Listener {
        fun onHelpRequested(toolName: String, detail: String)
    }

    const val DEBOUNCE_MS = 10_000L

    private val listeners = CopyOnWriteArrayList<Listener>()

    @Volatile
    private var lastTool: String = ""

    @Volatile
    private var lastAt: Long = 0L

    fun addListener(listener: Listener) {
        listeners.addIfAbsent(listener)
    }

    fun removeListener(listener: Listener) {
        listeners.remove(listener)
    }

    /** 供测试复位去抖状态。 */
    fun resetDebounceForTest() {
        lastTool = ""
        lastAt = 0L
    }

    fun notifyPageActionRefused(toolName: String, detail: String = BrowserHumanControl.refuseMessage()) {
        val now = System.currentTimeMillis()
        if (toolName == lastTool && now - lastAt < DEBOUNCE_MS) return
        lastTool = toolName
        lastAt = now
        listeners.forEach { runCatching { it.onHelpRequested(toolName, detail) } }
    }
}
