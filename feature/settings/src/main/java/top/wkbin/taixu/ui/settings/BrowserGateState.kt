package top.wkbin.taixu.ui.settings

/** 浏览器 MCP 安全门禁状态（默认全部关闭，最小权限）。 */
data class BrowserGateState(
    val allowRemoteConnect: Boolean = false,
    val allowEvalJs: Boolean = false,
    val allowHooks: Boolean = false,
    val allowCdp: Boolean = false,
    val allowVConsole: Boolean = false,
)
