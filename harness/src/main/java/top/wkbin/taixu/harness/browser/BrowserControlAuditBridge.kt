package top.wkbin.taixu.harness.browser

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import top.wkbin.taixu.core.browser.BrowserHelpRequests
import top.wkbin.taixu.core.browser.BrowserHumanControl
import top.wkbin.taixu.harness.approval.ApprovalAuditRecorder

/**
 * 浏览器人机交接审计桥（OpenBot handover）：
 * - 共浏览翻转 → control_taken / control_released
 * - 人接管期间 Bot 页面动作被拒 → help_requested（去抖）
 *
 * 同值 / 去抖窗口内不重复写；审计失败不回抛到 UI / 闸门。
 */
class BrowserControlAuditBridge(
    private val auditor: ApprovalAuditRecorder,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val controlListener = BrowserHumanControl.CoBrowsingListener { enabled ->
        scope.launch {
            if (enabled) auditor.controlReleased() else auditor.controlTaken()
        }
    }
    private val helpListener = BrowserHelpRequests.Listener { toolName, detail ->
        scope.launch { auditor.helpRequested(toolName, detail) }
    }

    @Volatile
    private var bound = false

    fun bind() {
        if (bound) return
        bound = true
        BrowserHumanControl.addListener(controlListener)
        BrowserHelpRequests.addListener(helpListener)
    }

    fun unbind() {
        if (!bound) return
        bound = false
        BrowserHumanControl.removeListener(controlListener)
        BrowserHelpRequests.removeListener(helpListener)
    }
}
