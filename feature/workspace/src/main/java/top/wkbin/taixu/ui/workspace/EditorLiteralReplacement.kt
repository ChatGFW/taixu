package top.wkbin.taixu.ui.workspace

/** Null means the replaced document would exceed the workspace write limit. */
internal fun replaceEditorMatches(
    source: String,
    query: String,
    replacement: String,
    matchCase: Boolean,
    maxChars: Int,
): String? {
    if (query.isEmpty()) return source
    val result = StringBuilder(minOf(source.length, maxChars))
    var end = 0
    while (true) {
        val match = source.indexOf(query, end, ignoreCase = !matchCase)
        if (match < 0) break
        if (result.length.toLong() + (match - end) + replacement.length > maxChars) return null
        result.append(source, end, match).append(replacement)
        end = match + query.length
    }
    if (result.length.toLong() + (source.length - end) > maxChars) return null
    return result.append(source, end, source.length).toString()
}
