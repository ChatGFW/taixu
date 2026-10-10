package top.wkbin.taixu.runtime.doctor

import top.wkbin.taixu.core.model.ToolchainProbe
import top.wkbin.taixu.core.model.ToolchainProbeResult
import top.wkbin.taixu.core.model.ToolchainStatus
import top.wkbin.taixu.runtime.LinuxRuntime
import top.wkbin.taixu.runtime.shell.CommandResult
import top.wkbin.taixu.runtime.shell.ShellCommand
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 🛠 沙箱工具链一键补齐器
 *
 * 目标：把「沙箱缺什么、什么版本太老」一次性补齐，包括版本升级，
 * 而不是让用户自己猜该装什么包。
 *
 * 三条硬性约束（均来自沙箱环境实测教训）：
 *  1. **只补明确缺失/落后的**，绝不覆盖已经就绪的工具，避免无谓破坏现成环境；
 *  2. **APT 一律走国内镜像**，并先清理 dpkg/apt 事务锁 —— PRoot 沙箱里 setuid 降级
 *     会留下 `*.dpkg-tmp` 残留导致 dpkg 卡死，不先清理必然失败；
 *  3. **离线套件路径兜底**：像 CMake/Ninja 这种构建期硬依赖，
 *     装完还要把可执行链接到离线套件目录，否则 Gradle 预检依旧报缺。
 */
class ToolchainInstaller(
    private val linuxRuntime: LinuxRuntime,
) {

    /**
     * 对 [targets] 逐项补齐。
     *
     * @param targets 待补齐项（通常来自 [ToolchainReport.repairable]）。
     * @param onLog 每条日志回调，用于把安装过程回显到面板。
     * @return 补齐后的最新检测报告，供 UI 刷新状态。
     */
    suspend fun install(
        targets: List<ToolchainProbeResult>,
        onLog: (String) -> Unit = {},
    ): List<ToolchainProbeResult> = withContext(Dispatchers.IO) {
        val inspector = ToolchainInspector(linuxRuntime)

        // 无需补齐：直接回传当前报告
        if (targets.isEmpty()) {
            onLog("所有工具均已就绪，无需补齐")
            return@withContext inspector.inspect().results
        }

        val actionable = targets.filter { it.status == ToolchainStatus.MISSING || it.status == ToolchainStatus.OUTDATED }
        if (actionable.isEmpty()) return@withContext inspector.inspect().results

        // 1) 先把 apt/dpkg 从锁残留里捞回来（PRoot 沙箱必备前置）
        onLog("准备阶段：清理 dpkg/apt 事务锁残留")
        runCatching {
            linuxRuntime.execute(ShellCommand(commandLine = UNLOCK_SCRIPT, timeoutMs = 60_000L))
        }

        // 2) 按探针逐项安装；同一次 apt 调用合并多个包，减少往返
        val pendingPackages = actionable.flatMap { it.probe.aptPackages }.distinct()
        if (pendingPackages.isNotEmpty()) {
            onLog("APT 安装 ${pendingPackages.size} 个软件包：${pendingPackages.joinToString(", ")}")
            val aptScript = buildAptInstallScript(pendingPackages)
            val res = runCatching {
                linuxRuntime.execute(ShellCommand(commandLine = aptScript, timeoutMs = APT_TIMEOUT_MS))
            }.getOrElse { CommandResult(1, "", it.message ?: "apt 执行异常", 0L) }
            appendResultLog(res, "APT 安装", onLog)
        }

        // 3) 非 apt 来源（离线套件/自带脚本）的兜底链接
        actionable.forEach { item ->
            val probe = item.probe
            val offlineLink = probe.offlineLinkPlan() ?: return@forEach
            onLog("离线套件兜底：${probe.displayName}")
            val res = runCatching {
                linuxRuntime.execute(ShellCommand(commandLine = offlineLink, timeoutMs = 30_000L))
            }.getOrElse { CommandResult(1, "", it.message ?: "兜底链接失败", 0L) }
            appendResultLog(res, "${probe.displayName} 兜底", onLog)
        }

        // 4) 版本升级：仅对已安装但低于最低版本的项强制升级
        val outdated = actionable.filter { it.status == ToolchainStatus.OUTDATED }
        if (outdated.isNotEmpty()) {
            val upgradable = outdated.flatMap { it.probe.aptPackages }.distinct()
            if (upgradable.isNotEmpty()) {
                onLog("版本升级：${upgradable.joinToString(", ")}")
                val res = runCatching {
                    linuxRuntime.execute(ShellCommand(commandLine = buildAptUpgradeScript(upgradable), timeoutMs = APT_TIMEOUT_MS))
                }.getOrElse { CommandResult(1, "", it.message ?: "升级执行异常", 0L) }
                appendResultLog(res, "版本升级", onLog)
            }
        }

        // 5) 重新检测并回传最新状态
        onLog("补齐完成，正在重新检测...")
        inspector.inspect().results
    }

    /** 合并安装脚本：清锁 → 国内源 → apt update → 安装全部。 */
    private fun buildAptInstallScript(packages: List<String>): String {
        val pkg = packages.joinToString(" ") { "'$it'" }
        return """
            set -e 2>/dev/null || true
            export DEBIAN_FRONTEND=noninteractive
            $UNLOCK_SCRIPT
            $MIRROR_SCRIPT
            apt-get update -y -qq 2>&1 | tail -3
            apt-get install -y -qq --no-install-recommends $pkg 2>&1 | tail -8
        """.trimIndent()
    }

    /** 仅升级指定包到候选最新版（不降级、不动其他包）。 */
    private fun buildAptUpgradeScript(packages: List<String>): String {
        val pkg = packages.joinToString(" ") { "'$it'" }
        return """
            set -e 2>/dev/null || true
            export DEBIAN_FRONTEND=noninteractive
            $UNLOCK_SCRIPT
            apt-get install -y -qq --only-upgrade --no-install-recommends $pkg 2>&1 | tail -8
        """.trimIndent()
    }

    private fun appendResultLog(
        result: CommandResult,
        prefix: String,
        onLog: (String) -> Unit,
    ) {
        if (result.isSuccess) {
            onLog("$prefix 成功")
            return
        }
        // lineSequence() 返回 Sequence，而 takeLast 只在 List 上存在，必须先 toList() 再截取
        val detail = result.stderr.ifBlank { result.stdout }.lineSequence()
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .toList()
            .takeLast(3)
            .joinToString(" | ")
        onLog("$prefix 未完全成功${if (detail.isBlank()) "" else "：$detail"}")
    }

    private companion object {
        const val APT_TIMEOUT_MS = 600_000L

        /**
         * 清理 dpkg/apt 事务锁。
         *
         * PRoot 沙箱 setuid 降级会残留 `*.dpkg-tmp`，不清掉 dpkg 会直接卡死；
         * 同时把已降级的 setuid 位改回普通权限，避免后续 apt 升级再次卡住。
         */
        val UNLOCK_SCRIPT = """
            rm -rf /var/lib/dpkg/updates/* /var/lib/apt/lists/lock /var/cache/apt/archives/lock 2>/dev/null || true
            find /var/lib/dpkg -name '*.dpkg-tmp' -delete 2>/dev/null || true
            find / -maxdepth 4 -perm -4000 -type f 2>/dev/null | while read -r f; do chmod 0644 "${'$'}f" 2>/dev/null || true; done
            dpkg --configure -a 2>&1 | tail -2 || true
        """.trimIndent()

        /** 清华 ubuntu-ports / debian / kali 三系国内源，ARM64 必须走 -ports。 */
        val MIRROR_SCRIPT = """
            mkdir -p /etc/apt/sources.list.d
            for f in /etc/apt/sources.list /etc/apt/sources.list.d/*.list /etc/apt/sources.list.d/*.sources; do
                if [ -f "${'$'}f" ]; then mv "${'$'}f" "${'$'}f.taixu-disabled" 2>/dev/null || true; fi
            done
            if [ -f /etc/os-release ]; then
                . /etc/os-release
                if [ "${'$'}ID" = "ubuntu" ]; then
                    CN="${'$'}{VERSION_CODENAME:-noble}"
                    printf 'deb https://mirrors.tuna.tsinghua.edu.cn/ubuntu-ports %s main restricted universe multiverse\n' "${'$'}CN" > /etc/apt/sources.list.d/taixu-mirrors.list
                elif [ "${'$'}ID_LIKE" = "debian" ] || [ "${'$'}ID" = "debian" ]; then
                    CN="${'$'}{VERSION_CODENAME:-bookworm}"
                    printf 'deb https://mirrors.tuna.tsinghua.edu.cn/debian %s main contrib non-free\n' "${'$'}CN" > /etc/apt/sources.list.d/taixu-mirrors.list
                elif [ "${'$'}ID" = "kali" ]; then
                    printf 'deb https://mirrors.tuna.tsinghua.edu.cn/kali kali-rolling main contrib non-free\n' > /etc/apt/sources.list.d/taixu-mirrors.list
                fi
            fi
            rm -rf /var/lib/apt/lists/* 2>/dev/null || true
        """.trimIndent()
    }
}

