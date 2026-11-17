package top.wkbin.taixu.core.browser

/**
 * CEL-lite 策略用的发起因字段约定（与 harness [RunInitiator] kind 对齐）。
 *
 * Agent → HTTP 自环 → 浏览器 MCP 时协程上下文会断开，因此由客户端请求头
 * [HEADER] 携带，服务端再传入 [BrowserMcpTools.invoke]。
 */
object ActionPolicyInitiator {
    const val HEADER = "X-TaiXu-Initiator-Kind"

    const val PERSON = "person"
    const val SCHEDULE = "schedule"
    const val HANDOFF = "handoff"
    const val WORKFLOW = "workflow"
    const val SYSTEM = "system"

    private val KNOWN = setOf(PERSON, SCHEDULE, HANDOFF, WORKFLOW, SYSTEM)

    /** 未知 / 空白一律回落 person，避免无人值守伪装。 */
    fun normalize(raw: String?): String {
        val kind = raw?.trim()?.lowercase().orEmpty()
        return if (kind in KNOWN) kind else PERSON
    }
}
