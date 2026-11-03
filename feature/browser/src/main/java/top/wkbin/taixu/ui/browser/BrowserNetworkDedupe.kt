package top.wkbin.taixu.ui.browser

import top.wkbin.taixu.runtime.browser.CapturedRequest

/**
 * native（shouldInterceptRequest，无状态码）与 js（hook 引擎，有状态码/body）
 * 对同一请求会各出一条：同 method+url 且 2.5s 时间窗内视为重复，隐藏 native 条目。
 */
internal fun dedupeNetwork(all: List<CapturedRequest>): List<CapturedRequest> {
    val jsEntries = all.filter { it.source != "native" }
    if (jsEntries.isEmpty()) return all
    return all.filterNot { native ->
        native.source == "native" && jsEntries.any {
            it.method == native.method && it.url == native.url &&
                kotlin.math.abs(it.startedAt - native.startedAt) < 2_500
        }
    }
}