/**
 * 离线套件兜底链接方案：apt 装完后，把关键工具挂到构建期硬编码的路径上。
 *
 * 没有这一步，CMake 明明 `which cmake` 能查到，Gradle 预检仍会报
 * 「缺少可执行文件 CMake: /opt/taixu/tools/android-suite-offline/cmake/bin/cmake」。
 */
internal fun ToolchainProbe.offlineLinkPlan(): String? = when (id) {
    "cmake", "ninja" -> """
        d=/opt/taixu/tools/android-suite-offline
        mkdir -p "${'$'}d/cmake/bin" "${'$'}d/bin" 2>/dev/null || true
        if command -v cmake >/dev/null 2>&1; then
            real=\$(command -v cmake); [ -x "${'$'}d/cmake/bin/cmake" ] || ln -sf "${'$'}real" "${'$'}d/cmake/bin/cmake"
        fi
        if command -v ninja >/dev/null 2>&1; then
            real=\$(command -v ninja); [ -x "${'$'}d/cmake/bin/ninja" ] || ln -sf "${'$'}real" "${'$'}d/cmake/bin/ninja"
            [ -x "${'$'}d/bin/ninja" ] || ln -sf "${'$'}real" "${'$'}d/bin/ninja"
        fi
        chmod 755 "${'$'}d/cmake/bin/cmake" 2>/dev/null || true
        echo "offline links: cmake=\$( ${'$'}d/cmake/bin/cmake --version 2>/dev/null | head -1 )"
    """.trimIndent()

    else -> null
}
