package top.wkbin.taixu.core.browser

import java.util.concurrent.CopyOnWriteArrayList

/**
 * 共浏览 / 人机接管闸门（对齐 OpenBot：人握方向盘时 Bot 页面动作被拒绝而非排队）。
 *
 * [coBrowsingEnabled]=true → Bot 可与用户交替操作页面；
 * false → 用户正在接管，Bot 的 navigate/click/type 等页面动作必须拒绝。
 *
 * 状态由 UI（[setCoBrowsingEnabled]）与启动快照同步；只影响浏览器页面动作，
 * 不挡 Shell / 工作区写（同 OpenBot issue #246）。
 * 实际翻转时通知监听器（供 control_taken / control_released 审计），同值不回调。
 */
object BrowserHumanControl {
    fun interface CoBrowsingListener {
        /** [enabled]=true 共浏览恢复（人交还）；false 人接管。 */
        fun onCoBrowsingChanged(enabled: Boolean)
    }

    @Volatile
    private var coBrowsingEnabled: Boolean = true

    private val listeners = CopyOnWriteArrayList<CoBrowsingListener>()

    fun coBrowsingEnabled(): Boolean = coBrowsingEnabled

    /** true = 用户握着方向盘，Bot 不得驱动页面。 */
    fun humanHasControl(): Boolean = !coBrowsingEnabled

    fun addListener(listener: CoBrowsingListener) {
        listeners.addIfAbsent(listener)
    }

    fun removeListener(listener: CoBrowsingListener) {
        listeners.remove(listener)
    }

    fun setCoBrowsingEnabled(enabled: Boolean) {
        if (coBrowsingEnabled == enabled) return
        coBrowsingEnabled = enabled
        listeners.forEach { runCatching { it.onCoBrowsingChanged(enabled) } }
    }

    /**
     * 人接管期间仍允许的只读 / 观测类工具（便于模型知道页面现状，不改状态）。
     * 其它 browser.* 一律拒绝。
     */
    fun isPageActingTool(toolName: String): Boolean {
        val name = toolName.removePrefix("browser.").removePrefix("browser_")
        return name !in READ_ONLY_WHILE_HUMAN_DRIVES
    }

    fun refuseMessage(): String =
        "用户正在接管浏览器（共浏览已暂停）。页面动作已拒绝、未排队；" +
            "请等待用户交还控制权（重新开启共浏览）后再试。" +
            "只读观测（snapshot / url / title 等）仍可用。"

    private val READ_ONLY_WHILE_HUMAN_DRIVES = setOf(
        "list_tabs",
        "snapshot",
        "screenshot",
        "current_url",
        "title",
        "console_list",
        "network_list",
        "network_detail",
        "page_source",
        "debug_status",
        "debug_state",
        "debug_scope",
    )
}
