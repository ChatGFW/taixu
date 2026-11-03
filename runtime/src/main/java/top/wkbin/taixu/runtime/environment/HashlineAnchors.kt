package top.wkbin.taixu.runtime.environment

/**
 * Hashline 行锚点：短内容哈希定位编辑，避免模型复述 oldText。
 * 协议见 docs/HASHLINE_PROTOCOL.md（v0）。
 */
object HashlineAnchors {
    const val HASH_ID = "fnv1a16"
    const val MAX_OPS = 64
    /** 文件开头哨兵：仅 [Kind.INSERT_AFTER] 的 `after` 可用。 */
    val FILE_HEAD: Anchor = Anchor(0, "00")
    private val ANCHOR = Regex("^([1-9][0-9]{0,6}):([0-9a-f]{2})$")

    data class Op(
        val op: Kind,
        val start: Anchor? = null,
        val end: Anchor? = null,
        val after: Anchor? = null,
        val content: String? = null,
    )

    enum class Kind { REPLACE, INSERT_AFTER, DELETE }

    data class Anchor(val line: Int, val hash: String) {
        override fun toString(): String = "$line:$hash"
    }

    data class ApplyResult(
        val updated: String,
        val operations: Int,
        val linesRemoved: Int,
        val linesAdded: Int,
    )

    sealed class ApplyException(message: String) : IllegalArgumentException(message) {
        class Stale(message: String) : ApplyException(message)
        class BadAnchor(message: String) : ApplyException(message)
        class Overlap(message: String) : ApplyException(message)
        class Invalid(message: String) : ApplyException(message)
    }

    /** 规范化行：去掉尾部 CR，保留缩进与内部空白。 */
    fun normalizeLine(line: String): String =
        if (line.endsWith('\r')) line.dropLast(1) else line

    fun lineHash(normalized: String): String {
        var hash = 0x811c9dc5u
        for (b in normalized.toByteArray(Charsets.UTF_8)) {
            hash = hash xor b.toUByte().toUInt()
            hash *= 0x01000193u
        }
        return (hash and 0xFFu).toString(16).padStart(2, '0')
    }

    fun anchorOf(lineNumber: Int, rawLine: String): Anchor =
        Anchor(lineNumber, lineHash(normalizeLine(rawLine)))

    /**
     * 与 [WorkspaceFileAccess] 一致的逻辑行切分：按 `\n`；
     * 全文以 `\n` 结尾时不计入末尾空段。
     */
    fun logicalLines(content: String): List<String> {
        if (content.isEmpty()) return emptyList()
        val parts = content.split('\n')
        return if (content.endsWith('\n')) parts.dropLast(1) else parts
    }

    fun parseAnchor(raw: String): Anchor {
        val trimmed = raw.trim()
        if (trimmed == FILE_HEAD.toString()) return FILE_HEAD
        val match = ANCHOR.matchEntire(trimmed)
            ?: throw ApplyException.BadAnchor("锚点格式非法：$raw（期望 n:hh，如 12:a3；文件头插入可用 0:00）")
        return Anchor(match.groupValues[1].toInt(), match.groupValues[2])
    }

    fun formatLine(lineNumber: Int, rawLine: String): String {
        val normalized = normalizeLine(rawLine)
        return "$lineNumber:${lineHash(normalized)}|$normalized"
    }

    fun formatBody(lines: List<String>, startLineNumber: Int): String =
        lines.mapIndexed { i, line -> formatLine(startLineNumber + i, line) }.joinToString("\n")

    fun rangeHeader(
        displayPath: String,
        totalLines: Int,
        startInclusive: Int,
        endInclusive: Int,
        truncated: Boolean,
    ): String = buildString {
        append("[文件 $displayPath 共 $totalLines 行，当前显示第 $startInclusive-$endInclusive 行；hash=$HASH_ID")
        if (truncated) append("；内容未完，继续读取请用 offset=${endInclusive + 1}")
        append("]\n")
    }

    /** 小文件全文或分页窗口：有范围头 + 锚点正文。 */
    fun formatPage(
        displayPath: String,
        allLines: List<String>,
        startIndex0: Int,
        endIndex0Exclusive: Int,
    ): String {
        val total = allLines.size
        val start = startIndex0.coerceIn(0, total)
        val end = endIndex0Exclusive.coerceIn(start, total)
        if (total == 0) {
            return rangeHeader(displayPath, 0, 0, 0, truncated = false) + ""
        }
        val slice = allLines.subList(start, end)
        val startLine = start + 1
        val endLine = end
        return rangeHeader(displayPath, total, startLine, endLine, truncated = end < total) +
            formatBody(slice, startLine)
    }

    fun apply(content: String, ops: List<Op>): ApplyResult {
        if (ops.isEmpty()) throw ApplyException.Invalid("ops 不能为空")
        if (ops.size > MAX_OPS) throw ApplyException.Invalid("ops 最多 $MAX_OPS 条")
        val lines = logicalLines(content).map { normalizeLine(it) }.toMutableList()
        val usesCrlf = content.contains("\r\n") || logicalLines(content).any { it.endsWith('\r') }
        val planned = ops.map { plan(it, lines) }
        assertNoOverlap(planned)
        var removed = 0
        var added = 0
        // 同行号时按原 ops 逆序，保证同锚多 insert 也可预期。
        val ordered = planned.mapIndexed { index, item -> index to item }
            .sortedWith(compareByDescending<Pair<Int, Planned>> { it.second.sortKey }.thenByDescending { it.first })
        for ((_, item) in ordered) {
            when (item) {
                is Planned.Replace -> {
                    removed += item.endLine - item.startLine + 1
                    added += item.insertion.size
                    repeat(item.endLine - item.startLine + 1) { lines.removeAt(item.startLine - 1) }
                    lines.addAll(item.startLine - 1, item.insertion)
                }
                is Planned.Insert -> {
                    added += item.insertion.size
                    lines.addAll(item.afterLine, item.insertion)
                }
            }
        }
        // 空原文件无「尾换行」可继承；若任一 content 以换行结束，则新文件带尾换行（常见于 0:00 写入）。
        val trailingNewline = content.endsWith('\n') ||
            (content.isEmpty() && lines.isNotEmpty() && ops.any { contentEndsWithNewline(it.content) })
        return ApplyResult(joinLines(lines, usesCrlf, trailingNewline), ops.size, removed, added)
    }

