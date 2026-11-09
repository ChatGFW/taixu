package top.wkbin.taixu.ui.chat

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import top.wkbin.taixu.core.common.files.BoundedStreamCopy
import top.wkbin.taixu.core.common.files.PayloadTooLargeException
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import top.wkbin.taixu.ui.miuix.minimumInteractiveComponentSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import top.wkbin.taixu.ui.miuix.RuntimeTheme
import top.wkbin.taixu.ui.miuix.Surface
import top.yukonga.miuix.kmp.basic.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import top.wkbin.taixu.feature.chat.R
import top.wkbin.taixu.ui.components.RuntimeIcon
import top.wkbin.taixu.ui.theme.semanticColors
import top.wkbin.taixu.ui.components.RuntimeIconName
import java.io.File
import java.util.Locale
import kotlinx.coroutines.CancellationException
import java.util.UUID
import android.graphics.Bitmap
import androidx.exifinterface.media.ExifInterface
import android.graphics.BitmapFactory
import android.util.Base64
import android.util.Log
import top.wkbin.taixu.runtime.AttachmentPaths
import java.io.ByteArrayOutputStream

/**
 * 用户选取的图片或附件元数据
 */
data class ChatAttachment(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val isImage: Boolean,
    val sizeBytes: Long,
    val uri: Uri,
    val hostFilePath: String? = null,
    val guestFilePath: String? = null,
    /** 有界正文旁路文件；仅把路径交给 Agent 按需 read。 */
    val extractedTextGuestPath: String? = null,
    val extractedTextHostPath: String? = null,
    val documentNotice: String? = null,
    val base64DataUrl: String? = null,
) {
    val localFilePath: String get() = guestFilePath ?: hostFilePath.orEmpty()
}

/** 附件处理结果：成功 / 超限（附实际大小与上限，用于大小范围提示）/ 其他失败 */
sealed interface AttachmentProcessResult {
    data class Success(val attachment: ChatAttachment) : AttachmentProcessResult
    data class TooLarge(val name: String, val sizeBytes: Long, val maxBytes: Long) : AttachmentProcessResult
    data class Failed(val name: String) : AttachmentProcessResult
}

/** 附件批量处理结果：成功项 + 需要 Toast 上抛的提示（含超限大小范围） */
data class AttachmentProcessOutcome(
    val items: List<ChatAttachment>,
    val notice: String?,
)

object AttachmentHelper {
    private const val TAG = "AttachmentHelper"

    suspend fun processUri(
        context: Context,
        uri: Uri,
        isImage: Boolean,
        sessionId: String? = null,
    ): AttachmentProcessResult {
        val contentResolver = context.contentResolver
        var name = if (isImage) "image_${System.currentTimeMillis()}.jpg" else "file_${System.currentTimeMillis()}"
        var size = 0L

        runCatching {
            contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (cursor.moveToFirst()) {
                    if (nameIndex != -1) name = cursor.getString(nameIndex) ?: name
                    if (sizeIndex != -1) size = cursor.getLong(sizeIndex)
                }
            }
        }

        // 预检：cursor 能拿到真实大小时，超限直接拦截，避免先复制再中止浪费 IO。
        val limit = BoundedStreamCopy.DEFAULT_MAX_ATTACHMENT_BYTES
        if (exceedsLimit(size, limit)) {
            return AttachmentProcessResult.TooLarge(name, size, limit)
        }

        // 落到发起会话的私有目录；PRoot 把该目录挂成 /attachments，guest 路径保持不变。
        val attachmentsDir = AttachmentPaths.session(context.filesDir, sessionId).apply { mkdirs() }
        val safeName = name.replace(Regex("[^a-zA-Z0-9._-]"), "_").takeLast(160).ifBlank { "attachment" }
        val targetFile = File(attachmentsDir, "${UUID.randomUUID()}_$safeName")

