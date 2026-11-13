package top.wkbin.taixu.runtime.virtualdisplay

import android.content.Context
import android.hardware.display.DisplayManager
import android.provider.Settings
import android.util.DisplayMetrics
import android.util.Log
import android.view.WindowManager
import com.ai.assistance.showerclient.ShellIdentity
import com.ai.assistance.showerclient.ShellRunner
import com.ai.assistance.showerclient.ShowerBinderRegistry
import com.ai.assistance.showerclient.ShowerController
import com.ai.assistance.showerclient.ShowerEnvironment
import com.ai.assistance.showerclient.ShowerLogSink
import com.ai.assistance.showerclient.ShowerServerManager
import top.wkbin.taixu.core.common.logging.AppLogger
import top.wkbin.taixu.runtime.apps.installedAppNames
import top.wkbin.taixu.runtime.privilege.PrivilegeManager
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * 虚拟屏能力门面（多会话版）。
 *
 * 职责：
 * 1. 构造时把 [TaixuShowerShellRunner] 装配进 [ShowerEnvironment]——这是 showerclient
 *    "宿主无关"设计的唯一注入点，Koin 单例首次创建即完成装配；
 * 2. 按 [sessionId] 维护相互独立的虚拟屏会话（每会话一块虚拟屏，可多 Agent 并行）；
 * 3. 对上暴露「确保虚拟屏 → 启动应用 → 截图 / 输入 → 关闭」的完整闭环，
 *    GUI 原语执行见 [VirtualScreenToolkit]，Agent 工具入口见 harness ToolExecutor 的
 *    virtual_screen_* 分支。
 *
 * 前置条件：需要 Shizuku（ADB 级）或 Root 特权；PRoot 模式下所有 shell 命令
 * 会在 [PrivilegeManager] 处失败，本门面相应返回 null/false。
 */
