package top.wkbin.taixu.harness

import java.io.File
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonPrimitive
import top.wkbin.taixu.runtime.gui.GuiKey
import top.wkbin.taixu.runtime.gui.GuiPrimitive
import top.wkbin.taixu.runtime.gui.ScrollDirection
import top.wkbin.taixu.runtime.virtualdisplay.VirtualDisplayCoordinator
import top.wkbin.taixu.runtime.virtualdisplay.VirtualScreenToolkit

/**
 * host 工具里 virtual_screen_* 的执行体。从 [ToolExecutor] 拆出来，避免那个文件继续涨过行数棘轮。
 */
internal class VirtualScreenHostActions(
    private val coordinator: VirtualDisplayCoordinator?,
    private val toolkit: VirtualScreenToolkit?,
    private val packageNamePattern: Regex,
    private val requireHostIdentifier: (JsonObject, String, Regex) -> String,
    private val requireString: (JsonObject, String) -> String,
    private val requireInt: (JsonObject, String) -> Int,
    private val optionalLong: (JsonObject, String, Long, Long, Long) -> Long,
    private val optionalSession: (JsonObject) -> String,
) {
    suspend fun execute(action: String, args: JsonObject): Pair<Boolean, String> = when (action) {
        "virtual_screen_ensure" -> {
            val coordinator = coordinator ?: return false to "未初始化虚拟屏协调器"
            val session = optionalSession(args)
            val displayId = coordinator.ensureVirtualDisplay(session)
            if (displayId == null) {
                false to "虚拟屏创建失败（session=$session）：Shower 服务未启动或建屏失败，详见 runtime.log 中的 [Shower] 日志（不一定是授权问题）"
            } else {
                val hidden = coordinator.reveal(session, "等待打开应用")
                true to "虚拟屏已就绪：session=$session displayId=$displayId。" +
                    (hidden ?: "悬浮窗已弹出。刚建好的屏上没有应用，画面是黑的。") +
                    "用 virtual_screen_launch 打开应用后才会有画面，再用 virtual_screen_screenshot 截图识图"
            }
        }
        "virtual_screen_launch" -> {
            val coordinator = coordinator ?: return false to "未初始化虚拟屏协调器"
            val session = optionalSession(args)
            val packageName = requireHostIdentifier(args, "package", packageNamePattern)
            if (coordinator.getDisplayId(session) == null &&
                coordinator.ensureVirtualDisplay(session) == null
            ) {
                return false to "虚拟屏创建失败（session=$session）：Shower 服务未启动或建屏失败，详见 runtime.log 中的 [Shower] 日志（不一定是授权问题）"
            }
            val res = coordinator.launchApp(session, packageName)
            if (res) {
                val hidden = coordinator.reveal(session, "启动 $packageName")
                true to "已在虚拟屏启动应用：$packageName（session=$session " +
                    "displayId=${coordinator.getDisplayId(session)}）。" +
                    (hidden ?: "悬浮窗上能看到这个应用，标题栏是当前步骤。")
            } else {
                coordinator.reveal(session, "启动失败 $packageName")
                false to "虚拟屏启动应用失败：$packageName（检查包名是否为已安装应用）"
            }
        }
        "virtual_screen_screenshot" -> {
            val coordinator = coordinator ?: return false to "未初始化虚拟屏协调器"
            val session = optionalSession(args)
            if (coordinator.getDisplayId(session) == null) {
                return false to "虚拟屏未创建（session=$session）：先调用 virtual_screen_ensure"
            }
            val hiddenShot = coordinator.reveal(session, "正在截图")
            val targetPath = requireString(args, "path")
            val png = coordinator.requestScreenshot(session)
                ?: return false to "虚拟屏截图失败（session=$session）：screencap/Binder 通道均不可用，详见 runtime.log 的 [Shower] 日志"
            runCatching {
                val file = File(targetPath)
                file.parentFile?.mkdirs()
                file.writeBytes(png)
            }.fold(
                onSuccess = {
                    true to "虚拟屏截图已保存至 $targetPath（${png.size} 字节），可用 read 查看图片" +
                        (hiddenShot?.let { " $it" } ?: "")
                },
                onFailure = { err -> false to "截图写入失败：${err.message}" },
            )
        }
        "virtual_screen_click",
        "virtual_screen_double_click",
        "virtual_screen_long_press",
        "virtual_screen_swipe",
        "virtual_screen_scroll",
        "virtual_screen_key",
        -> {
            val toolkit = toolkit ?: return false to "未初始化虚拟屏工具"
            val coordinator = coordinator ?: return false to "未初始化虚拟屏协调器"
            val session = optionalSession(args)
            val primitive = when (action) {
                "virtual_screen_click" ->
                    GuiPrimitive.Tap(requireInt(args, "x"), requireInt(args, "y"))
                "virtual_screen_double_click" ->
                    GuiPrimitive.DoubleTap(requireInt(args, "x"), requireInt(args, "y"))
                "virtual_screen_long_press" -> GuiPrimitive.LongPress(
                    x = requireInt(args, "x"),
                    y = requireInt(args, "y"),
                    durationMs = optionalLong(args, "duration_ms", 800L, 200L, 5_000L),
                )
                "virtual_screen_swipe" -> GuiPrimitive.Swipe(
                    x1 = requireInt(args, "x1"),
                    y1 = requireInt(args, "y1"),
                    x2 = requireInt(args, "x2"),
                    y2 = requireInt(args, "y2"),
                    durationMs = optionalLong(args, "duration_ms", 300L, 50L, 5_000L),
                )
                "virtual_screen_scroll" -> GuiPrimitive.Scroll(
                    direction = when (args["direction"]?.jsonPrimitive?.contentOrNull?.lowercase()) {
                        "up" -> ScrollDirection.UP
                        "down" -> ScrollDirection.DOWN
                        "left" -> ScrollDirection.LEFT
                        "right" -> ScrollDirection.RIGHT
                        else -> return false to "screen_scroll 需要 direction: up/down/left/right"
                    },
                    distanceRatio = args["distance_ratio"]?.jsonPrimitive?.doubleOrNull?.toFloat() ?: 0.45f,
                    durationMs = optionalLong(args, "duration_ms", 350L, 50L, 5_000L),
                )
                else -> {
                    val key = GuiKey.parse(requireString(args, "key"))
                        ?: return false to "未知按键：支持 back/home/recents/enter/delete/paste/power"
                    GuiPrimitive.Key(key)
                }
            }
            val result = toolkit.execute(session, primitive)
            val hiddenStep = coordinator.reveal(session, result.message)
            result.success to if (hiddenStep == null) result.message else "${result.message} $hiddenStep"
        }
        "virtual_screen_close" -> {
            val coordinator = coordinator ?: return false to "未初始化虚拟屏协调器"
            val session = optionalSession(args)
            coordinator.closeSession(session)
            true to "已关闭虚拟屏会话：$session"
        }
        "virtual_screen_show" -> {
            val coordinator = coordinator ?: return false to "未初始化虚拟屏协调器"
            val session = optionalSession(args)
            if (coordinator.getDisplayId(session) == null &&
                coordinator.ensureVirtualDisplay(session) == null
            ) {
                return false to "虚拟屏创建失败（session=$session）：Shower 服务未启动或建屏失败，详见 runtime.log 中的 [Shower] 日志（不一定是授权问题）"
            }
            if (coordinator.showOverlay(session)) {
                coordinator.reveal(session, "正在显示画面")
                true to "已显示虚拟屏实时悬浮窗（session=$session）：用户可观看画面并直接触摸干预，标题栏是当前步骤"
            } else {
                false to "悬浮窗权限未授予：请引导用户在系统设置中允许「显示在其他应用上层」后重试"
            }
        }
        "virtual_screen_hide" -> {
            val coordinator = coordinator ?: return false to "未初始化虚拟屏协调器"
            coordinator.hideOverlay()
            true to "已隐藏虚拟屏悬浮窗（虚拟屏会话不受影响，仍可继续操作）"
        }
        else -> error("virtual_screen action 未登记：$action")
    }

    companion object {
        fun handles(action: String): Boolean = action in ACTIONS

        private val ACTIONS = setOf(
            "virtual_screen_ensure",
            "virtual_screen_launch",
            "virtual_screen_screenshot",
            "virtual_screen_click",
            "virtual_screen_double_click",
            "virtual_screen_long_press",
            "virtual_screen_swipe",
            "virtual_screen_scroll",
            "virtual_screen_key",
            "virtual_screen_close",
            "virtual_screen_show",
            "virtual_screen_hide",
        )
    }
}
