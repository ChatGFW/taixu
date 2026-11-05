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

    fun sessionsRoot(filesDir: File): File = File(base(filesDir), "sessions")

    /**
     * 会话私有附件目录；`sessionId` 为空时回退到全局目录，
     * 兼容 workflow / 插件等没有会话上下文的调用方。
     */
    fun session(filesDir: File, sessionId: String?): File =
        sessionId?.takeIf { it.isNotBlank() }
            ?.let { File(File(File(base(filesDir), "sessions"), segment(it)), "attachments") }
            ?: global(filesDir)

    /** 会话 ID 只作为单段目录名使用，防路径穿越。 */
    fun segment(raw: String): String =
        raw.replace(Regex("[^A-Za-z0-9._-]"), "_").take(120).ifBlank { "unknown" }
}
