package top.wkbin.taixu.ui.chat

import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import top.wkbin.taixu.core.common.files.BinaryFileSniffer
import top.wkbin.taixu.core.common.files.FileDigest
import top.wkbin.taixu.feature.chat.R

internal sealed interface DeliveryTextPreview {
    data class Text(val content: String) : DeliveryTextPreview
    data object TooLarge : DeliveryTextPreview
    data object Binary : DeliveryTextPreview
    data object Unreadable : DeliveryTextPreview
}

private const val MAX_PREVIEW_BYTES = 1024 * 1024

internal suspend fun readDeliveryText(file: File): DeliveryTextPreview = withContext(Dispatchers.IO) {
    try {
        file.inputStream().use { input ->
            val bytes = ByteArray(MAX_PREVIEW_BYTES + 1)
            var size = 0
            while (size < bytes.size) {
                val count = input.read(bytes, size, bytes.size - size)
                if (count < 0) break
                size += count
            }
            when {
                size > MAX_PREVIEW_BYTES -> DeliveryTextPreview.TooLarge
                BinaryFileSniffer.looksBinary(bytes, size) -> DeliveryTextPreview.Binary
                else -> DeliveryTextPreview.Text(String(bytes, 0, size, Charsets.UTF_8))
            }
        }
    } catch (_: Exception) { DeliveryTextPreview.Unreadable }
}

internal suspend fun verifyDeliveryFile(file: File?, sha256: String): DeliveryIntegrity = withContext(Dispatchers.IO) {
    when {
        file == null -> DeliveryIntegrity.UNRESOLVED
        !file.isFile -> DeliveryIntegrity.MISSING
        else -> {
            val digest = FileDigest.sha256(file)
            when {
                digest == null -> DeliveryIntegrity.MISSING
                digest.equals(sha256, ignoreCase = true) -> DeliveryIntegrity.MATCHED
                else -> DeliveryIntegrity.CHANGED
            }
        }
    }
}

internal fun integrityLabel(integrity: DeliveryIntegrity): Int? = when (integrity) {
    DeliveryIntegrity.MISSING -> R.string.delivery_artifact_missing
    DeliveryIntegrity.CHANGED -> R.string.delivery_artifact_changed
    DeliveryIntegrity.PENDING, DeliveryIntegrity.MATCHED, DeliveryIntegrity.UNRESOLVED -> null
}
