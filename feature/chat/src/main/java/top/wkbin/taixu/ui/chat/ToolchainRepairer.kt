package top.wkbin.taixu.ui.chat

import top.wkbin.taixu.core.model.PluginBundleScripts
import top.wkbin.taixu.core.model.RepairStrategy
import top.wkbin.taixu.core.model.ToolchainReport
import top.wkbin.taixu.core.tools.ToolManager
import top.wkbin.taixu.runtime.LinuxRuntime
import top.wkbin.taixu.runtime.shell.ShellCommand

/**
 * 🛠 沙箱工具链 apt 补齐器（仅处理开发套件未覆盖的 ByAptPackages 工具）。
 *
 * 职责与边界（对齐 wkbin review 的架构要求）：
 * - 开发套件已覆盖的工具（cmake/ninja/aapt2/apksigner/zipalign/jadx/apktool）
 *   由 ToolManager.batchInstallComponents 走套件安装，本类不碰；
 * - 本类只负责 ByAptPackages 项（patchelf/baksmali/dex2jar/readelf/strace/ltrace/gdb），
 *   并**复用 PluginBundleScripts 的 preparationSteps() 与 aptOptions()**——
 *   含 PRoot 的 force-unsafe-io/force-overwrite 准备、dpkg 状态修复、超时/重试选项，
 *   绝不自己删锁、换源、动 setuid；
 * - **逐包安装**：单个包失败不拖累其余包（review 第 5 条「合成一次 install 一个包找不到整批失败」）；
 * - 通过 [ToolManager.runExclusive] 与套件安装共用互斥锁，避免并发动 dpkg（review 第 8 条）；
 * - 每步实时回调 [onLog]，安装过程界面可見（review 第 6 条）。
 */
class ToolchainRepairer(
    private val linuxRuntime: LinuxRuntime,
    private val toolManager: ToolManager,
) {

    /**
     * 从报告中收集 ByAptPackages 待补齐项（去重合并包名）。
     */
    fun aptTargets(report: ToolchainReport): List<Pair<String, List<String>>> =
        report.repairable
            .mapNotNull { result ->
                val apt = result.probe.repair as? RepairStrategy.ByAptPackages ?: return@mapNotNull null
                result.probe.id to apt.packages
            }

    /**
     * 执行 apt 补齐。返回 成功安装的探针 id 列表 与 失败明细。
     *
     * @param onLog 实时日志回调（在调用方协程上下文串行回调）。
     */
    suspend fun repair(
        report: ToolchainReport,
        onLog: (String) -> Unit,
    ): RepairOutcome = toolManager.runExclusive {
        val targets = aptTargets(report)
        if (targets.isEmpty()) return@runExclusive RepairOutcome(emptyList(), emptyList())

        val installed = mutableListOf<String>()
        val failures = mutableListOf<String>()

        // 1) 复用开发套件的 PRoot 准备步骤（force-unsafe-io / dpkg 状态修复，不删业务锁之外的任何东西）
        val aptOpts = PluginBundleScripts.aptOptions()
        val prep = PluginBundleScripts.preparationSteps()
        onLog("==> [工具链补齐] 执行 PRoot 准备步骤（复用开发套件 PluginBundleScripts）")
        prep.forEach { step ->
            val r = linuxRuntime.execute(ShellCommand(commandLine = step, timeoutMs = STEP_TIMEOUT_MS))
            if (!r.isSuccess) {
                onLog("准备步骤告警: ${r.stderr.ifBlank { r.stdout }.takeLast(200)}")
            }
        }

        // 2) 逐包安装：一个包失败不影响其余包
        targets.forEach { (probeId, packages) ->
            packages.forEach { pkg ->
                onLog("==> 安装 $pkg")
                val script = "DEBIAN_FRONTEND=noninteractive apt-get $aptOpts install -y --no-install-recommends $pkg"
                val r = linuxRuntime.execute(ShellCommand(commandLine = script, timeoutMs = INSTALL_TIMEOUT_MS))
                // 退出码以 apt-get 本身为准（不用管道 tail，规避退出码吞掉问题）
                if (r.isSuccess) {
                    installed.add(probeId)
                    onLog("[OK] $pkg")
                } else {
                    val detail = r.stderr.ifBlank { r.stdout }.lineSequence()
                        .map { it.trim() }
                        .filter { it.isNotBlank() }
                        .toList()
                        .takeLast(3)
                        .joinToString(" | ")
                    failures.add("$pkg: $detail")
                    onLog("[FAIL] $pkg → $detail")
                }
            }
        }

        RepairOutcome(installed.distinct(), failures)
    }

    /** 补齐结果：成功安装的探针 id + 失败明细。 */
    data class RepairOutcome(
        val installedProbeIds: List<String>,
        val failures: List<String>,
    ) {
        val isSuccess: Boolean get() = failures.isEmpty()
    }

    private companion object {
        const val STEP_TIMEOUT_MS = 120_000L
        const val INSTALL_TIMEOUT_MS = 600_000L
    }
}
