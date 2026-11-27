package top.wkbin.taixu.ui.workspace.codex

import android.content.Context
import android.util.AtomicFile
import java.io.File
import java.io.FileNotFoundException
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import top.wkbin.taixu.harness.codex.CodexException
import top.wkbin.taixu.harness.codex.CodexFailure
import top.wkbin.taixu.harness.codex.CodexWorkspace
import top.wkbin.taixu.harness.codex.isCodexThreadId

/** Only a per-project pointer. Credentials and transcript are owned by Codex. */
internal class WorkspaceCodexThreadStore(context: Context) {
    private val root = File(context.noBackupFilesDir, "workspace-codex")

    suspend fun read(target: CodexWorkspace): String? = withContext(Dispatchers.IO) {
        synchronized(lock) {
            val file = record(target)
            try {
                file.openRead().use { input ->
                    val buffer = ByteArray(129)
                    var length = 0
                    while (length < buffer.size) {
                        val read = input.read(buffer, length, buffer.size - length)
                        if (read < 0) break
                        length += read
                    }
                    val id = String(buffer, 0, length, Charsets.UTF_8)
                    id.takeIf(::isCodexThreadId) ?: throw CodexException(CodexFailure.HISTORY)
                }
            } catch (_: FileNotFoundException) {
                if (file.baseFile.exists()) throw CodexException(CodexFailure.STORAGE)
                null
            }
        }
    }

    suspend fun write(target: CodexWorkspace, id: String) = withContext(Dispatchers.IO) {
        require(isCodexThreadId(id))
        synchronized(lock) {
            val file = record(target)
            val stream = file.startWrite()
            try {
                stream.write(id.toByteArray(Charsets.UTF_8))
                file.finishWrite(stream)
            } catch (failure: Exception) {
                file.failWrite(stream)
                throw failure
            }
        }
    }

    suspend fun forget(target: CodexWorkspace) = withContext(Dispatchers.IO) {
        synchronized(lock) {
            val file = record(target)
            file.delete()
            if (file.baseFile.exists()) throw CodexException(CodexFailure.STORAGE)
        }
    }

    private fun record(target: CodexWorkspace): AtomicFile {
        val scope = listOf(target.distribution, target.project, target.directory).joinToString("\u0000")
        val name = MessageDigest.getInstance("SHA-256").digest(scope.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
        return AtomicFile(File(root, name))
    }

    private companion object {
        val lock = Any()
    }
}
