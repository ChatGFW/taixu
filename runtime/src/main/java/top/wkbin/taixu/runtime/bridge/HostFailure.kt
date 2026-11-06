package top.wkbin.taixu.runtime.bridge

/**
 * 宿主桥接失败分类。
 *
 * 沙箱 CLI 把它映射成进程退出码，agent 据此区分"我参数写错了"（改参数）、
 * "没授权"（请用户处理）、"通道没开"（换通道）和"真超时"（缩范围），
 * 而不是看到一个布尔失败就原样重试。
 */
enum class HostFailure(val wire: String, val exitCode: Int, val hint: String) {
    USAGE("usage", 2, "参数不合法：修正参数后重试"),
    NOT_FOUND("not_found", 66, "目标不存在：先确认路径或包名，不要重复同一请求"),
    PERMISSION_DENIED("permission_denied", 126, "宿主拒绝或未授权：不要重试同一操作，需用户在设置中授权"),
    UNAVAILABLE("unavailable", 69, "宿主通道不可用：提示用户开启无线调试或 Shizuku/Root"),
    TIMEOUT("timeout", 124, "执行超时：缩小范围或拆成更短的命令"),
    INTERNAL("internal", 1, "宿主内部错误：换一条路径，或把错误报告给用户"),
    ;

    companion object {
        fun ofHttpStatus(status: Int): HostFailure? = when (status) {
            400, 422 -> USAGE
            401, 403 -> PERMISSION_DENIED
            404 -> NOT_FOUND
            408, 504 -> TIMEOUT
            in 500..599 -> INTERNAL
            else -> null
        }

        /** 宿主 shell 通道的退出码 → 分类；126/127 是 shell 约定的"不可执行/找不到"。 */
        fun ofShellExit(exitCode: Int, stderr: String): HostFailure? {
            if (exitCode == 0) return null
            val text = stderr.lowercase()
            return when {
                exitCode == 126 || "permission denied" in text -> PERMISSION_DENIED
                exitCode == 127 || "inaccessible or not found" in text -> NOT_FOUND
                exitCode == 124 -> TIMEOUT
                "not connected" in text || "offline" in text || "device unauthorized" in text -> UNAVAILABLE
                else -> INTERNAL
            }
        }
    }
}
