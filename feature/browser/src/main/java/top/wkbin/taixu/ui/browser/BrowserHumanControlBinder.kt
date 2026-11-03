package top.wkbin.taixu.ui.browser

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import top.wkbin.taixu.core.browser.BrowserHelpRequests
import top.wkbin.taixu.core.browser.BrowserHumanControl
import top.wkbin.taixu.core.datastore.BrowserPreferences

/** 共浏览开关与「Bot 需要你接手」提示，从 [BrowserViewModel] 拆出。 */
internal class BrowserHumanControlBinder(
    private val toolMessage: MutableStateFlow<String?>,
    private val coBrowsingEnabled: MutableStateFlow<Boolean>,
    private val browserPreferences: BrowserPreferences,
    private val scope: CoroutineScope,
) {
    private val helpListener = BrowserHelpRequests.Listener { toolName, _ ->
        toolMessage.value = "Bot 需要你接手：$toolName 已拒绝（共浏览已暂停）"
    }

    fun start() {
        BrowserHelpRequests.addListener(helpListener)
        scope.launch {
            runCatching {
                browserPreferences.coBrowsingEnabled().collect { enabled ->
                    coBrowsingEnabled.value = enabled
                    BrowserHumanControl.setCoBrowsingEnabled(enabled)
                }
            }
        }
    }

    /** 进程内闸门立即生效，不等待 DataStore；Bot 页面动作随即拒绝/恢复。 */
    fun mirror(enabled: Boolean) {
        BrowserHumanControl.setCoBrowsingEnabled(enabled)
    }

    fun stop() {
        BrowserHelpRequests.removeListener(helpListener)
    }
}
