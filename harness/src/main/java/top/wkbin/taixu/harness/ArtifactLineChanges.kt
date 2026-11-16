package top.wkbin.taixu.harness

import kotlinx.coroutines.CancellationException
import kotlinx.serialization.Serializable
import top.wkbin.taixu.harness.text.UnifiedDiffGenerator

/** Per-mutation line counts; null means no complete text comparison was available. */
@Serializable
data class ArtifactLineChanges(val added: Int, val removed: Int)

internal object ArtifactTextSnapshot {
    private const val MAX_BYTES = 1024L * 1024

    suspend fun before(files: WorkspaceToolOperations, path: String): String? = safely {
        val size = files.fileSizeOrNull(path)
        if (size != null) return@safely read(files, path, size)
        // A failed read/size lookup is not proof that the file is new.
        if (files.existsOrNull(path) == false) "" else null
    }

    suspend fun changes(files: WorkspaceToolOperations, path: String, before: String?, knownAfter: String? = null): ArtifactLineChanges? = safely {
        if (before == null) return@safely null
        val after = if (knownAfter != null) knownAfter.takeIf(::validText) else {
            val size = files.fileSizeOrNull(path) ?: return@safely null
            read(files, path, size)
        } ?: return@safely null
        val (added, removed) = UnifiedDiffGenerator.lineCounts(before, after)
        ArtifactLineChanges(added, removed)
    }

    private suspend fun read(files: WorkspaceToolOperations, path: String, size: Long): String? {
        if (size !in 0..MAX_BYTES) return null
        return files.readTextForEdit(path).getOrNull()?.takeIf(::validText)
    }

    private fun validText(text: String) = '\u0000' !in text && utf8Fits(text)

    // Count without allocating another full UTF-8 byte array. Recheck after reading
    // because the file may have grown since the size probe.
    private fun utf8Fits(text: String): Boolean {
        var bytes = 0L
        var index = 0
        while (index < text.length && bytes <= MAX_BYTES) {
            val char = text[index++]
            bytes += when {
                char.code < 0x80 -> 1
                char.code < 0x800 -> 2
                char.isHighSurrogate() && index < text.length && text[index].isLowSurrogate() -> { index++; 4 }
                char.isSurrogate() -> 1
                else -> 3
            }
        }
        return bytes <= MAX_BYTES
    }

    private suspend fun <T> safely(block: suspend () -> T?): T? = try { block() }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { null }
}
