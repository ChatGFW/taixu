package top.wkbin.taixu.ui.chat

// These markers also recognize persisted messages after the app language changes.
private val attachmentHeaders = listOf(
    "[附件：已复制并挂载到 Linux 沙箱]\n",
    "[Attachments copied and mounted in the Linux sandbox]\n",
)
private val attachmentHints = listOf(
    "需要通过工具访问时，请使用 base 在上述绝对路径读取；不要使用 Android content:// URI 或宿主机路径。",
    "When tool access is needed, use base to read the absolute paths above. Do not use Android content:// URIs or host paths.",
)
private val attachmentPrompts = setOf(
    "请分析附带的图片：",
    "请分析并处理以下附带的文件：",
    "Analyze the attached images:",
    "Analyze the following attached files:",
)
private val attachmentRow = Regex(
    "^\\d+\\. \\[(图片|文件|Image|File)] (.+) -> (?:Linux 沙箱绝对路径|Linux sandbox absolute path): /.+$",
)

/** Hide composer-generated transport instructions in the transcript, including older messages. */
internal fun userMessageDisplayText(text: String, hasImagePreviews: Boolean): String {
    val header = attachmentHeaders.firstOrNull { it in text } ?: return text
    val headerIndex = text.lastIndexOf(header)
    if (headerIndex > 0 && !text.substring(0, headerIndex).endsWith("\n\n")) return text
    val blockStart = headerIndex + header.length
    val hint = attachmentHints.firstOrNull { text.indexOf(it, blockStart) >= 0 } ?: return text
    val hintIndex = text.indexOf(hint, blockStart)
    val rows = text.substring(blockStart, hintIndex).lineSequence()
        .mapNotNull(attachmentRow::matchEntire).toList()
    if (rows.isEmpty()) return text

    val userText = text.substring(0, headerIndex).trimEnd()
        .takeUnless { it in attachmentPrompts }.orEmpty()
    val files = rows.filterNot {
        hasImagePreviews && it.groupValues[1] in setOf("图片", "Image")
    }.map { it.groupValues[2] }
    val trailingText = text.substring(hintIndex + hint.length).trim()
    return (listOf(userText) + files + trailingText)
        .filter(String::isNotBlank).joinToString("\n\n")
}