    private fun contentEndsWithNewline(content: String?): Boolean {
        if (content.isNullOrEmpty()) return false
        return content.endsWith('\n') || content.endsWith("\r\n")
    }

    private sealed class Planned {
        abstract val sortKey: Int
        data class Replace(val startLine: Int, val endLine: Int, val insertion: List<String>) : Planned() {
            override val sortKey: Int get() = startLine
        }
        data class Insert(val afterLine: Int, val insertion: List<String>) : Planned() {
            override val sortKey: Int get() = afterLine
        }
    }

    private fun plan(op: Op, lines: List<String>): Planned = when (op.op) {
        Kind.REPLACE, Kind.DELETE -> {
            val start = op.start ?: throw ApplyException.Invalid("${op.op} 需要 start")
            val end = op.end ?: start
            if (op.op == Kind.DELETE && op.content != null) {
                throw ApplyException.Invalid("delete 不能带 content")
            }
            if (op.after != null) throw ApplyException.Invalid("${op.op} 不能带 after")
            if (start == FILE_HEAD || end == FILE_HEAD) {
                throw ApplyException.Invalid("${op.op} 不能使用文件头哨兵 0:00（仅 insert_after.after）")
            }
            verify(start, lines)
            verify(end, lines)
            if (start.line > end.line) throw ApplyException.BadAnchor("start 行号不能大于 end：$start .. $end")
            val insertion = if (op.op == Kind.DELETE) emptyList() else splitContent(op.content ?: "")
            Planned.Replace(start.line, end.line, insertion)
        }
        Kind.INSERT_AFTER -> {
            val after = op.after ?: throw ApplyException.Invalid("insert_after 需要 after")
            if (op.start != null || op.end != null) {
                throw ApplyException.Invalid("insert_after 不能带 start/end")
            }
            if (op.content == null) throw ApplyException.Invalid("insert_after 需要 content")
            if (after == FILE_HEAD) {
                Planned.Insert(0, splitContent(op.content))
            } else {
                verify(after, lines)
                Planned.Insert(after.line, splitContent(op.content))
            }
        }
    }

    private fun verify(anchor: Anchor, lines: List<String>) {
        if (anchor.line == 0) {
            throw ApplyException.BadAnchor("行号越界：0（文件头哨兵仅可用于 insert_after.after=0:00）")
        }
        if (anchor.line !in 1..lines.size) {
            throw ApplyException.BadAnchor("行号越界：${anchor.line}（文件共 ${lines.size} 行）")
        }
        val actual = anchorOf(anchor.line, lines[anchor.line - 1])
        if (actual.hash != anchor.hash) {
            throw ApplyException.Stale(
                "锚点已过期：期望 $anchor，磁盘现为 $actual（文件自上次 read 后已变化）。整次修改未写入。",
            )
        }
    }

    private fun assertNoOverlap(planned: List<Planned>) {
        data class Span(val start: Int, val end: Int, val label: String)
        val spans = planned.map { item ->
            when (item) {
                is Planned.Replace -> Span(item.startLine, item.endLine, "replace/delete ${item.startLine}-${item.endLine}")
                is Planned.Insert -> Span(item.afterLine, item.afterLine, "insert_after ${item.afterLine}")
            }
        }
        for (i in spans.indices) {
            for (j in i + 1 until spans.size) {
                val a = spans[i]
                val b = spans[j]
                val replaceOverlap = a.start <= b.end && b.start <= a.end &&
                    !(planned[i] is Planned.Insert || planned[j] is Planned.Insert)
                val insertIntoReplace = when {
                    planned[i] is Planned.Insert && planned[j] is Planned.Replace -> {
                        val r = planned[j] as Planned.Replace
                        (planned[i] as Planned.Insert).afterLine in r.startLine..r.endLine
                    }
                    planned[j] is Planned.Insert && planned[i] is Planned.Replace -> {
                        val r = planned[i] as Planned.Replace
                        (planned[j] as Planned.Insert).afterLine in r.startLine..r.endLine
                    }
                    else -> false
                }
                if (replaceOverlap || insertIntoReplace) {
                    throw ApplyException.Overlap("ops 区间冲突：${a.label} 与 ${b.label}")
                }
            }
        }
    }

    /** content 切行；末尾单个多余换行不产生额外空行。 */
    fun splitContent(content: String): List<String> {
        if (content.isEmpty()) return emptyList()
        val normalized = content.replace("\r\n", "\n").replace('\r', '\n')
        val parts = normalized.split('\n')
        return if (normalized.endsWith('\n')) parts.dropLast(1) else parts
    }

    private fun joinLines(lines: List<String>, crlf: Boolean, trailingNewline: Boolean): String {
        if (lines.isEmpty()) return if (trailingNewline) if (crlf) "\r\n" else "\n" else ""
        val sep = if (crlf) "\r\n" else "\n"
        val body = lines.joinToString(sep)
        return if (trailingNewline) body + sep else body
    }
}
