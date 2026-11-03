package top.wkbin.taixu.harness

/**
 * 解析 `data:image/...;base64,<payload>`。用 indexOf 切分，避免对大 base64 串
 * 编译正则 + matchEntire + groupValues 双份拷贝。
 */
internal fun parseDataImageUrl(url: String): Pair<String, String>? {
    if (!url.startsWith("data:")) return null
    val marker = url.indexOf(";base64,")
    if (marker <= "data:".length) return null
    val mediaType = url.substring("data:".length, marker)
    if (!mediaType.startsWith("image/")) return null
    val subtype = mediaType.removePrefix("image/")
    // 对齐原正则 image/[a-zA-Z+]+。
    if (subtype.isEmpty() || subtype.any { ch -> ch !in 'a'..'z' && ch !in 'A'..'Z' && ch != '+' }) {
        return null
    }
    val payload = url.substring(marker + ";base64,".length)
    if (payload.isEmpty()) return null
    return mediaType to payload
}
