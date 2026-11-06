package top.wkbin.taixu.core.document

import java.io.File
import org.xmlpull.v1.XmlPullParser

/** DOCX main body, PPTX presentation order, and EPUB spine order; no embedded assets execute. */
object ZipDocumentExtractor {
    fun extract(file: File, format: DocumentFormat, limits: ExtractionLimits = ExtractionLimits()): DocumentExtraction {
        val output = ExtractedText(limits.maxTextChars)
        return try {
            validateInput(file, limits)
            DocumentArchive(file, ExtractionBudget(limits)).use { archive ->
                try {
                    when (format) {
                        DocumentFormat.DOCX -> body(archive, "word/document.xml", output)
                        DocumentFormat.PPTX -> slides(archive).forEachIndexed { index, path ->
                            output.line(); output.append("[幻灯片 ${index + 1}]\n", semantic = false); body(archive, path, output)
                        }
                        DocumentFormat.EPUB -> chapters(archive).forEach { path -> html(archive, path, output); output.line() }
                        else -> return DocumentExtraction.Unavailable(DocumentExtraction.Reason.UNSUPPORTED)
                    }
                } catch (_: TextFull) { output.truncated = true }
            }
            output.finish()
        } catch (e: ExtractionStop) { DocumentExtraction.Unavailable(e.reason) }
        catch (_: Exception) { DocumentExtraction.Unavailable(DocumentExtraction.Reason.INVALID) }
    }

    private fun body(archive: DocumentArchive, path: String, output: ExtractedText) = archive.parser(path) { parser ->
        var inText = false
        while (true) when (archive.next(parser)) {
            XmlPullParser.START_TAG -> when (parser.name) {
                "t" -> inText = true
                "br", "cr" -> output.append("\n")
                "tab" -> output.append("\t")
            }
            XmlPullParser.TEXT, XmlPullParser.CDSECT, XmlPullParser.ENTITY_REF -> if (inText) output.append(archive.text(parser))
            XmlPullParser.END_TAG -> when (parser.name) {
                "t" -> inText = false
                "p" -> output.line()
            }
            XmlPullParser.END_DOCUMENT -> return@parser
        }
    }

    private fun slides(archive: DocumentArchive): List<String> {
        val relationships = mutableMapOf<String, String>()
        archive.parser("ppt/_rels/presentation.xml.rels") { parser ->
            while (true) when (archive.next(parser)) {
                XmlPullParser.START_TAG -> if (parser.name == "Relationship" && attr(parser, "Type").endsWith("/slide")) {
                    require(attr(parser, "TargetMode") != "External")
                    require(relationships.size < archive.budget.limits.maxParts)
                    val path = DocumentArchive.resolve("ppt/presentation.xml", attr(parser, "Target"))
                    require(path.startsWith("ppt/slides/") && path.endsWith(".xml"))
                    require(relationships.put(attr(parser, "Id"), path) == null)
                }
                XmlPullParser.END_DOCUMENT -> return@parser
            }
        }
        val order = mutableListOf<String>()
        archive.parser("ppt/presentation.xml") { parser ->
            while (true) when (archive.next(parser)) {
                XmlPullParser.START_TAG -> if (parser.name == "sldId") {
                    require(order.size < archive.budget.limits.maxParts)
                    val id = (0 until parser.attributeCount).firstOrNull {
                        parser.getAttributeName(it) == "id" && parser.getAttributeNamespace(it).endsWith("/relationships")
                    }?.let(parser::getAttributeValue) ?: error("Missing slide relationship")
                    order += relationships[id] ?: error("Missing slide")
                }
                XmlPullParser.END_DOCUMENT -> return@parser
            }
        }
        require(order.distinct().size == order.size)
        return order
    }

    private fun chapters(archive: DocumentArchive): List<String> {
        var packagePath: String? = null
        archive.parser("META-INF/container.xml") { parser ->
            while (true) when (archive.next(parser)) {
                XmlPullParser.START_TAG -> if (parser.name == "rootfile" && packagePath == null)
                    packagePath = DocumentArchive.resolve("", attr(parser, "full-path"))
                XmlPullParser.END_DOCUMENT -> return@parser
            }
        }
        val opf = requireNotNull(packagePath)
        val manifest = mutableMapOf<String, String>()
        val order = mutableListOf<String>()
        archive.parser(opf) { parser ->
            while (true) when (archive.next(parser)) {
                XmlPullParser.START_TAG -> when (parser.name) {
                    "item" -> if (attr(parser, "media-type") in setOf("application/xhtml+xml", "text/html")) {
                        require(manifest.size < archive.budget.limits.maxParts)
                        require(manifest.put(attr(parser, "id"), DocumentArchive.resolve(opf, attr(parser, "href"))) == null)
                    }
                    "itemref" -> {
                        require(order.size < archive.budget.limits.maxParts)
                        order += attr(parser, "idref")
                    }
                }
                XmlPullParser.END_DOCUMENT -> return@parser
            }
        }
        return order.map { manifest[it] ?: error("Unsupported or missing spine item") }
    }

    private fun html(archive: DocumentArchive, path: String, output: ExtractedText) = archive.parser(path) { parser ->
        var bodyDepth = 0
        var ignoredDepth = 0
        while (true) when (archive.next(parser)) {
            XmlPullParser.START_TAG -> {
                val tag = parser.name.lowercase()
                if (tag == "body") bodyDepth = parser.depth
                if (tag in setOf("script", "style", "head") && ignoredDepth == 0) ignoredDepth = parser.depth
                if (bodyDepth > 0 && ignoredDepth == 0 && tag in blocks) output.line()
            }
            XmlPullParser.TEXT, XmlPullParser.CDSECT, XmlPullParser.ENTITY_REF -> if (bodyDepth > 0 && ignoredDepth == 0)
                output.append(archive.text(parser).replace(Regex("\\s+"), " "))
            XmlPullParser.END_TAG -> {
                if (parser.depth == ignoredDepth) ignoredDepth = 0
                if (bodyDepth > 0 && ignoredDepth == 0 && parser.name.lowercase() in blocks) output.line()
                if (parser.name == "body") bodyDepth = 0
            }
            XmlPullParser.END_DOCUMENT -> return@parser
        }
    }

    private fun attr(parser: XmlPullParser, name: String) = parser.getAttributeValue(null, name).orEmpty()
    private val blocks = setOf("p", "div", "br", "li", "h1", "h2", "h3", "h4", "h5", "h6", "tr", "blockquote")
}
