package top.wkbin.taixu.ui.settings

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import top.wkbin.taixu.core.browser.ActionPolicyDocument
import top.wkbin.taixu.core.browser.HostActionPolicy
import top.wkbin.taixu.core.datastore.BrowserPreferences

/**
 * 主机动作策略 JSON（CEL-lite）。DataStore 空串时对外展示出厂默认 pretty JSON，
 * 保存成功会即时 [HostActionPolicy.install]（无需重启）。
 */
internal class HostActionPolicySettings(
    private val browserPrefs: BrowserPreferences,
    private val scope: CoroutineScope,
) {
    val json: StateFlow<String> = browserPrefs.actionPolicyJson()
        .map { stored ->
            stored.trim().ifEmpty { HostActionPolicy.encodePretty(ActionPolicyDocument.DEFAULT) }
        }
        .stateIn(scope, SharingStarted.Eagerly, HostActionPolicy.encodePretty(ActionPolicyDocument.DEFAULT))

    suspend fun save(raw: String): Result<Unit> {
        val parsed = HostActionPolicy.validateAndParse(raw)
        val doc = parsed.getOrElse { return Result.failure(it) }
        HostActionPolicy.install(doc)
        browserPrefs.setActionPolicyJson(HostActionPolicy.encodePretty(doc))
        return Result.success(Unit)
    }

    fun reset() {
        HostActionPolicy.resetToDefault()
        scope.launch { browserPrefs.setActionPolicyJson("") }
    }
}
