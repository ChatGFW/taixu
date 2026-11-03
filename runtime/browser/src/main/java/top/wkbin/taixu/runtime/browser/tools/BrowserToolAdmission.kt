package top.wkbin.taixu.runtime.browser.tools

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import top.wkbin.taixu.core.browser.ActionContext
import top.wkbin.taixu.core.browser.ActionPolicyInitiator
import top.wkbin.taixu.core.browser.BrowserHelpRequests
import top.wkbin.taixu.core.browser.BrowserHumanControl
import top.wkbin.taixu.core.browser.HostActionPolicy
import top.wkbin.taixu.core.browser.PolicyDecision
import top.wkbin.taixu.runtime.browser.BrowserEngine

/**
 * 人机接管与 CEL-lite 主机策略。
 * 参数未带 url 时用当前标签页地址；策略引用 page.host 且 host 无法确定时失败封闭。
 */
internal suspend fun admitBrowserTool(
    toolName: String,
    explicitUrl: String,
    tabId: String?,
    initiatorKind: String,
    homeUrl: String,
    engine: BrowserEngine?,
): BrowserMcpTools.InvokeResult? {
    if (BrowserHumanControl.humanHasControl() && BrowserHumanControl.isPageActingTool(toolName)) {
        val message = BrowserHumanControl.refuseMessage()
        BrowserHelpRequests.notifyPageActionRefused(toolName, message)
        return BrowserMcpTools.InvokeResult.error(message)
    }
    val policyUrl = when {
        explicitUrl.isNotEmpty() -> explicitUrl
        toolName == "browser.open" -> homeUrl
        else -> engine?.let { currentTabUrl(it, tabId) }.orEmpty()
    }
    val host = HostActionPolicy.hostOf(policyUrl)
    if (host.isEmpty() && HostActionPolicy.constrainsPageHost()) {
        return BrowserMcpTools.InvokeResult.error(
            HostActionPolicy.refuseMessage(PolicyDecision.Deny("无法确定当前页面主机，主机 allowlist 已启用")),
        )
    }
    return when (val decision = HostActionPolicy.evaluate(
        ActionContext(
            toolName = toolName,
            pageUrl = policyUrl,
            pageHost = host,
            initiatorKind = ActionPolicyInitiator.normalize(initiatorKind),
        ),
    )) {
        is PolicyDecision.Deny -> BrowserMcpTools.InvokeResult.error(HostActionPolicy.refuseMessage(decision))
        PolicyDecision.Allow -> null
    }
}

/** 工具参数省略 url 时，用指定 tab 或当前活动 tab 的已加载地址。 */
internal suspend fun currentTabUrl(engine: BrowserEngine, tabId: String?): String {
    val requested = tabId?.trim()?.takeIf { it.isNotEmpty() }
    val active = engine.activeTab()
    val resolvedId = when {
        requested == null -> active?.tabId
        else -> {
            val tabs = runCatching { engine.listTabs() }.getOrDefault(emptyList())
            tabs.firstOrNull { it.tabId == requested }?.tabId
                ?: tabs.firstOrNull { it.tabId.endsWith(requested) }?.tabId
                ?: requested
        }
    } ?: return ""
    val live = engine.eventBus.urlOf(resolvedId)?.trim()?.takeIf { it.isNotEmpty() }
    if (live != null) return live
    return active?.takeIf { it.tabId == resolvedId }?.url?.trim().orEmpty()
}

internal fun JsonElement?.asString(): String? =
    (this as? JsonPrimitive)?.content?.takeIf { it.isNotEmpty() }

internal fun JsonElement?.asInt(): Int? =
    (this as? JsonPrimitive)?.content?.toIntOrNull()

internal fun JsonElement?.asBool(): Boolean? =
    (this as? JsonPrimitive)?.content?.toBooleanStrictOrNull()

internal fun fmtBytes(n: Long): String = when {
    n >= 1024 * 1024 -> "${n / (1024 * 1024)}MB"
    n >= 1024 -> "${n / 1024}KB"
    else -> "${n}B"
}
