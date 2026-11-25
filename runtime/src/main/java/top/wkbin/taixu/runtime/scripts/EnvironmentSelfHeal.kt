package top.wkbin.taixu.runtime.scripts

import top.wkbin.taixu.runtime.RuntimePathManager
import top.wkbin.taixu.runtime.backend.BackendSelector
import top.wkbin.taixu.runtime.shell.ShellCommand
import top.wkbin.taixu.runtime.shell.ShellExecutor

/**
 * RootFS 更新后的环境自愈。
 *
 * 更新会把整个发行版替换成上游镜像，旧 rootfs 里 apt 装过的包、以及
 * setup_android_core.sh 写出的 `/etc/profile.d/taixu-android.sh` 会一起消失，
 * 表现为环境检查掉项（基础工具缺失 / Node.js 未检测到 / Android 环境「未安装」）。
 *
 * 真正的补齐逻辑放在沙箱内的 `assets/scripts/taixu_env_heal.sh`（随
 * [RuntimeAssetSynchronizer] 同步到 /opt/taixu/scripts/），这里只负责触发：
 * 幂等脚本全部就绪时只做几次 stat，缺什么才补什么，永不返回非零。
 */
class EnvironmentSelfHeal(
    private val pathManager: RuntimePathManager,
    private val backendSelector: BackendSelector,
    private val shellExecutor: ShellExecutor,
) {
    companion object {
        const val GUEST_SCRIPT = "/opt/taixu/scripts/taixu_env_heal.sh"

        /** 补装依赖 apt 网络，给足时间；离线时脚本自身会快速放弃。 */
        const val HEAL_TIMEOUT_MS = 180_000L
    }

    /** 执行自愈脚本；任何异常都吞掉，环境自愈不能影响主流程。 */
    suspend fun heal(): Boolean = runCatching {
        shellExecutor.execute(
            command = backendSelector.active().build(
                prootBinary = pathManager.activeProotFile(),
                rootfsDir = pathManager.rootfsDir,
                workspaceDir = pathManager.workspaceDir,
                tmpDir = pathManager.tmpDir,
                attachmentsDir = pathManager.sharedAttachmentsDir,
                command = ShellCommand(
                    commandLine = "/bin/sh $GUEST_SCRIPT",
                    timeoutMs = HEAL_TIMEOUT_MS,
                ),
            ),
            timeoutMs = HEAL_TIMEOUT_MS,
        ).isSuccess
    }.getOrDefault(false)
}
