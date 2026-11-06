package top.wkbin.taixu.runtime.gui

import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.Base64
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import top.wkbin.taixu.runtime.privilege.ShellExecResult

/** Shell owns the capture file, while the app alone writes its private destination. */
internal suspend fun captureHostScreenshot(
    targetPath: String,
    execute: suspend (String) -> ShellExecResult,
): Result<String> {
    val remote = "/data/local/tmp/taixu-shot-${UUID.randomUUID()}.png"
    val target = File(targetPath)
    var staged: File? = null
    try {
        val capture = execute("/system/bin/screencap -p '$remote' && /system/bin/wc -c < '$remote'")
        check(capture.success) { capture.stderr.ifBlank { "截图失败" } }
        val size = capture.stdout.trim().toLongOrNull() ?: error("截图大小无效")
        check(size in 8..MAX_SCREENSHOT_BYTES) { "截图为空或超过 16 MiB" }
        check(target.parentFile!!.mkdirs() || target.parentFile!!.isDirectory)
        staged = File.createTempFile("screenshot-", ".png", target.parentFile)
        staged.outputStream().use { output ->
            var offset = 0L
            while (offset < size) {
                // Encoded output stays below HostProcessRunner's 64 KiB and Binder limits.
                val count = minOf(CHUNK_BYTES.toLong(), size - offset).toInt()
                val chunk = execute("/system/bin/dd if='$remote' bs=$CHUNK_BYTES skip=${offset / CHUNK_BYTES} count=1 2>/dev/null | /system/bin/base64")
                check(chunk.success) { chunk.stderr.ifBlank { "截图传输失败" } }
                val bytes = Base64.getDecoder().decode(chunk.stdout.filterNot { it.isWhitespace() })
                check(bytes.size == count) { "截图传输不完整" }
                if (offset == 0L) check(bytes.take(8).toByteArray().contentEquals(PNG_SIGNATURE)) { "截图不是 PNG" }
                output.write(bytes)
                offset += bytes.size
            }
        }
        Files.move(staged.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
        return Result.success("屏幕截图已保存至 $targetPath")
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (failure: Exception) {
        return Result.failure(failure)
    } finally {
        staged?.delete()
        withContext(NonCancellable) { runCatching { execute("/system/bin/rm -f '$remote'") } }
    }
}

private const val CHUNK_BYTES = 32 * 1024
private const val MAX_SCREENSHOT_BYTES = 16L * 1024 * 1024
private val PNG_SIGNATURE = byteArrayOf(0x89.toByte(), 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a)
