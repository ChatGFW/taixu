package top.wkbin.taixu.harness

/** 分片累积一次工具调用的 id/name/arguments（OpenAI 与 Anthropic 流式均复用）。 */
internal data class ToolCallAccumulator(
    var id: String = "",
    var name: String = "",
    val arguments: StringBuilder = StringBuilder(),
) {
    private var lastProgressAtNanos: Long = 0L
    private var lastProgress: Triple<Int, Int, Int>? = null
    private var progressStartedAtMs: Long = 0L

    /**
     * 工具参数本身也是 SSE 分片。WRITE/EDIT 在完整 JSON 到达前不能执行，但可以从
     * 已收到的 JSON 字符串片段估算路径与增删行数，让任务行在模型生成参数期间就出现。
     */
    fun publishProgress(onProgress: (ToolCallStreamProgress) -> Unit, force: Boolean = false) {
        val normalizedName = name.trim().lowercase()
        if (normalizedName.isBlank()) return
        val fileTool = normalizedName == "write" || normalizedName == "edit"
        // 文件工具在名称出现、参数尚未开始时也发首帧；其他工具保持有参数后才计数。
        if (arguments.isEmpty() && !fileTool) return
        val now = System.nanoTime()
        if (!force && lastProgressAtNanos != 0L && now - lastProgressAtNanos < TOOL_PROGRESS_INTERVAL_NANOS) return
        if (progressStartedAtMs == 0L) progressStartedAtMs = System.currentTimeMillis()

        val progress = when (normalizedName) {
            "write" -> ToolCallStreamProgress(
                name = normalizedName,
                path = partialJsonStringValue(arguments, "path"),
                addedLines = countPartialJsonStringLines(arguments, "content") ?: 0,
                deletedLines = 0,
                startedAtMs = progressStartedAtMs,
            )
            "edit" -> ToolCallStreamProgress(
                name = normalizedName,
                path = partialJsonStringValue(arguments, "path"),
                addedLines = countPartialJsonStringLines(arguments, "newText") ?: 0,
                deletedLines = countPartialJsonStringLines(arguments, "oldText") ?: 0,
                startedAtMs = progressStartedAtMs,
            )
            else -> ToolCallStreamProgress(normalizedName, 0, 0, startedAtMs = progressStartedAtMs)
        }.copy(argumentChars = arguments.length)
        val counts = Triple(progress.addedLines, progress.deletedLines, progress.argumentChars)
        if (force || counts != lastProgress) {
            lastProgress = counts
            lastProgressAtNanos = now
            onProgress(progress)
        }
    }

    private companion object {
        const val TOOL_PROGRESS_INTERVAL_NANOS = 200_000_000L
    }
}

/** 工具参数流的轻量进度；不参与最终执行。WRITE/EDIT 另提供路径与行数预览。 */
data class ToolCallStreamProgress(
    val name: String,
    val addedLines: Int,
    val deletedLines: Int,
    val argumentChars: Int = 0,
    val path: String? = null,
    val startedAtMs: Long = 0L,
)

internal fun ToolCallStreamProgress.statusText(): String {
    val target = displayTarget()?.let { " · $it" }.orEmpty()
    return when (name) {
        "write" -> "正在生成 write$target · +$addedLines"
        "edit" -> "正在生成 edit$target · +$addedLines -$deletedLines"
        else -> "正在生成工具调用 ${name.take(80)} · $argumentChars 字符"
    }
}

/** 展示用文件名：路径仍在流式生成时也保留当前片段。 */
fun ToolCallStreamProgress.displayTarget(): String? =
    path?.replace('\\', '/')?.trimEnd('/')?.substringAfterLast('/')
        ?.replace(Regex("[\\r\\n\\t]+"), " ")?.take(100)?.takeIf { it.isNotBlank() }

/**
 * 从尚未闭合的 JSON 参数中读取指定字符串字段的原始内容；
 * 字符串尚未闭合时返回已到达的片段，字段未开始时返回 null。
 */
internal fun partialJsonStringRaw(arguments: CharSequence, fieldName: String): String? {
    val key = "\"$fieldName\""
    var searchFrom = 0
    while (searchFrom < arguments.length) {
        val keyStart = arguments.indexOf(key, searchFrom)
        if (keyStart < 0) return null
        var cursor = keyStart + key.length
        while (cursor < arguments.length && arguments[cursor].isWhitespace()) cursor++
        if (cursor >= arguments.length) return null
        if (arguments[cursor] != ':') {
            searchFrom = keyStart + key.length
            continue
        }
        cursor++
        while (cursor < arguments.length && arguments[cursor].isWhitespace()) cursor++
        if (cursor >= arguments.length) return null
        if (arguments[cursor] != '"') {
            searchFrom = keyStart + key.length
            continue
        }
        cursor++
        val start = cursor
        var escaped = false
        while (cursor < arguments.length) {
            val char = arguments[cursor++]
            if (escaped) {
                escaped = false
            } else {
                when (char) {
                    '\\' -> escaped = true
                    '"' -> return arguments.subSequence(start, cursor - 1).toString()
                }
            }
        }
        return arguments.subSequence(start, cursor).toString()
    }
    return null
}

/** 解除常见 JSON 转义；末尾悬空的反斜杠丢弃，待下一分片补全。 */
internal fun partialJsonStringValue(arguments: CharSequence, fieldName: String): String? {
    val raw = partialJsonStringRaw(arguments, fieldName) ?: return null
    val out = StringBuilder(raw.length)
    var escaped = false
    raw.forEach { char ->
        if (escaped) {
            when (char) {
                'n' -> out.append('\n'); 't' -> out.append('\t'); 'r' -> out.append('\r')
                'b' -> out.append('\b'); 'f' -> out.append('\u000C')
                else -> out.append(char)
            }
            escaped = false
        } else if (char == '\\') {
            escaped = true
        } else {
            out.append(char)
        }
    }
    return out.toString()
}

/**
 * 从尚未闭合的 JSON 参数中读取指定字符串字段的当前行数。
 * JSON 中换行通常是 `\n`；转义反斜杠 `\\n` 不应误判为新行。
 */
internal fun countPartialJsonStringLines(arguments: CharSequence, fieldName: String): Int? =
    partialJsonStringRaw(arguments, fieldName)?.let(::countJsonStringLines)

private fun countJsonStringLines(raw: CharSequence): Int {
    var lines = 1
    var escaped = false
    var index = 0
    while (index < raw.length) {
        val char = raw[index++]
        if (escaped) {
            if (char == 'n') lines++
            escaped = false
        } else {
            when (char) {
                '\\' -> escaped = true
                '\n' -> lines++
            }
        }
    }
    return lines
}