        return try {
            contentResolver.openInputStream(uri)?.use { input ->
                BoundedStreamCopy.copyToFile(
                    input = input,
                    targetFile = targetFile,
                    maxBytes = BoundedStreamCopy.DEFAULT_MAX_ATTACHMENT_BYTES,
                    policy = BoundedStreamCopy.OverflowPolicy.ABORT,
                )
            } ?: return AttachmentProcessResult.Failed(name)
            size = targetFile.length()

            val guestPath = "/attachments/${targetFile.name}"
            val sourceMimeType = runCatching { contentResolver.getType(uri) }.getOrNull()
            val base64 = if (isImage) {
                runCatching {
                    // 与 ImagePayloadCompressor 对齐：长边 1280、JPEG 80，避免相册原图把请求体顶到 413。
                    val boundsOptions = BitmapFactory.Options().apply {
                        inJustDecodeBounds = true
                    }
                    BitmapFactory.decodeFile(targetFile.absolutePath, boundsOptions)

                    var inSampleSize = 1
                    val maxDimension = 1280
                    if (boundsOptions.outHeight > maxDimension || boundsOptions.outWidth > maxDimension) {
                        val halfHeight = boundsOptions.outHeight / 2
                        val halfWidth = boundsOptions.outWidth / 2
                        while ((halfHeight / inSampleSize) >= maxDimension || (halfWidth / inSampleSize) >= maxDimension) {
                            inSampleSize *= 2
                        }
                    }

                    val decodeOptions = BitmapFactory.Options().apply {
                        this.inSampleSize = inSampleSize
                    }
                    val bitmap = BitmapFactory.decodeFile(targetFile.absolutePath, decodeOptions)
                    if (bitmap != null) {
                        val baos = ByteArrayOutputStream()
                        try {
                            // BitmapFactory 不读 EXIF：带方向元数据的相机照片须先归一，否则模型看到横竖颠倒的图
                            val oriented = applyExifOrientation(bitmap, targetFile.absolutePath)
                            oriented.compress(Bitmap.CompressFormat.JPEG, 80, baos)
                        } finally {
                            bitmap.recycle()
                        }
                        val bytes = baos.toByteArray()
                        val encoded = Base64.encodeToString(bytes, Base64.NO_WRAP)
                        "data:image/jpeg;base64,$encoded"
                    } else if (targetFile.length() <= 4L * 1024 * 1024) {
                        val bytes = targetFile.readBytes()
                        val encoded = Base64.encodeToString(bytes, Base64.NO_WRAP)
                        "data:${sourceMimeType?.takeIf { it.startsWith("image/") } ?: "image/jpeg"};base64,$encoded"
                    } else {
                        null
                    }
                }.getOrNull()
            } else null

            val document = if (isImage) PreparedAttachmentText()
                else AttachmentDocumentPreparation.prepare(context, targetFile, name, sourceMimeType)
            AttachmentProcessResult.Success(
                ChatAttachment(
                    name = name,
                    isImage = isImage,
                    sizeBytes = size,
                    uri = uri,
                    hostFilePath = targetFile.absolutePath,
                    guestFilePath = guestPath,
                    extractedTextGuestPath = document.file?.let { "/attachments/${it.name}" },
                    extractedTextHostPath = document.file?.absolutePath,
                    documentNotice = document.notice,
                    base64DataUrl = base64,
                ),
            )
        } catch (e: CancellationException) {
            targetFile.delete()
            File(targetFile.parentFile, targetFile.name + ".txt").delete()
            throw e
        } catch (e: PayloadTooLargeException) {
            // 流式拷贝中触发超限：以实际观测大小构造提示，保证"大小范围"信息完整
            Log.w(TAG, "附件超限 uri=$uri size=${e.actualBytesObserved} max=${e.maxBytes}")
            AttachmentProcessResult.TooLarge(name, e.actualBytesObserved, e.maxBytes)
        } catch (e: Exception) {
            // 失败不能静默：记录日志并由 ChatViewModel 统计后以 Toast 上抛给用户
            Log.w(TAG, "附件处理失败 uri=$uri", e)
            AttachmentProcessResult.Failed(name)
        }
    }

    /** 纯判定：sizeBytes 是否超过 maxBytes（0 表示未知大小，不拦截，交给拷贝后兜底） */
    internal fun exceedsLimit(sizeBytes: Long, maxBytes: Long = BoundedStreamCopy.DEFAULT_MAX_ATTACHMENT_BYTES): Boolean =
        sizeBytes > maxBytes

    /** 清理 host 侧已拷贝文件；移除附件时调用，避免 200MB 上限下撑满 filesDir */
    fun deleteHostFile(path: String?) {
        if (path == null) return
        runCatching { File(path).delete() }
            .onFailure { Log.w(TAG, "删除附件 host 文件失败 path=$path", it) }
    }

    /**
     * 粘贴超长文本 → txt 附件：与图片附件同目录
     * （linux-runtime/attachments，PRoot 挂 /attachments），guest 路径由调用方拼装。
     * 写入失败返回 null（调用方放行原文插入输入框）。
     */
    fun writePastedText(context: Context, text: String, sessionId: String? = null): File? = runCatching {
        val attachmentsDir = AttachmentPaths.session(context.filesDir, sessionId).apply { mkdirs() }
        File(attachmentsDir, "${System.currentTimeMillis()}_paste.txt").apply { writeText(text) }
    }.onFailure { Log.w(TAG, "粘贴文本落盘失败", it) }.getOrNull()

    /** 纯聚合：按 TooLarge/Failed 计数拼装提示；无异常时返回 null（保持单测不依赖 Context） */
    internal fun buildAttachmentNotice(
        tooLargeCount: Int,
        failedCount: Int,
        limitLabel: String,
        tooLargeMessage: (count: Int, limitLabel: String) -> String,
        failedMessage: (count: Int) -> String,
    ): String? {
        if (tooLargeCount <= 0 && failedCount <= 0) return null
        return buildList {
            if (tooLargeCount > 0) add(tooLargeMessage(tooLargeCount, limitLabel))
            if (failedCount > 0) add(failedMessage(failedCount))
        }.joinToString("；")
    }

    /** 批量处理附件并聚合超限/失败提示；通知文案在此拼装，保持 ViewModel 精简 */
    suspend fun processPicked(
        context: Context,
        uris: List<Uri>,
        isImage: Boolean,
        sessionId: String? = null,
    ): AttachmentProcessOutcome {
        val results = mutableListOf<AttachmentProcessResult>()
        try { uris.forEach { results += processUri(context, it, isImage, sessionId) } }
        catch (e: CancellationException) {
            results.filterIsInstance<AttachmentProcessResult.Success>().forEach { AttachmentDocumentPreparation.delete(it.attachment) }
            throw e
        }
        val items = results.mapNotNull { (it as? AttachmentProcessResult.Success)?.attachment }
        val tooLargeCount = results.count { it is AttachmentProcessResult.TooLarge }
        val failedCount = results.count { it is AttachmentProcessResult.Failed }
        val notice = buildAttachmentNotice(
            tooLargeCount = tooLargeCount,
            failedCount = failedCount,
            limitLabel = formatFileSize(results.filterIsInstance<AttachmentProcessResult.TooLarge>().firstOrNull()?.maxBytes ?: BoundedStreamCopy.DEFAULT_MAX_ATTACHMENT_BYTES),
            tooLargeMessage = { count, label -> context.getString(R.string.chat_attachment_too_large, count, label) },
            failedMessage = { count -> context.getString(R.string.chat_attachment_process_failed, count) },
        )
        return AttachmentProcessOutcome(items, (listOfNotNull(notice) + items.mapNotNull { it.documentNotice }).distinct().joinToString("；").ifEmpty { null })
    }

    fun formatFileSize(bytes: Long): String {
        return when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> "${bytes / 1024} KB"
            else -> String.format(Locale.US, "%.1f MB", bytes / (1024.0 * 1024.0))
        }
    }

    /**
     * EXIF 方向归一：按文件的方向元数据旋转位图（BitmapFactory 忽略 EXIF）。
     * 覆盖相机常见的旋转四态与镜像四态；无元数据或解析失败原样返回（不 recycle 入参）。
     */
    private fun applyExifOrientation(bitmap: Bitmap, path: String): Bitmap {
        val orientation = runCatching {
            ExifInterface(path).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
        }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)
        val matrix = android.graphics.Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> { matrix.postRotate(90f); matrix.postScale(-1f, 1f) }
            ExifInterface.ORIENTATION_TRANSVERSE -> { matrix.postRotate(270f); matrix.postScale(-1f, 1f) }
            else -> return bitmap
        }
        return runCatching {
            Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        }.getOrDefault(bitmap)
    }
}

