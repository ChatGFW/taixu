package top.wkbin.taixu.runtime

import java.io.File
import java.io.InputStream

/**
 * 从消息正文里取出 `/attachments/<name>` 的一级名字。
 *
 * 规则与原先的正则一致：名字由 `[A-Za-z0-9._-]` 组成、最长 255，且不能再接一个同类字符；
 * `skills` 与 `legacy` 是目录，不算附件。匹配用字面量查找完成，避免 ICU 正则扫过多 MB 的工具结果。
 */
internal object AttachmentReferenceScan {
    private const val NEEDLE = "/attachments/"
    private val NEEDLE_BYTES = NEEDLE.encodeToByteArray()
    private const val MAX_NAME_LENGTH = 255

    /** 跨块保留：半截 needle + 最长名字 + 一个前瞻字节。 */
    private const val TAIL = (NEEDLE.length - 1) + MAX_NAME_LENGTH + 1

    fun names(payload: String, limitTo: Set<String>? = null): Set<String> {
        if (limitTo != null && limitTo.isEmpty()) return emptySet()
        if (!payload.contains(NEEDLE)) return emptySet()
        return names(payload.encodeToByteArray(), limitTo)
    }

    fun names(file: File, chunkSize: Int = 64 * 1024, limitTo: Set<String>? = null): Set<String> {
        if (limitTo != null && limitTo.isEmpty()) return emptySet()
        return file.inputStream().buffered().use { names(it, chunkSize, limitTo) }
    }

    private fun names(bytes: ByteArray, limitTo: Set<String>?): Set<String> {
        val found = linkedSetOf<String>()
        consume(bytes, found, commitThrough = bytes.size, eof = true, limitTo = limitTo)
        return found
    }

    private fun names(input: InputStream, chunkSize: Int, limitTo: Set<String>?): Set<String> {
        val found = linkedSetOf<String>()
        val chunk = ByteArray(chunkSize.coerceAtLeast(1))
        var pending = ByteArray(0)
        while (true) {
            if (limitTo != null && found.size == limitTo.size) return found
            val read = input.read(chunk)
            if (read < 0) {
                if (pending.isNotEmpty()) {
                    consume(pending, found, commitThrough = pending.size, eof = true, limitTo = limitTo)
                }
                return found
            }
            if (read == 0) continue
            val incoming = chunk.copyOf(read)
            val data = if (pending.isEmpty()) incoming else pending + incoming
            val keep = consume(
                data,
                found,
                commitThrough = (data.size - TAIL).coerceAtLeast(0),
                eof = false,
                limitTo = limitTo,
            )
            pending = if (keep >= data.size) ByteArray(0) else data.copyOfRange(keep, data.size)
        }
    }

    /**
     * 只提交起点落在 [commitThrough] 之前的匹配；名字可以伸进尾部。
     * 返回下一块必须保留的起始下标。
     */
    private fun consume(
        data: ByteArray,
        found: MutableSet<String>,
        commitThrough: Int,
        eof: Boolean,
        limitTo: Set<String>?,
    ): Int {
        val startLimit = if (eof) data.size else commitThrough.coerceIn(0, data.size)
        var index = 0
        while (index < startLimit) {
            if (limitTo != null && found.size == limitTo.size) return index
            val at = indexOfNeedle(data, index, startLimit)
            if (at < 0) return startLimit
            val decision = takeName(data, at, eof) ?: return at
            val name = decision.name
            if (name != null && (limitTo == null || name in limitTo)) found.add(name)
            index = if (decision.next > index) decision.next else index + 1
        }
        return index.coerceAtMost(data.size)
    }

    private fun indexOfNeedle(data: ByteArray, from: Int, startLimit: Int): Int {
        val last = minOf(startLimit - 1, data.size - NEEDLE_BYTES.size)
        var index = from
        val first = NEEDLE_BYTES[0]
        while (index <= last) {
            if (data[index] == first && matchesNeedle(data, index)) return index
            index++
        }
        return -1
    }

    private fun matchesNeedle(data: ByteArray, at: Int): Boolean {
        for (offset in 1 until NEEDLE_BYTES.size) {
            if (data[at + offset] != NEEDLE_BYTES[offset]) return false
        }
        return true
    }

    private data class NameMatch(val name: String?, val next: Int)

    private fun takeName(data: ByteArray, at: Int, eof: Boolean): NameMatch? {
        val nameStart = at + NEEDLE_BYTES.size
        if (nameStart > data.size) return if (eof) NameMatch(null, at + 1) else null
        if (nameStart == data.size || !isNameStart(data[nameStart])) return NameMatch(null, at + 1)

        val maxEnd = nameStart + MAX_NAME_LENGTH
        var end = nameStart + 1
        while (end < data.size && end < maxEnd && isNameChar(data[end])) end++
        if (end == data.size && end < maxEnd && !eof) return null
        if (end == maxEnd && end == data.size && !eof) return null
        if (end == maxEnd && end < data.size && isNameChar(data[end])) return NameMatch(null, end)

        val name = data.decodeToString(nameStart, end)
        return NameMatch(name.takeUnless { it == "skills" || it == "legacy" }, end)
    }

    private fun isNameStart(byte: Byte): Boolean {
        val value = byte.toInt() and 0xFF
        return value in 'A'.code..'Z'.code || value in 'a'.code..'z'.code || value in '0'.code..'9'.code
    }

    private fun isNameChar(byte: Byte): Boolean =
        isNameStart(byte) || byte == '.'.code.toByte() || byte == '_'.code.toByte() || byte == '-'.code.toByte()
}
