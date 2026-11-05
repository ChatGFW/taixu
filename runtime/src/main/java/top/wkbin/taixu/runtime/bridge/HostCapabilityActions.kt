package top.wkbin.taixu.runtime.bridge

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PersistableBundle

/**
 * 沙箱可调用的宿主能力。
 *
 * 只收"用户可见、后果可撤销"的动作：打开链接、写入剪贴板。读取剪贴板、发通知、
 * 改设置等更强或更敏感的能力不在这里——它们要么已有插件网关路径，要么需要单独的
 * 授权设计，不能借这条通道顺带开放。
 */
internal class HostCapabilityActions(private val context: Context) {

    sealed interface Outcome {
        data object Ok : Outcome
        data class Rejected(val failure: HostFailure, val message: String) : Outcome
    }

    fun open(rawUri: String): Outcome {
        val normalized = parseAllowedUri(rawUri) ?: return Outcome.Rejected(
            HostFailure.USAGE,
            "只允许 http/https/mailto/tel 链接：${rawUri.take(120)}",
        )
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(normalized))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return try {
            context.startActivity(intent)
            Outcome.Ok
        } catch (missing: ActivityNotFoundException) {
            Outcome.Rejected(HostFailure.NOT_FOUND, "没有应用可以打开该链接")
        } catch (denied: SecurityException) {
            Outcome.Rejected(HostFailure.PERMISSION_DENIED, denied.message ?: "宿主拒绝打开该链接")
        }
    }

    fun writeClipboard(text: String): Outcome {
        if (text.length !in 1..MAX_CLIPBOARD_CHARS || '\u0000' in text) {
            return Outcome.Rejected(
                HostFailure.USAGE,
                "剪贴板文本需为 1..$MAX_CLIPBOARD_CHARS 字符且不含 NUL",
            )
        }
        val clipboard = context.getSystemService(ClipboardManager::class.java)
            ?: return Outcome.Rejected(HostFailure.UNAVAILABLE, "剪贴板服务不可用")
        val clip = ClipData.newPlainText("TaiXu Agent", text)
        clip.description.extras = PersistableBundle().apply {
            putBoolean("android.content.extra.IS_SENSITIVE", true)
        }
        return try {
            clipboard.setPrimaryClip(clip)
            Outcome.Ok
        } catch (denied: SecurityException) {
            Outcome.Rejected(HostFailure.PERMISSION_DENIED, denied.message ?: "宿主拒绝写入剪贴板")
        }
    }

    companion object {
        const val MAX_CLIPBOARD_CHARS = 16_384
        private const val MAX_URI_CHARS = 2_048

        /** 只放行用户可预期、不会隐式触发特权页面的 scheme；`intent://` 等一律拒绝。 */
        private val ALLOWED_SCHEMES = setOf("http", "https", "mailto", "tel")

        /** 纯函数，便于单测：拒绝控制字符、超长与非白名单 scheme。 */
        fun parseAllowedUri(raw: String): String? {
            val trimmed = raw.trim()
            if (trimmed.isEmpty() || trimmed.length > MAX_URI_CHARS) return null
            if (trimmed.any { it < ' ' || it == '\u007f' }) return null
            val parsed = runCatching { java.net.URI(trimmed) }.getOrNull() ?: return null
            return trimmed.takeIf { parsed.scheme?.lowercase() in ALLOWED_SCHEMES }
        }
    }
}
