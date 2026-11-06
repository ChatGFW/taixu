package top.wkbin.taixu.core.markdown

sealed interface MdBlock

data class MdParagraph(val text: String) : MdBlock
data class MdHeading(val level: Int, val text: String) : MdBlock
data class MdCodeBlock(val language: String, val code: String) : MdBlock
data class MdList(val ordered: Boolean, val items: List<String>) : MdBlock
data class MdQuote(val lines: List<String>) : MdBlock
data class MdTable(val headers: List<String>, val rows: List<List<String>>) : MdBlock
data class MdRemoteMedia(val url: String, val description: String) : MdBlock
object MdHr : MdBlock
