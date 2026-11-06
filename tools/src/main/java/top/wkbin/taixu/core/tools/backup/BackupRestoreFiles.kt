package top.wkbin.taixu.core.tools.backup

import android.util.AtomicFile
import java.io.File
import java.nio.file.Files
import java.util.UUID
import kotlinx.serialization.json.*
import top.wkbin.taixu.runtime.AttachmentPaths

/** Publishes complete files and records external destinations before they become visible. */
internal class BackupRestoreFiles(private val locations: BackupLocations, private val root: File) {
    private val ledger = File(root, ".restore-targets")
    private val allowedRoots = listOf(locations.attachments, AttachmentPaths.global(locations.files))

    fun install(source: File, target: File) {
        validate(target)
        val digest = BackupArchive.digest(source)
        if (target.exists()) {
            check(target.isFile && target.length() == source.length() && BackupArchive.digest(target) == digest) {
                "恢复目标已存在且内容不同：${target.name}"
            }
            return
        }
        check(target.parentFile!!.mkdirs() || target.parentFile!!.isDirectory)
        val staged = File.createTempFile("publish-", ".tmp", root)
        var receipt: File? = null
        try {
            source.inputStream().use { input -> staged.outputStream().use { input.copyTo(it) } }
            check(staged.length() == source.length() && BackupArchive.digest(staged) == digest) { "恢复文件复制不完整" }
            if (!target.toPath().startsWith(root.toPath())) {
                check(ledger.mkdirs() || ledger.isDirectory)
                receipt = File(ledger, UUID.randomUUID().toString() + ".json")
                val atomic = AtomicFile(receipt)
                val output = atomic.startWrite()
                try {
                    output.write(buildJsonObject {
                        put("path", target.absolutePath)
                        put("sha256", digest)
                    }.toString().toByteArray(Charsets.UTF_8))
                    atomic.finishWrite(output)
                } catch (failure: Exception) { atomic.failWrite(output); throw failure }
            }
            try {
                // No REPLACE_EXISTING: an intervening writer retains its file.
                Files.move(staged.toPath(), target.toPath())
            } catch (failure: Exception) {
                receipt?.delete()
                throw failure
            }
        } finally { staged.delete() }
    }

    fun rollback() {
        ledger.listFiles().orEmpty().filter { it.extension == "json" }.forEach { receipt ->
            check(receipt.isFile && receipt.length() <= 16_384) { "恢复资源日志无效" }
            val record = BackupArchive.json.parseToJsonElement(receipt.readText()).jsonObject
            val target = File(record.getValue("path").jsonPrimitive.content)
            validate(target)
            // Preserve a file changed by a later writer, instead of deleting their content.
            if (target.isFile && BackupArchive.digest(target) == record.getValue("sha256").jsonPrimitive.content) {
                check(target.delete()) { "无法回滚恢复资源" }
            }
        }
    }

    fun complete() {
        check(!ledger.exists() || ledger.deleteRecursively()) { "无法清理恢复资源日志" }
    }

    private fun validate(target: File) {
        val canonical = target.canonicalFile
        val path = target.absoluteFile.normalize().toPath()
        check(allowedRoots.any { base ->
            val basePath = base.absoluteFile.normalize().toPath()
            path != basePath && path.startsWith(basePath) &&
                canonical == File(base.canonicalFile, basePath.relativize(path).toString()).normalize()
        }) { "恢复资源目标越界" }
    }
}
