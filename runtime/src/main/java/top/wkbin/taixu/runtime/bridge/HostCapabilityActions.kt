package top.wkbin.taixu.runtime.bridge

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PersistableBundle
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

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

    /** 单一通知位：重复调用覆盖上一条，避免长任务刷出一串待清理的通知。 */
    fun notify(title: String, body: String): Outcome {
        val (safeTitle, safeBody) = sanitizeNotification(title, body) ?: return Outcome.Rejected(
            HostFailure.USAGE,
            "通知标题需为 1..$MAX_NOTIFICATION_TITLE 字符，正文不超过 $MAX_NOTIFICATION_BODY 字符且不含 NUL",
        )
        val manager = context.getSystemService(NotificationManager::class.java)
            ?: return Outcome.Rejected(HostFailure.UNAVAILABLE, "通知服务不可用")
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) {
            return Outcome.Rejected(HostFailure.PERMISSION_DENIED, "应用通知已关闭：请在系统设置中允许太墟发送通知")
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(NOTIFICATION_CHANNEL_ID, "太墟 Agent 通知", NotificationManager.IMPORTANCE_DEFAULT).apply {
                    description = "Agent 经沙箱能力命令发出的提醒"
                },
            )
        }
        val notification = NotificationCompat.Builder(context, NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setContentTitle(safeTitle)
            .setContentText(safeBody)
            .setStyle(NotificationCompat.BigTextStyle().bigText(safeBody))
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        return try {
            manager.notify(NOTIFICATION_ID, notification)
            Outcome.Ok
        } catch (denied: SecurityException) {
            Outcome.Rejected(HostFailure.PERMISSION_DENIED, denied.message ?: "宿主拒绝发送通知")
        }
    }

    companion object {
        const val MAX_CLIPBOARD_CHARS = 16_384
        const val MAX_NOTIFICATION_TITLE = 80
        const val MAX_NOTIFICATION_BODY = 500
        private const val NOTIFICATION_CHANNEL_ID = "taixu_agent_capability"
        private const val NOTIFICATION_ID = 8898
        private const val MAX_URI_CHARS = 2_048

        /** 纯函数，便于单测：空白标题与超长正文直接拒绝，不进入通知栈。 */
        fun sanitizeNotification(title: String, body: String): Pair<String, String>? {
            val trimmed = title.trim()
            if (trimmed.isEmpty() || trimmed.length > MAX_NOTIFICATION_TITLE) return null
            if (body.length > MAX_NOTIFICATION_BODY) return null
            if ('\u0000' in trimmed || '\u0000' in body) return null
            return trimmed to body.trim()
        }

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
