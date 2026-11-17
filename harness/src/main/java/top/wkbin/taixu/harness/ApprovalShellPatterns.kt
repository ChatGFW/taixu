package top.wkbin.taixu.harness

/**
 * `ApprovalPolicyEngine` 剥离 fd 重定向（`2>&1`、`>&2`）用的模式。
 *
 * 每次工具调用都会走一遍命令白名单判定，正则不能在函数体内现编；
 * 单独成文件是为了不撑大已登记架构基线的 `ApprovalPolicyEngine.kt`。
 */
internal val FD_DUP_PADDED = Regex("""\s+\d*>&\d+\b""")

internal val FD_DUP_PADDED_SHORT = Regex("""\s+>&\d+\b""")

internal val FD_DUP = Regex("""\d*>&\d+""")

internal val FD_DUP_SHORT = Regex(""">&\d+""")