package top.wkbin.taixu.harness.groupchat

import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/** Each recipient gets its own copy before the prompt can start executing. */
internal suspend fun bindGroupAttachments(global: File, session: File, prompt: String) = withContext(Dispatchers.IO) {
    val folders = Regex("""/attachments/(bot-input/[a-fA-F0-9-]{36})/""")
        .findAll(prompt).map { it.groupValues[1] }.toSet()
    folders.forEach { folder ->
        val source = File(global, folder)
        if (!source.isDirectory) return@forEach
        check(source.canonicalFile == File(global.canonicalFile, folder)) { "群聊附件目录不可为链接" }
        val destination = File(session, folder)
        check(destination.canonicalFile == File(session.canonicalFile, folder)) { "会话附件目录不可为链接" }
        check(destination.mkdirs() || destination.isDirectory)
        source.listFiles().orEmpty().forEach { file ->
            currentCoroutineContext().ensureActive()
            check(file.isFile && !Files.isSymbolicLink(file.toPath())) { "群聊附件不是普通文件" }
            val target = File(destination, file.name)
            if (target.exists()) {
                check(!Files.isSymbolicLink(target.toPath()) && target.isFile) {
                    "会话附件不是普通文件"
                }
            } else {
                val staged = File.createTempFile("import-", ".tmp", destination)
                try {
                    file.inputStream().use { input -> staged.outputStream().use { input.copyTo(it) } }
                    currentCoroutineContext().ensureActive()
                    Files.move(staged.toPath(), target.toPath())
                } finally { staged.delete() }
            }
        }
    }
}