class VirtualDisplayCoordinator(
    private val context: Context,
    privilegeManager: PrivilegeManager,
    private val logger: AppLogger,
) {

    private val sessions = ConcurrentHashMap<String, ShowerController>()
    private val creationFailures = ConcurrentHashMap<String, String>()
    val phoneTasks = PhoneTaskRegistry()
    private val lifecycleMutex = Mutex()
    private val phoneTaskUi = PhoneTaskPublisher(context.applicationContext, phoneTasks)

    init {
        ShowerEnvironment.shellRunner = TaixuShowerShellRunner(privilegeManager, logger)
        // 把 showerclient 的日志镜像进 runtime.log，否则虚拟屏失败的**真实原因**
        // （server 落盘日志 / 工作目录不可写 / Binder 未回传）只存在于 logcat，
        // 宿主侧只剩一句笼统的「server 启动失败」。
        // DEBUG/INFO 仍只走 logcat，避免 hasAliveService 轮询把 runtime.log 刷爆。
        ShowerEnvironment.logSink = ShowerLogSink { priority, tag, message, throwable ->
            val text = "[Shower][$tag] $message"
            when {
                priority >= Log.ERROR -> logger.e(text, throwable)
                priority >= Log.WARN -> logger.w(text, throwable)
                else -> Unit
            }
        }
        phoneTaskUi.installBridge()
    }

    /** shower-server Binder 是否已就绪（收到 SHOWER_BINDER_READY 广播且未死亡） */
    val isServerReady: Boolean
        get() = ShowerBinderRegistry.hasAliveService()

    /** 当前活跃会话 ID 集合 */
    val activeSessionIds: Set<String>
        get() = sessions.keys.toSet()

    /** 取指定会话的 controller（不存在则创建，仅持有本地状态，不触发 server 交互） */
    fun controller(sessionId: String): ShowerController =
        sessions.getOrPut(sessionId) { ShowerController() }

    /** 指定会话当前虚拟屏 displayId（未创建为 null） */
    fun getDisplayId(sessionId: String): Int? = sessions[sessionId]?.getDisplayId()

    fun creationFailure(sessionId: String): String =
        "虚拟屏创建失败（session=$sessionId）：" +
            (creationFailures[sessionId] ?: "请查看 runtime.log 中的 [Shower] 日志")

    /** 指定会话的视频流尺寸（未创建或未收到流为 null） */
    fun getVideoSize(sessionId: String): Pair<Int, Int>? = sessions[sessionId]?.getVideoSize()

    /**
     * 确保 shower-server 已启动，并为指定会话创建/复用一块与主屏同尺寸同密度的虚拟屏。
     *
     * 预览直挂 Surface（[ShowerController.setDisplaySurface]），不建 H.264 管线。
     * [bitrateKbps] 仅为旧调用方保留，服务端已忽略。
     *
     * @return 虚拟屏 displayId；server 启动失败或建屏失败返回 null
     */
    suspend fun ensureVirtualDisplay(
        sessionId: String = DEFAULT_SESSION_ID,
        bitrateKbps: Int? = null,
    ): Int? = lifecycleMutex.withLock {
        ensureDisplayLocked(sessionId, bitrateKbps)
    }

    private suspend fun ensureDisplayLocked(sessionId: String, bitrateKbps: Int?): Int? {
        // 手机任务全局单飞期间禁止另建 VD，避免双屏抢 Surface 黑屏。
        val owner = phoneTasks.activeSession()
        if (owner != null && owner != sessionId) {
            creationFailures[sessionId] =
                "手机任务占用中（session=$owner），全局同时只能一块虚拟屏"
            logger.w(creationFailure(sessionId))
            return null
        }
        creationFailures.remove(sessionId)
        val controller = controller(sessionId)
        if (!ShowerServerManager.ensureServerStarted(context) { creationFailures[sessionId] = it }) {
            logger.w(creationFailure(sessionId))
            return null
        }
        // Application 的 displayMetrics 在分屏/折叠屏上常常不是物理屏尺寸。
        val metrics = realDisplayMetrics()
        val ok = controller.ensureDisplay(
            context = context,
            width = metrics.widthPixels,
            height = metrics.heightPixels,
            dpi = metrics.densityDpi,
            bitrateKbps = bitrateKbps,
        )
        if (!ok) {
            logger.w("虚拟屏创建失败（session=$sessionId，server 已启动）")
            return null
        }
        // 有活跃虚拟屏时拉起 SPECIAL_USE FGS 保活后台显示。
        TwinDisplayKeepAlive.start(context)
        creationFailures.remove(sessionId)
        return controller.getDisplayId()
    }

    /** 已安装应用的包名和应用名，供手机操作模型把「微信」解析成包名。 */
    fun installedApps(): List<Pair<String, String>> = installedAppNames(context)

    /** 在指定会话的虚拟屏上以 shell 身份启动第三方应用（需 Shizuku/Root；普通 App 无法做到） */
    suspend fun launchApp(sessionId: String = DEFAULT_SESSION_ID, packageName: String): Boolean =
        controller(sessionId).launchApp(packageName)

    /** 向虚拟屏注入按键（如系统返回 KEYCODE_BACK）。 */
    suspend fun injectKey(sessionId: String = DEFAULT_SESSION_ID, keyCode: Int): Boolean =
        controller(sessionId).key(keyCode)

    /**
     * 空 VirtualDisplay 合成结果是纯黑；打开「手机」预览时先往该屏丢一个桌面/设置，
     * 避免 setDisplaySurface 成功仍看起来像黑屏。
     */
    suspend fun warmDisplayContent(sessionId: String = DEFAULT_SESSION_ID): Boolean {
        if (controller(sessionId).getDisplayId() == null) return false
        for (pkg in resolveVirtualDisplayWarmPackages(context)) {
            if (launchApp(sessionId, pkg)) {
                logger.w("虚拟屏预热已启动 session=$sessionId pkg=$pkg")
                return true
            }
            logger.w("虚拟屏预热候选失败 session=$sessionId pkg=$pkg")
        }
        return false
    }

    /** 整屏截图：优先 PixelCopy，再 screencap，最后 Binder（避免大图撑爆 1MB 事务）。 */
    suspend fun requestScreenshot(
        sessionId: String = DEFAULT_SESSION_ID,
        timeoutMs: Long = SCREENSHOT_TIMEOUT_MS,
    ): ByteArray? = withContext(Dispatchers.IO) {
        VirtualDisplayFrameCaptures.capture(sessionId)?.let { return@withContext it }
        screencapToBytes(sessionId)?.let { return@withContext it }
        logger.w("虚拟屏截图回退 Binder 通道（session=$sessionId）")
        controller(sessionId).requestScreenshot(timeoutMs)
    }

    /** shell screencap；失败返回 null。 */
    private suspend fun screencapToBytes(sessionId: String): ByteArray? {
        val logicalDisplayId = getDisplayId(sessionId) ?: return null
        val runner = ShowerEnvironment.shellRunner ?: return null
        // 路径必须同时满足「shell 可写」与「App 可读」：App 外部私有目录两者都满足且无需存储权限
        // （/data/local/tmp 因 SELinux shell_data_file 标签 App 读不了，不能用）。
        val dir = context.getExternalFilesDir(null) ?: return null
        val physicalDisplayId = resolvePhysicalDisplayId(runner, logicalDisplayId) ?: return null
        // 每次截图独立路径，不会因同 session 并发或文件名净化碰撞而读错图。
        val file = try {
            File.createTempFile("vs_screenshot_", ".png", dir)
        } catch (e: Exception) {
            logger.w("创建虚拟屏截图文件失败：${e.message}")
            return null
        }
        try {
            val result = runner.run(
                // chmod 644：文件由 shell 创建（属 shell:ext_data_rw），显式放开「其它」读位，
                // 保证另一 uid 的 App 进程能读到，不单纯依赖 FUSE 的按包可见性。
                "screencap -d $physicalDisplayId -p \"${file.absolutePath}\" && " +
                    "chmod 644 \"${file.absolutePath}\"",
                ShellIdentity.SHELL,
            )
            if (!result.success || !file.exists() || file.length() == 0L) {
                logger.w(
                    "screencap 截图失败（session=$sessionId, display=$logicalDisplayId, " +
                        "physical=$physicalDisplayId, exit=${result.exitCode}, " +
                        "err=${result.stderr.trim().take(LOG_PREVIEW_LIMIT)}）",
                )
                return null
            }
            return file.readBytes()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logger.w("读取虚拟屏截图失败：${e.message}")
            return null
        } finally {
            file.delete()
        }
    }

    /**
     * 把逻辑 displayId 解析为 `screencap -d` 需要的**物理** display id（`dumpsys SurfaceFlinger
     * --display-id` 中列出的那个）。
     *
     * 该 id 是 64 位无符号数（实测虚拟屏为 11529215046613627067，已超出 Long.MAX_VALUE），
     * **必须按字符串原样传递**，任何 toLong()/toInt() 都会溢出成非法值。
     *
     * 先查逻辑屏的名称，再与 SurfaceFlinger 的 displayName 精确匹配。
     * 名称不可见、未匹配或同名多屏时拒绝猜屏，交给按逻辑 id 截图的 Binder 通道。
     */
    private suspend fun resolvePhysicalDisplayId(runner: ShellRunner, logicalDisplayId: Int): String? {
        val displayManager = context.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
        val name = displayManager.getDisplay(logicalDisplayId)?.name ?: run {
            val logicalDump = runner.run("dumpsys display", ShellIdentity.SHELL)
            if (!logicalDump.success) return null
            logicalDisplayName(logicalDump.stdout, logicalDisplayId) ?: return null
        }
        val dump = runner.run("dumpsys SurfaceFlinger --display-id", ShellIdentity.SHELL)
        if (!dump.success) {
            logger.w(
                "读取 SurfaceFlinger 显示器列表失败（exit=${dump.exitCode}）：" +
                    dump.stderr.trim().take(LOG_PREVIEW_LIMIT),
            )
            return null
        }
        val chosen = physicalDisplayIdForName(dump.stdout, name)
        if (chosen == null) {
            logger.w(
                "未从 SurfaceFlinger 解析出物理屏 id（logical=$logicalDisplayId）：" +
                    dump.stdout.trim().take(LOG_PREVIEW_LIMIT),
            )
            return null
        }
        logger.d(
            "resolvePhysicalDisplayId: logical=$logicalDisplayId name=$name -> $chosen",
        )
        return chosen
    }

    /**
     * 打开分身 Tab + 发进度通知；不再弹半透明 Overlay 视频窗。
     * [minimized] 保留兼容旧调用，忽略。
     */
    @Suppress("UNUSED_PARAMETER")
    fun reveal(
        sessionId: String = DEFAULT_SESSION_ID,
        step: String,
        minimized: Boolean = false,
    ): String? {
        TwinPhonePresence.requestOpen()
        publishPhoneTaskProgress(step)
        // 清掉旧版本可能残留的黑块悬浮窗。
        hideOverlay()
        return null
    }

    /** 任务步骤进度（通知 + 分身页状态流）。 */
    fun setOverlayStep(step: String) = publishPhoneTaskProgress(step)

    fun setPhoneTaskTitle(title: String?) = phoneTaskUi.setTaskTitle(title)

    fun publishPhoneTaskProgress(step: String, stepIndex: Int = 0) =
        phoneTaskUi.progress(step, stepIndex)

    fun publishPhoneTaskPaused(
        sessionId: String = DEFAULT_SESSION_ID,
        step: String = "人工操作中 · 任务已暂停",
    ) = phoneTaskUi.paused(sessionId, step)

    fun takeOverPhoneTask(
        sessionId: String = DEFAULT_SESSION_ID,
        step: String = "你正在操作 · 接管中",
    ) = phoneTaskUi.takeOver(sessionId, step)

    fun publishPhoneTaskNeed(
        step: String,
        sessionId: String = DEFAULT_SESSION_ID,
    ) = phoneTaskUi.need(step, sessionId)

    fun publishPhoneTaskHandoff(
        sessionId: String = DEFAULT_SESSION_ID,
        step: String,
    ) {
        phoneTasks.requestHandoff(sessionId, PhoneTaskWaitMode.HANDOFF)
        phoneTaskUi.handoff(sessionId, step)
    }

    fun publishPhoneTaskConfirm(
        sessionId: String = DEFAULT_SESSION_ID,
        step: String,
    ) {
        phoneTasks.requestHandoff(sessionId, PhoneTaskWaitMode.CONFIRM)
        phoneTaskUi.confirm(sessionId, step)
    }

    fun publishPhoneTaskResult(step: String, success: Boolean, cancelled: Boolean = false) =
        phoneTaskUi.result(step, success, cancelled)

    fun resumePhoneTask(sessionId: String = DEFAULT_SESSION_ID) = phoneTaskUi.resume(sessionId)

    fun cancelPhoneTask(sessionId: String = DEFAULT_SESSION_ID) = phoneTaskUi.cancel(sessionId)

    /** 显式「显示画面」：打开分身 Tab，不叠 Overlay 黑窗。 */
    @Suppress("UNUSED_PARAMETER")
    fun showOverlay(sessionId: String = DEFAULT_SESSION_ID): Boolean {
        TwinPhonePresence.requestOpen()
        publishPhoneTaskProgress("分身画面")
        hideOverlay()
        return true
    }

    @Suppress("UNUSED_PARAMETER")
    fun showOverlayMinimized(sessionId: String = DEFAULT_SESSION_ID): Boolean {
        publishPhoneTaskProgress(VirtualDisplayHud.step.value)
        hideOverlay()
        return true
    }

    fun minimizeOverlay() {
        hideOverlay()
    }

    fun expandOverlay() {
        TwinPhonePresence.requestOpen()
        hideOverlay()
    }

    /** 隐藏遗留 Overlay 悬浮窗（未显示时为幂等空操作）。 */
    fun hideOverlay() {
        VirtualDisplayHud.hide()
    }

    /**
     * 销毁指定会话的虚拟屏并释放本地状态；server 进程由其空闲看护（15s 无客户端）自行退出。
     */
    suspend fun closeSession(sessionId: String) {
        phoneTasks.cancel(sessionId)
        lifecycleMutex.withLock { closeSessionLocked(sessionId) }
    }

    private fun closeSessionLocked(sessionId: String) {
        creationFailures.remove(sessionId)
        if (VirtualDisplayHud.showingSessionId == sessionId) {
            hideOverlay()
        }
        val controller = sessions.remove(sessionId) ?: return
        runCatching { controller.shutdown() }
            .onFailure { logger.w("关闭虚拟屏会话失败 (session=$sessionId): ${it.message}") }
        if (sessions.isEmpty()) {
            TwinDisplayKeepAlive.stop(context)
            phoneTaskUi.cancelAllNotifications()
        }
    }

    /** 关闭全部会话（App 退出或工作流整体结束时使用） */
    suspend fun closeAllSessions() {
        for (sessionId in sessions.keys.toList()) {
            closeSession(sessionId)
        }
    }

    /** 旧 H.264 缓冲裁剪入口；直出 Surface 后无缓冲可裁，保留空实现以免调用方改签名。 */
    fun trimVideoBuffers() = Unit

    @Suppress("DEPRECATION")
    private fun realDisplayMetrics(): DisplayMetrics {
        val metrics = DisplayMetrics()
        val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        windowManager.defaultDisplay.getRealMetrics(metrics)
        return metrics
    }

    companion object {
        const val DEFAULT_SESSION_ID = "default"
        private const val SCREENSHOT_TIMEOUT_MS = 3000L
        /** 日志中命令 stderr/stdout 的截断长度，避免异常输出刷爆 runtime.log */
        private const val LOG_PREVIEW_LIMIT = 200
    }
}
