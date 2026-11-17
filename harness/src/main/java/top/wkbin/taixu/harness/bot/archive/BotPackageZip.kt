package top.wkbin.taixu.harness.bot.archive

import java.io.File
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * 把 Bot 记忆目录写入 ZIP。
 *
 * `includeHistory = true` 时保留 `.git`（导入后可回滚）；`false` 时整段 `.git` 都不进包，
 * 避免已删除内容的旧提交随包分享。Git 锁文件始终跳过。
 */
internal fun writeMemoryToZip(zip: ZipOutputStream, memoryDir: File, includeHistory: Boolean) {
    if (!memoryDir.isDirectory) return
    memoryDir.walkTopDown().filter { it != memoryDir }.forEach { entry ->
        val relative = entry.relativeTo(memoryDir).invariantSeparatorsPath
        if (!includeHistory && relative.split('/').any { it == ".git" }) return@forEach
        if (relative == ".git/index.lock" || relative.endsWith("/index.lock")) return@forEach
        val name = "memory/$relative"
        if (entry.isDirectory) {
            zip.putNextEntry(ZipEntry("$name/"))
            zip.closeEntry()
        } else if (!Files.isSymbolicLink(entry.toPath())) {
            zip.putNextEntry(ZipEntry(name))
            entry.inputStream().use { input -> input.copyTo(zip) }
            zip.closeEntry()
        }
    }
}
