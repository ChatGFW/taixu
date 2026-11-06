package top.wkbin.taixu.core.document

import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

object DocumentSidecar {
    /** Same-directory atomic publication. A failure never changes the original document. */
    fun write(original: File, text: DocumentExtraction.Text): File? {
        val target = File(original.parentFile, original.name + ".txt")
        var temporary: File? = null
        return try {
            require(text.content.length <= ExtractionLimits().maxTextChars)
            temporary = File.createTempFile(".document-", ".tmp", original.parentFile)
            temporary.outputStream().bufferedWriter(Charsets.UTF_8).use { it.write(text.content) }
            Files.move(temporary.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
            target
        } catch (_: Exception) { null }
        finally { temporary?.delete() }
    }
}
