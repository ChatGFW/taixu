package top.wkbin.taixu.runtime.virtualdisplay

import android.content.Context
import android.hardware.display.DisplayManager
import com.ai.assistance.showerclient.ShellIdentity
import com.ai.assistance.showerclient.ShellRunner
import top.wkbin.taixu.core.common.logging.AppLogger
import java.io.File
import kotlinx.coroutines.CancellationException

/**
 * 虚拟屏 shell `screencap` 落盘读取：先逻辑 displayId，再 SurfaceFlinger 物理 id。
 * 从 [VirtualDisplayCoordinator] 拆出以满足 architectureCheck 行数上限。
 */
internal class VirtualDisplayScreencap(
    private val context: Context,
    private val logger: AppLogger,
    private val displayIdOf: (sessionId: String) -> Int?,
) {
    suspend fun capture(sessionId: String, runner: ShellRunner): ByteArray? {
        val logicalDisplayId = displayIdOf(sessionId) ?: return null
        val dir = context.getExternalFilesDir(null) ?: return null
        tryScreencapDisplay(runner, dir, sessionId, logicalDisplayId, logicalDisplayId.toString())
            ?.also {
                logger.d("screencap 逻辑屏成功 session=$sessionId display=$logicalDisplayId")
                return it
            }
        val physicalDisplayId = resolvePhysicalDisplayId(runner, logicalDisplayId) ?: return null
        return tryScreencapDisplay(runner, dir, sessionId, logicalDisplayId, physicalDisplayId)
    }

    private suspend fun tryScreencapDisplay(
        runner: ShellRunner,
        dir: File,
        sessionId: String,
        logicalDisplayId: Int,
        displayArg: String,
    ): ByteArray? {
        val file = try {
            File.createTempFile("vs_screenshot_", ".png", dir)
        } catch (e: Exception) {
            logger.w("创建虚拟屏截图文件失败：${e.message}")
            return null
        }
        try {
            val result = runner.run(
                "screencap -d $displayArg -p \"${file.absolutePath}\" && " +
                    "chmod 644 \"${file.absolutePath}\"",
                ShellIdentity.SHELL,
            )
            if (!result.success || !file.exists() || file.length() == 0L) {
                logger.w(
                    "screencap 截图失败（session=$sessionId, display=$logicalDisplayId, " +
                        "arg=$displayArg, exit=${result.exitCode}, " +
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

    private suspend fun resolvePhysicalDisplayId(
        runner: ShellRunner,
        logicalDisplayId: Int,
    ): String? {
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
        logger.d("resolvePhysicalDisplayId: logical=$logicalDisplayId name=$name -> $chosen")
        return chosen
    }

    private companion object {
        const val LOG_PREVIEW_LIMIT = 200
    }
}