/**
 * 待发送附件预览栏
 */
@Composable
fun AttachmentPreviewRow(
    attachments: List<ChatAttachment>,
    onRemove: (ChatAttachment) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (attachments.isEmpty()) return

    LazyRow(
        modifier = modifier.fillMaxWidth().padding(bottom = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(attachments, key = { it.id }) { item ->
            Surface(
                shape = RuntimeTheme.shapes.small,
                color = RuntimeTheme.colorScheme.surfaceContainerHigh,
                border = androidx.compose.foundation.BorderStroke(
                    0.8.dp,
                    RuntimeTheme.colorScheme.primary.copy(alpha = 0.35f),
                ),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .clip(CircleShape)
                            .background(
                                if (item.isImage) RuntimeTheme.colorScheme.primary.copy(alpha = 0.15f)
                                else semanticColors().success.copy(alpha = 0.15f),
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        RuntimeIcon(
                            name = if (item.isImage) RuntimeIconName.Image else RuntimeIconName.Attach,
                            modifier = Modifier.size(13.dp),
                            tint = if (item.isImage) RuntimeTheme.colorScheme.primary else semanticColors().success,
                        )
                    }

                    Column(modifier = Modifier.widthIn(max = 180.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = item.name,
                            style = RuntimeTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                            color = RuntimeTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = listOfNotNull(AttachmentHelper.formatFileSize(item.sizeBytes), item.documentNotice).joinToString(" · "),
                            maxLines = 2, overflow = TextOverflow.Ellipsis,
                            style = RuntimeTheme.typography.labelSmall.copy(fontSize = 11.sp),
                            color = RuntimeTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    val removeLabel = stringResource(R.string.chat_remove_attachment)
                    Box(
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .clickable { onRemove(item) }
                            .semantics { contentDescription = removeLabel },
                        contentAlignment = Alignment.Center,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(18.dp)
                                .clip(CircleShape)
                                .background(RuntimeTheme.colorScheme.surfaceContainerHighest),
                            contentAlignment = Alignment.Center,
                        ) {
                            RuntimeIcon(
                                name = RuntimeIconName.Close,
                                modifier = Modifier.size(10.dp),
                                tint = RuntimeTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}
