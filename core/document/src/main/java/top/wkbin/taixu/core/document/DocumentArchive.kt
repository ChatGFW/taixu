package top.wkbin.taixu.core.document

import java.io.File
import java.io.FilterInputStream
import java.net.URI
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory

/** Inventory only; no entry is ever unpacked to a filesystem or fetched from the network. */
internal class DocumentArchive(file: File, val budget: ExtractionBudget) : AutoCloseable {
    private val zip = ZipFile(file)
    val entries: Map<String, ZipEntry>
    init {
        try {
            val found = linkedMapOf<String, ZipEntry>()
            val iterator = zip.entries()
            while (iterator.hasMoreElements()) {
                budget.check()
                val entry = iterator.nextElement()
                if (found.size >= budget.limits.maxEntries) throw ExtractionStop(DocumentExtraction.Reason.LIMIT)
                require(validName(entry.name))
                require(found.put(entry.name, entry) == null)
            }
            entries = found
        } catch (e: Exception) { zip.close(); throw e }
    }
    fun parser(path: String, block: (XmlPullParser) -> Unit) {
        budget.check()
        val entry = entries[path] ?: throw ExtractionStop(DocumentExtraction.Reason.INVALID)
        if (entry.isDirectory || entry.size > budget.limits.maxEntryBytes) throw ExtractionStop(DocumentExtraction.Reason.LIMIT)
        zip.getInputStream(entry).use { stream ->
            val bounded = object : FilterInputStream(stream) {
                private var bytes = 0L
                private fun count(n: Int): Int {
                    if (n > 0) { bytes += n; budget.bytes(n) }
                    if (bytes > budget.limits.maxEntryBytes) throw ExtractionStop(DocumentExtraction.Reason.LIMIT)
                    return n
                }
                override fun read() = super.read().also { if (it >= 0) count(1) }
                override fun read(b: ByteArray, off: Int, len: Int) = count(`in`.read(b, off, len))
            }
            val parser = XmlPullParserFactory.newInstance().apply { isNamespaceAware = true }.newPullParser()
            parser.setFeature(XmlPullParser.FEATURE_PROCESS_DOCDECL, false)
            parser.setInput(bounded, null)
            parser.defineEntityReplacementText("nbsp", " ")
            block(parser)
        }
    }
    fun next(parser: XmlPullParser): Int {
        budget.check()
        val event = parser.nextToken()
        // DTDs are never resolved. Reject entity declarations; a simple XHTML DOCTYPE is harmless.
        if (event == XmlPullParser.DOCDECL && parser.text.contains("<!ENTITY", ignoreCase = true))
            throw ExtractionStop(DocumentExtraction.Reason.INVALID)
        if (event == XmlPullParser.ENTITY_REF && parser.text == null)
            throw ExtractionStop(DocumentExtraction.Reason.INVALID)
        if (parser.depth > 64) throw ExtractionStop(DocumentExtraction.Reason.LIMIT)
        return event
    }
    fun text(parser: XmlPullParser): String = parser.text.orEmpty()
    override fun close() = zip.close()

    companion object {
        private fun validName(name: String): Boolean = name.length in 1..1024 && !name.startsWith('/') &&
            name.none { it == '\\' || it == ':' || it.isISOControl() } && name.trimEnd('/').split('/').none { it.isEmpty() || it == "." || it == ".." }
        fun resolve(base: String, href: String): String {
            val uri = URI(href)
            require(!uri.isAbsolute && uri.rawAuthority == null && uri.rawQuery == null)
            val path = uri.path ?: error("Missing entry path")
            require(!path.startsWith('/') && path.none { it == '\\' || it.isISOControl() })
            val parts = base.substringBeforeLast('/', "").split('/').filter { it.isNotEmpty() }.toMutableList()
            for (part in path.split('/')) when (part) {
                "", "." -> Unit
                ".." -> { require(parts.isNotEmpty()); parts.removeAt(parts.lastIndex) }
                else -> parts += part
            }
            return parts.joinToString("/").also { require(validName(it)) }
        }
    }
}
