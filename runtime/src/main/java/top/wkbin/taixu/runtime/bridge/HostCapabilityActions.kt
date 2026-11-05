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
import top.wkbin.taixu.runtime.gui.GuiKey
import top.wkbin.taixu.runtime.gui.HostGuiController
import top.wkbin.taixu.runtime.privilege.PrivilegeManager
import top.wkbin.taixu.runtime.privilege.ShellExecResult
import java.io.File

/**
 * 沙箱可调用的宿主能力。
 *
 * 分两级：
 * - 低危、用户可见可撤销：打开链接、写剪贴板、发通知，默认签发；
 * - 会改变系统状态或代用户操作界面：改设置、控制应用、模拟屏幕，
 *   只随全权授权签发，且每次调用仍要过命令级审批。
 */
internal class HostCapabilityActions(
    private val context: Context,
    private val privilegeManager: PrivilegeManager,
    private val gui: HostGuiController? = null,
    private val workspaceDir: File? = null,
) {

    sealed interface Outcome {
        data class Ok(val output: String = "") : Outcome
        data class Rejected(val failure: HostFailure, val message: String) : Outcome
    }

    fun open(rawUri: String): Outcome {
        val normalized = parseAllowedUri(rawUri) ?: return rejected(
            HostFailure.USAGE,
            "只允许 http/https/mailto/tel 链接：${rawUri.take(120)}",
        )
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(normalized))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return try {
            context.startActivity(intent)
            Outcome.Ok()
        } catch (missing: ActivityNotFoundException) {
            rejected(HostFailure.NOT_FOUND, "没有应用可以打开该链接")
        } catch (denied: SecurityException) {
            rejected(HostFailure.PERMISSION_DENIED, denied.message ?: "宿主拒绝打开该链接")
        }
    }

    fun writeClipboard(text: String): Outcome {
        if (text.length !in 1..MAX_CLIPBOARD_CHARS || '\u0000' in text) {
            return rejected(HostFailure.USAGE, "剪贴板文本需为 1..$MAX_CLIPBOARD_CHARS 字符且不含 NUL")
        }
        val clipboard = context.getSystemService(ClipboardManager::class.java)
            ?: return rejected(HostFailure.UNAVAILABLE, "剪贴板服务不可用")
        val clip = ClipData.newPlainText("TaiXu Agent", text)
        clip.description.extras = PersistableBundle().apply {
            putBoolean("android.content.extra.IS_SENSITIVE", true)
        }
        return try {
            clipboard.setPrimaryClip(clip)
            Outcome.Ok()
        } catch (denied: SecurityException) {
            rejected(HostFailure.PERMISSION_DENIED, denied.message ?: "宿主拒绝写入剪贴板")
        }
    }

    /** 单一通知位：重复调用覆盖上一条，避免长任务刷出一串待清理的通知。 */
    fun notify(title: String, body: String): Outcome {
        val (safeTitle, safeBody) = sanitizeNotification(title, body) ?: return rejected(
            HostFailure.USAGE,
            "通知标题需为 1..$MAX_NOTIFICATION_TITLE 字符，正文不超过 $MAX_NOTIFICATION_BODY 字符且不含 NUL",
        )
        val manager = context.getSystemService(NotificationManager::class.java)
            ?: return rejected(HostFailure.UNAVAILABLE, "通知服务不可用")
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) {
            return rejected(HostFailure.PERMISSION_DENIED, "应用通知已关闭：请在系统设置中允许太墟发送通知")
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
            Outcome.Ok()
        } catch (denied: SecurityException) {
            rejected(HostFailure.PERMISSION_DENIED, denied.message ?: "宿主拒绝发送通知")
        }
    }

    // ============================ 设置 ============================

    suspend fun settingsGet(namespace: String, key: String): Outcome {
        val ns = parseNamespace(namespace) ?: return rejected(HostFailure.USAGE, USAGE_NAMESPACE)
        val safeKey = sanitizeKey(key) ?: return rejected(HostFailure.USAGE, USAGE_SETTING_KEY)
        requireShell()?.let { return it }
        val result = privilegeManager.executeShellCommand("settings get $ns $safeKey")
        if (!result.success) return shellFailure(result)
        return Outcome.Ok(result.stdout.trim())
    }

    suspend fun settingsPut(namespace: String, key: String, value: String): Outcome {
        val ns = parseNamespace(namespace) ?: return rejected(HostFailure.USAGE, USAGE_NAMESPACE)
        val safeKey = sanitizeKey(key) ?: return rejected(HostFailure.USAGE, USAGE_SETTING_KEY)
        if (value.length > MAX_SETTING_VALUE_CHARS || '\u0000' in value) {
            return rejected(HostFailure.USAGE, "设置值不超过 $MAX_SETTING_VALUE_CHARS 字符且不含 NUL")
        }
        // system 命名空间优先走 ContentResolver（需 WRITE_SETTINGS，免 shell 转义）；
        // secure/global 第三方应用拿不到写权限，只能走特权 shell。
        if (ns == "system" && privilegeManager.writeSystemSetting(safeKey, value)) {
            return Outcome.Ok("settings put system $safeKey 已生效")
        }
        requireShell()?.let { return it }
        val result = privilegeManager.executeShellCommand("settings put $ns $safeKey ${shellQuote(value)}")
        if (!result.success) return shellFailure(result)
        return Outcome.Ok("settings put $ns $safeKey 已提交")
    }

    // ============================ 应用 ============================

    suspend fun appStart(packageName: String): Outcome {
        val pkg = sanitizePackage(packageName) ?: return rejected(HostFailure.USAGE, USAGE_PACKAGE)
        val controller = gui ?: return rejected(HostFailure.UNAVAILABLE, "宿主 GUI 控制器不可用")
        return controller.launchApp(pkg).fold(
            onSuccess = { Outcome.Ok(it) },
            onFailure = { rejected(HostFailure.NOT_FOUND, it.message ?: "启动应用失败：$pkg") },
        )
    }

    suspend fun appStop(packageName: String): Outcome {
        val pkg = sanitizePackage(packageName) ?: return rejected(HostFailure.USAGE, USAGE_PACKAGE)
        val controller = gui ?: return rejected(HostFailure.UNAVAILABLE, "宿主 GUI 控制器不可用")
        return controller.forceStopApp(pkg).fold(
            onSuccess = { Outcome.Ok(it) },
            onFailure = { rejected(HostFailure.INTERNAL, it.message ?: "停止应用失败：$pkg") },
        )
    }

    suspend fun appFreeze(packageName: String, freeze: Boolean): Outcome {
        val pkg = sanitizePackage(packageName) ?: return rejected(HostFailure.USAGE, USAGE_PACKAGE)
        requireShell()?.let { return it }
        val command = if (freeze) "pm disable-user --user 0 $pkg" else "pm enable $pkg"
        val result = privilegeManager.executeShellCommand(command)
        if (!result.success) return shellFailure(result)
        return Outcome.Ok(if (freeze) "$pkg 已冻结" else "$pkg 已恢复")
    }

    // ============================ 屏幕 ============================

    suspend fun screenObserve(): Outcome {
        val controller = gui ?: return rejected(HostFailure.UNAVAILABLE, "宿主 GUI 控制器不可用")
        return controller.observeScreen(onlyInteractive = true).fold(
            onSuccess = { Outcome.Ok(it.toAgentSummary()) },
            onFailure = { rejected(HostFailure.UNAVAILABLE, it.message ?: "屏幕感知失败：请确认无障碍或 Shizuku 通道可用") },
        )
    }

    suspend fun screenTap(x: Int, y: Int): Outcome {
        val controller = gui ?: return rejected(HostFailure.UNAVAILABLE, "宿主 GUI 控制器不可用")
        if (!inScreenBounds(x) || !inScreenBounds(y)) return rejected(HostFailure.USAGE, USAGE_COORDINATES)
        return controller.click(x, y).fold(
            onSuccess = { Outcome.Ok(it) },
            onFailure = { rejected(HostFailure.INTERNAL, it.message ?: "点击失败") },
        )
    }

    suspend fun screenSwipe(x1: Int, y1: Int, x2: Int, y2: Int, durationMs: Long): Outcome {
        val controller = gui ?: return rejected(HostFailure.UNAVAILABLE, "宿主 GUI 控制器不可用")
        if (listOf(x1, y1, x2, y2).any { !inScreenBounds(it) }) {
            return rejected(HostFailure.USAGE, USAGE_COORDINATES)
        }
        if (durationMs !in MIN_SWIPE_MS..MAX_SWIPE_MS) {
            return rejected(HostFailure.USAGE, "滑动时长需在 $MIN_SWIPE_MS..$MAX_SWIPE_MS 毫秒")
        }
        return controller.swipe(x1, y1, x2, y2, durationMs).fold(
            onSuccess = { Outcome.Ok(it) },
            onFailure = { rejected(HostFailure.INTERNAL, it.message ?: "滑动失败") },
        )
    }

    /**
     * 截图只落在工作区的 screenshots/ 下并回传沙箱可见路径：
     * 不给 agent 指定任意宿主路径的能力，避免把截图写成任意文件写入原语。
     */
    suspend fun screenShot(name: String?): Outcome {
        val controller = gui ?: return rejected(HostFailure.UNAVAILABLE, "宿主 GUI 控制器不可用")
        val root = workspaceDir ?: return rejected(HostFailure.UNAVAILABLE, "工作区不可用")
        requireShell()?.let { return it }
        val safeName = sanitizeShotName(name) ?: return rejected(
            HostFailure.USAGE,
            "截图名需为 1..64 位字母、数字、点、下划线或连字符，可省略 .png 后缀",
        )
        val dir = File(root, "screenshots").apply { mkdirs() }
        return controller.captureScreenshot(File(dir, safeName).absolutePath).fold(
            onSuccess = { Outcome.Ok("/workspace/screenshots/$safeName") },
            onFailure = { rejected(HostFailure.INTERNAL, it.message ?: "截图失败") },
        )
    }

    suspend fun screenText(text: String): Outcome {
        val controller = gui ?: return rejected(HostFailure.UNAVAILABLE, "宿主 GUI 控制器不可用")
        if (text.isEmpty() || text.length > MAX_SCREEN_TEXT_CHARS) {
            return rejected(HostFailure.USAGE, "输入文本需为 1..$MAX_SCREEN_TEXT_CHARS 字符")
        }
        return controller.inputText(text).fold(
            onSuccess = { Outcome.Ok(it) },
            onFailure = { rejected(HostFailure.INTERNAL, it.message ?: "输入失败") },
        )
    }

    suspend fun screenKey(keyName: String): Outcome {
        val controller = gui ?: return rejected(HostFailure.UNAVAILABLE, "宿主 GUI 控制器不可用")
        if (GuiKey.parse(keyName) == null) {
            return rejected(HostFailure.USAGE, "未知按键：$keyName（支持 back/home/recents/enter/delete/paste/power）")
        }
        return controller.sendKey(keyName).fold(
            onSuccess = { Outcome.Ok(it) },
            onFailure = { rejected(HostFailure.INTERNAL, it.message ?: "按键失败") },
        )
    }

    // ============================ 内部 ============================

    private fun rejected(failure: HostFailure, message: String) = Outcome.Rejected(failure, message)

    private suspend fun requireShell(): Outcome.Rejected? {
        val info = privilegeManager.getPrivilegeInfo()
        if (!info.modeActive || !(info.shizukuAvailable || info.rootAvailable)) {
            return rejected(HostFailure.UNAVAILABLE, "需要 Shizuku 或 Root 通道：请先在设置中授权并切换模式")
        }
        return null
    }

    private fun shellFailure(result: ShellExecResult): Outcome.Rejected = rejected(
        HostFailure.ofShellExit(result.exitCode, result.stderr) ?: HostFailure.INTERNAL,
        result.stderr.trim().ifEmpty { "宿主命令失败（exit ${result.exitCode}）" },
    )

    companion object {
        const val MAX_CLIPBOARD_CHARS = 16_384
        const val MAX_NOTIFICATION_TITLE = 80
        const val MAX_NOTIFICATION_BODY = 500
        const val MAX_SCREEN_TEXT_CHARS = 2_000
        const val MAX_SETTING_VALUE_CHARS = 4_096
        const val MIN_SWIPE_MS = 50L
        const val MAX_SWIPE_MS = 3_000L
        private const val NOTIFICATION_CHANNEL_ID = "taixu_agent_capability"
        private const val NOTIFICATION_ID = 8898
        private const val MAX_URI_CHARS = 2_048
        private const val MAX_SCREEN_COORDINATE = 10_000

        private const val USAGE_NAMESPACE = "命名空间仅支持 system / secure / global"
        private const val USAGE_SETTING_KEY = "设置键需为 1..160 位的字母、数字、点、下划线或连字符"
        private const val USAGE_PACKAGE = "包名格式不合法（形如 com.example.app）"
        private const val USAGE_COORDINATES = "坐标需在 0..$MAX_SCREEN_COORDINATE"
        private val PACKAGE_NAME = Regex("^[A-Za-z0-9_]+(?:\\.[A-Za-z0-9_]+)+$")
        private val SETTING_KEY = Regex("^[A-Za-z0-9._-]{1,160}$")
        private val SHOT_NAME = Regex("^[A-Za-z0-9][A-Za-z0-9._-]{0,63}$")
        private val NAMESPACES = setOf("system", "secure", "global")

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

        /** 纯函数，便于单测：空白标题与超长正文直接拒绝，不进入通知栈。 */
        fun sanitizeNotification(title: String, body: String): Pair<String, String>? {
            val trimmed = title.trim()
            if (trimmed.isEmpty() || trimmed.length > MAX_NOTIFICATION_TITLE) return null
            if (body.length > MAX_NOTIFICATION_BODY) return null
            if ('\u0000' in trimmed || '\u0000' in body) return null
            return trimmed to body.trim()
        }

        /** 纯函数：命名空间与键的先验校验，避免把任意文本拼进 shell 命令。 */
        fun parseNamespace(raw: String): String? = raw.trim().lowercase().takeIf { it in NAMESPACES }

        fun sanitizeKey(raw: String): String? = raw.trim().takeIf { SETTING_KEY.matches(it) }

        fun sanitizePackage(raw: String): String? = raw.trim().takeIf { PACKAGE_NAME.matches(it) }

        fun inScreenBounds(value: Int): Boolean = value in 0..MAX_SCREEN_COORDINATE

        /** 纯函数：截图名只能是单段文件名，缺省补时间戳与 .png 后缀。 */
        fun sanitizeShotName(raw: String?, now: Long = System.currentTimeMillis()): String? {
            val candidate = raw?.trim().orEmpty().ifEmpty { "shot-$now" }
            val stem = if (candidate.endsWith(".png", ignoreCase = true)) candidate.dropLast(4) else candidate
            if (stem.isEmpty() || stem.length > 64) return null
            if (!SHOT_NAME.matches(stem)) return null
            return "$stem.png"
        }

        /** 单引号包裹并按 POSIX 规则转义，供 secure/global 的 shell 写入使用。 */
        fun shellQuote(value: String): String = "'" + value.replace("'", "'\\''") + "'"
    }
}
