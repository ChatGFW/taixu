package top.wkbin.taixu.runtime

import java.io.File

/**
 * 附件目录布局的唯一来源。
 *
 * host 侧多处需要"把 guest 的 `/attachments/<name>` 落到哪个宿主目录"这一知识：
 * 运行时挂载、聊天上传、粘贴文本、迁移器。集中在这里，避免各写一份后逐渐漂移。
 */
object AttachmentPaths {
    private const val RUNTIME_DIR = "linux-runtime"

    fun base(filesDir: File): File = File(filesDir, RUNTIME_DIR)

    /** 全局附件根：只保留 skills 与无人引用的历史文件。 */
    fun global(filesDir: File): File = File(base(filesDir), "attachments")

    fun skills(filesDir: File): File = File(global(filesDir), "skills")

    fun orphan(filesDir: File): File = File(global(filesDir), "legacy")

    /** 会话目录放在全局附件根下，本地备份的递归收集与恢复路径才能自然覆盖。 */
    fun sessionsRoot(filesDir: File): File = File(global(filesDir), "sessions")

    /**
     * 无会话上下文的挂载源。**绝不能回退到 [global]**：全局根下含 `sessions/`，
     * 挂进 guest 就等于把所有会话的私有附件公开。这里只给一个共享空目录。
     */
    fun shared(filesDir: File): File = File(global(filesDir), "shared")

    /**
     * 会话私有附件目录；`sessionId` 为空时落到 [shared]，
     * 兼容 workflow / 插件等没有会话上下文的调用方。
     */
    fun session(filesDir: File, sessionId: String?): File =
        sessionId?.takeIf { it.isNotBlank() }
            ?.let { File(sessionsRoot(filesDir), segment(it)) }
            ?: shared(filesDir)

    /**
     * 会话 ID 只作为单段目录名使用，防路径穿越。
     * 必须至少含一个字母或数字：`.`、`..`、`___` 这类名字会被整段替换成 `unknown`，
     * 否则 `File(root, "..")` 会指到会话根之外。
     */
    fun segment(raw: String): String {
        val sanitized = raw.replace(Regex("[^A-Za-z0-9._-]"), "_").take(120)
        return sanitized.takeIf { it.any { ch -> ch.isLetterOrDigit() } } ?: "unknown"
    }
}
