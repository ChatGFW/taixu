package top.wkbin.taixu.ui.components

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import android.util.Base64
import androidx.compose.runtime.staticCompositionLocalOf
import coil3.request.ImageRequest
import coil3.request.crossfade
import java.io.ByteArrayInputStream
import java.net.HttpURLConnection
import java.net.URL
import java.io.File

/**
 * 沙箱绝对路径前缀（如 "workspace" 对应 /workspace）到宿主真实目录的映射。
 * 模型回复里的本地媒体路径（/workspace/xxx.jpg）是 PRoot 沙箱内语义，
 * Android 侧必须翻译成应用私有目录下的真实文件才能被 Coil 加载。
 */
val LocalSandboxHostRoots = staticCompositionLocalOf<Map<String, File>> { emptyMap() }

/** 把模型引用的本地路径解析为 Coil 可加载的 data model；无法映射时原样返回。 */
internal fun resolveMediaSource(source: String, hostRoots: Map<String, File>): Any {
    // 模型可能输出 file:///workspace/...，统一剥掉 file:// 前缀再按沙箱绝对路径解析
    val path = if (source.startsWith("file://", ignoreCase = true)) source.substring("file://".length) else source
    if (path.startsWith("//")) return source
    if (!path.startsWith("/")) {
        // ./xxx.jpg 相对路径：尽力映射到工作区根（下载工具的 /workspace/ 前缀路径落盘于此）
        if (!path.startsWith("./")) return source
        val workspaceRoot = hostRoots["workspace"] ?: return source
        val relative = path.removePrefix("./").substringBefore('?').substringBefore('#')
        if (relative.isBlank() || relative.split('/').any { it.isEmpty() || it == "." || it == ".." }) return source
        return File(workspaceRoot, relative)
    }
    val clean = path.replace('\\', '/').substringBefore('?').substringBefore('#').trimEnd('/')
    val segments = clean.trimStart('/').split('/').filter { it.isNotEmpty() && it != "." }
    if (segments.isEmpty() || segments.any { it == ".." }) return source
    val root = hostRoots[segments.first()] ?: return source
    return if (segments.size == 1) root else File(root, segments.drop(1).joinToString("/"))
}

internal fun imageRequest(context: Context, dataModel: Any): ImageRequest =
    ImageRequest.Builder(context).data(dataModel).crossfade(true).build()

internal fun imageMimeType(source: String): String {
    if (source.startsWith("data:", ignoreCase = true)) {
        return source.substringAfter("data:").substringBefore(';').takeIf { it.startsWith("image/") } ?: "image/png"
    }
    val clean = source.substringBefore('?').substringBefore('#').lowercase()
    return when {
        clean.endsWith(".jpg") || clean.endsWith(".jpeg") -> "image/jpeg"
        clean.endsWith(".webp") -> "image/webp"
        clean.endsWith(".gif") -> "image/gif"
        clean.endsWith(".avif") -> "image/avif"
        else -> "image/png"
    }
}

private fun imageExtension(mimeType: String): String = when (mimeType.lowercase()) {
    "image/jpeg" -> "jpg"
    "image/webp" -> "webp"
    "image/gif" -> "gif"
    "image/avif" -> "avif"
    else -> "png"
}

/** Saves into Pictures/TaiXu so the generated image appears in the system gallery immediately. */
internal fun saveImageToGallery(context: Context, mediaModel: Any, mimeType: String): Boolean {
    val resolver = context.contentResolver
    val values = ContentValues().apply {
        put(MediaStore.Images.Media.DISPLAY_NAME, "taixu-${System.currentTimeMillis()}.${imageExtension(mimeType)}")
        put(MediaStore.Images.Media.MIME_TYPE, mimeType)
        put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/TaiXu")
        put(MediaStore.Images.Media.IS_PENDING, 1)
    }
    val target = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return false
    return runCatching {
        copyImageSource(context, mediaModel, target)
        resolver.update(
            target,
            ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) },
            null,
            null,
        )
        true
    }.getOrElse {
        resolver.delete(target, null, null)
        false
    }
}

private fun copyImageSource(context: Context, source: Any, target: Uri) {
    context.contentResolver.openOutputStream(target)?.use { output ->
        when (source) {
            is File -> source.inputStream().use { it.copyTo(output) }
            is String -> when {
                source.startsWith("data:", ignoreCase = true) -> {
                    val payload = source.substringAfter(',', missingDelimiterValue = "")
                    require(payload.isNotEmpty()) { "Invalid image data URL" }
                    ByteArrayInputStream(Base64.decode(payload, Base64.DEFAULT)).use { it.copyTo(output) }
                }
                source.startsWith("file://", ignoreCase = true) ->
                    File(source.removePrefix("file://")).inputStream().use { it.copyTo(output) }
                source.startsWith("http://", true) || source.startsWith("https://", true) -> {
                    val connection = URL(source).openConnection() as HttpURLConnection
                    connection.connectTimeout = 15_000
                    connection.readTimeout = 30_000
                    connection.instanceFollowRedirects = true
                    try {
                        check(connection.responseCode in 200..299) { "HTTP ${connection.responseCode}" }
                        connection.inputStream.use { it.copyTo(output) }
                    } finally {
                        connection.disconnect()
                    }
                }
                else -> File(source).inputStream().use { it.copyTo(output) }
            }
        }
    } ?: error("Cannot open destination")
}
