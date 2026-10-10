package top.wkbin.taixu.harness

import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.security.MessageDigest
import java.util.Locale
import kotlinx.coroutines.flow.collect
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import top.wkbin.taixu.core.network.DownloadEvent
import top.wkbin.taixu.core.network.DownloadRequest
import top.wkbin.taixu.core.network.FileDownloader
import top.wkbin.taixu.harness.core.ToolBackend

data class DownloadToolRequest(
    val args: JsonObject,
    val sessionId: String,
    val workspace: String,
    val progressReporter: (suspend (String) -> Unit)?,
)

/**
 * Agent 工具后端：HTTPS 文件下载（download 工具）。
 *
 * 职责边界：
 * - 只负责网络下载调用（FileDownloader）、进度上报与下载后的图片 magic 验证。
 * - 输出截断、脱敏等横切关注点由 ToolExecutor 管道层处理。
 */
class DownloadToolBackend(
    private val fileDownloader: FileDownloader,
    private val fileAccess: WorkspaceFileAccess,
    private val mutationSnapshots: WorkspaceMutationSnapshots,
) : ToolBackend<DownloadToolRequest, Pair<Boolean, String>> {

    override suspend fun execute(request: DownloadToolRequest): Pair<Boolean, String> {
        val activeFileAccess = if (request.workspace.isNotBlank())
            fileAccess.withBase(request.workspace) else fileAccess
        if (request.args["destination"]?.jsonPrimitive?.content?.trim()?.isNotBlank() == true) {
            mutationSnapshots.before(request.sessionId, activeFileAccess, JsonArgs.requireString(request.args, "destination"))
        }
        return executeDownload(request.args, activeFileAccess, request.progressReporter)
    }

    private suspend fun executeDownload(
        args: JsonObject,
        activeFileAccess: WorkspaceFileAccess,
        progressReporter: (suspend (String) -> Unit)?,
    ): Pair<Boolean, String> {
        val url = JsonArgs.requireString(args, "url")
        require(url.startsWith("https://", ignoreCase = true)) { "下载地址必须使用 HTTPS" }
        val destinationPath = JsonArgs.requireString(args, "destination")
        val destination = activeFileAccess.resolveDownloadDestination(destinationPath)
        val sha256 = args["sha256"]?.jsonPrimitive?.content?.trim()?.takeIf { it.isNotEmpty() }
        val maxAttempts = JsonArgs.optionalLong(args, "max_attempts", DEFAULT_DOWNLOAD_ATTEMPTS, 1L, MAX_DOWNLOAD_ATTEMPTS).toInt()
        val maxBytes = JsonArgs.optionalLong(args, "max_bytes", DEFAULT_DOWNLOAD_MAX_BYTES, 1L, MAX_DOWNLOAD_MAX_BYTES)
        var latestProgress: DownloadEvent.Progress? = null
        var verified = false
        var completedFile: File? = null
        val startedAt = System.currentTimeMillis()
        var lastReportedAt = 0L
        fileDownloader.download(
            DownloadRequest(
                url = url,
                destination = destination,
                sha256 = sha256,
                maxAttempts = maxAttempts,
                maxBytes = maxBytes,
            ),
        ).collect { event ->
            when (event) {
                is DownloadEvent.Progress -> {
                    latestProgress = event
                    val now = System.currentTimeMillis()
                    if (progressReporter != null && (now - lastReportedAt >= PROGRESS_REPORT_INTERVAL_MS || event.totalBytes != null && event.downloadedBytes == event.totalBytes)) {
                        lastReportedAt = now
                        progressReporter(formatDownloadProgress(event, startedAt))
                    }
                }
                DownloadEvent.Verifying -> {
                    verified = true
                    progressReporter?.invoke("正在校验下载文件 SHA-256…")
                }
                is DownloadEvent.Completed -> completedFile = event.file
                DownloadEvent.Started -> Unit
            }
        }
        val file = completedFile ?: destination
        val size = file.length()
        if (hasImageExtension(destination) && !isImageMagic(destination)) {
            return false to buildString {
                append("下载失败：目标应为图片，但内容不是有效的图片格式（JPEG/PNG/GIF/WebP/BMP）。")
                append("\n来源：").append(url)
                append("\n大小：").append(size).append(" bytes")
                append("\n常见原因：图床反爬/防盗链返回了占位图或错误页，或链接已过期。")
                append("\n建议：更换图源（换站点/换域名）或稍后重试，不要用相同 URL 原样重试。")
            }
        }
        val duplicateOf = findDuplicateDownload(destination)
        val body = buildString {
            append("下载完成：").append(destinationPath)
            append("\n大小：").append(size).append(" bytes")
            latestProgress?.totalBytes?.let { append(" / ").append(it).append(" bytes") }
            append("\n特性：HTTPS、HTTP Range 断点续传、自动重试（最多 ").append(maxAttempts).append(" 次）")
            if (verified) append("\nSHA-256：已校验")
            append("\n说明：当前下载器是单连接续传，不是多线程分片下载。")
            if (duplicateOf != null) {
                append("\n\n警告：本次下载内容与工作区已有文件 ").append(duplicateOf)
                append(" 完全相同（SHA-256 一致），疑似图床反爬占位图。请立即更换图源（换站点/换域名），")
                append("不要继续从同一图床高频下载，也不要重复下载相同的 URL。")
            }
        }
        return true to body
    }

    private fun hasImageExtension(file: File): Boolean =
        file.extension.lowercase() in IMAGE_DOWNLOAD_EXTENSIONS

    private fun isImageMagic(file: File): Boolean {
        val head = ByteArray(16)
        val read = try {
            FileInputStream(file).use { it.read(head) }
        } catch (_: IOException) {
            return true
        }
        if (read < 3) return false
        val jpeg = head[0] == 0xFF.toByte() && head[1] == 0xD8.toByte() && head[2] == 0xFF.toByte()
        val png = read >= 8 && head[0] == 0x89.toByte() && head[1] == 0x50.toByte() && head[2] == 0x4E.toByte() && head[3] == 0x47.toByte()
        val gif = head[0] == 0x47.toByte() && head[1] == 0x49.toByte() && head[2] == 0x46.toByte()
        val bmp = head[0] == 0x42.toByte() && head[1] == 0x4D.toByte()
        val webp = read >= 12 && head[0] == 0x52.toByte() && head[1] == 0x49.toByte() && head[2] == 0x46.toByte() && head[3] == 0x46.toByte() &&
            head[8] == 0x57.toByte() && head[9] == 0x45.toByte() && head[10] == 0x42.toByte() && head[11] == 0x50.toByte()
        return jpeg || png || gif || bmp || webp
    }

    /** 检测同目录下是否已有字节完全相同的文件（反爬占位图的典型特征：不同 URL 下载结果一模一样）。 */
    private fun findDuplicateDownload(file: File): String? {
        if (file.length() <= 0L || file.length() > 32L * 1024 * 1024) return null
        val parent = file.parentFile ?: return null
        val candidates = parent.listFiles { f -> f.isFile && f.name != file.name && f.length() == file.length() }
            ?: return null
        val digest = try {
            sha256Of(file)
        } catch (_: Exception) {
            return null
        } ?: return null
        for (candidate in candidates) {
            if (sha256Of(candidate) == digest) return candidate.name
        }
        return null
    }

    private fun sha256Of(file: File): String? = try {
        val digest = MessageDigest.getInstance("SHA-256")
        FileInputStream(file).use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        digest.digest().joinToString("") { "%02x".format(it) }
    } catch (_: Exception) {
        null
    }

    private fun formatDownloadProgress(event: DownloadEvent.Progress, startedAt: Long): String {
        val elapsedMs = (System.currentTimeMillis() - startedAt).coerceAtLeast(1L)
        val speed = event.downloadedBytes * 1000L / elapsedMs
        val downloaded = formatBytes(event.downloadedBytes)
        val total = event.totalBytes?.let(::formatBytes)
        val percent = event.totalBytes?.takeIf { it > 0L }?.let { event.downloadedBytes * 100 / it }
        return buildString {
            append("下载中：").append(downloaded)
            if (total != null) {
                append(" / ").append(total)
                percent?.let { append(" (").append(it.coerceIn(0L, 100L)).append("%)") }
            }
            append(" · ").append(formatBytes(speed)).append("/s")
        }
    }

    private fun formatBytes(bytes: Long): String {
        if (bytes < 1024L) return "$bytes B"
        val units = arrayOf("KiB", "MiB", "GiB", "TiB")
        var value = bytes.toDouble()
        var index = -1
        while (value >= 1024.0 && index < units.lastIndex) {
            value /= 1024.0
            index += 1
        }
        return if (value >= 100 || value % 1.0 == 0.0) "${value.toInt()} ${units[index]}" else "${"%.1f".format(Locale.US, value)} ${units[index]}"
    }

    companion object {
        const val DEFAULT_DOWNLOAD_ATTEMPTS = 3L
        const val MAX_DOWNLOAD_ATTEMPTS = 10L
        const val DEFAULT_DOWNLOAD_MAX_BYTES = 1024L * 1024L * 1024L
        const val MAX_DOWNLOAD_MAX_BYTES = 4L * 1024L * 1024L * 1024L
        const val PROGRESS_REPORT_INTERVAL_MS = 250L
        private val IMAGE_DOWNLOAD_EXTENSIONS = setOf("jpg", "jpeg", "png", "gif", "webp", "bmp")
    }
}
