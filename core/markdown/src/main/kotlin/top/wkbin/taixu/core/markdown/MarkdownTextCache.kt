package top.wkbin.taixu.core.markdown

/** Exact-content LRU with a source-character budget; it owns no platform or UI objects. */
internal class MarkdownTextCache<V>(private val maxEntries: Int, private val maxSourceChars: Int) {
    private data class Entry<V>(val source: String, val value: V)
    private val entries = LinkedHashMap<String, Entry<V>>()
    internal var sourceChars = 0
        private set
    internal val size: Int get() = synchronized(this) { entries.size }

    init { require(maxEntries > 0 && maxSourceChars > 0) }

    @Synchronized fun get(key: String, source: String): V? {
        val entry = entries[key] ?: return null
        if (entry.source != source) return null
        entries.remove(key)
        entries[key] = entry
        return entry.value
    }

    @Synchronized fun put(key: String, source: String, value: V) {
        entries.remove(key)?.let { sourceChars -= it.source.length }
        if (source.length > maxSourceChars) return
        entries[key] = Entry(source, value)
        sourceChars += source.length
        while (entries.size > maxEntries || sourceChars > maxSourceChars) {
            val oldest = entries.entries.iterator().next()
            sourceChars -= oldest.value.source.length
            entries.remove(oldest.key)
        }
    }

    @Synchronized fun evictAll() { entries.clear(); sourceChars = 0 }
}
