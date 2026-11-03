package top.wkbin.taixu.harness.mcp

import top.wkbin.taixu.core.browser.ActionPolicyInitiator
import top.wkbin.taixu.harness.approval.currentRunInitiator

/**
 * tools/call 按次附带发起因。会话缓存头不含此字段，避免无人值守与有人交互串味。
 */
internal suspend fun mcpCallHeaders(sessionHeaders: Map<String, String>, method: String): Map<String, String> =
    if (method == "tools/call") {
        sessionHeaders + (ActionPolicyInitiator.HEADER to currentRunInitiator().kind)
    } else {
        sessionHeaders
    }
