package top.wkbin.taixu.runtime.doctor

import top.wkbin.taixu.core.model.SandboxToolchainCatalog
import top.wkbin.taixu.core.model.ToolchainProbe
import top.wkbin.taixu.core.model.ToolchainProbeResult
import top.wkbin.taixu.core.model.ToolchainReport
import top.wkbin.taixu.core.model.ToolchainStatus
import top.wkbin.taixu.core.model.ToolchainVersion
import top.wkbin.taixu.runtime.LinuxRuntime
import top.wkbin.taixu.runtime.shell.ShellCommand
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 🔬 沙箱工具链检测器
 *
 * 对 [SandboxToolchainCatalog] 里的每个探针跑一条 shell 命令，一次性拿到
 * 「工具是否存在 + 版本号」，交给纯逻辑 [ToolchainVersion] 判定状态。
 *
 * 关键口径（review 第 4 条）：
 * - 沙箱不可达（execute 抛异常）、命令超时（exitCode 124）、非 0 退出、
 *   stdout 被截断导致某个探针缺少 PATH/VER 标记 → 该工具一律判 UNKNOWN，绝不误报 MISSING。
 * - 每条版本命令外面包 `timeout 5`，串行 15 个探针（含 jadx/apktool 等 JVM --version）
 *   总超时给到 60 秒，避免 PRoot 里被误判超时。
 */
class ToolchainInspector(
    private val linuxRuntime: LinuxRuntime,
) {

    suspend fun inspect(): ToolchainReport = withContext(Dispatchers.IO) {
        val probes = SandboxToolchainCatalog.probes
        if (probes.isEmpty()) return@withContext ToolchainReport()

        val raw = runCatching {
            linuxRuntime.execute(
                ShellCommand(
                    commandLine = buildProbeScript(probes),
                    timeoutMs = PROBE_TIMEOUT_MS,
                )
            )
        }.getOrNull()

        // 沙箱不可达 / 执行抛异常：全部如实 UNKNOWN
        if (raw == null) {
            return@withContext ToolchainReport(
                results = probes.map { unknownResult(it) },
            )
        }

        // 命令超时或非 0 退出：全部 UNKNOWN，不误报缺失
        if (raw.exitCode != 0) {
            return@withContext ToolchainReport(
                results = probes.map { unknownResult(it) },
            )
        }

        val parsed = parseProbeOutput(raw.stdout, probes)
        val results = probes.map { probe -> parsed[probe.id] ?: unknownResult(probe) }
        ToolchainReport(results = results)
    }

    /**
     * 生成合并探针脚本。
     *
     * 输出约定：每个探针输出两行：
     * ```
     * __TX__<id>__PATH__<命中路径|空>
     * __TX__<id>__VER__<版本原文|空>
     * ```
     */
    private fun buildProbeScript(probes: List<ToolchainProbe>): String {
        val sb = StringBuilder()
        probes.forEach { probe ->
            // 1) 定位可执行文件：候选路径优先，再退 PATH 查找
            val resolve = StringBuilder()
            resolve.append("res=\"\"; ")
            probe.candidatePaths.forEach { p ->
                resolve.append("if [ -z \"\$res\" ] && { [ -x \"$p\" ] || [ -f \"$p\" ]; }; then res=\"$p\"; fi; ")
            }
            probe.commands.forEach { cmd ->
                resolve.append("if [ -z \"\$res\" ]; then c=\$(command -v $cmd 2>/dev/null || true); [ -n \"\$c\" ] && res=\"\$c\"; fi; ")
            }
            resolve.append("echo \"__TX__${probe.id}__PATH__\$res\"; ")

            // 2) 取版本（仅当定位成功），外层加 timeout 5 防单个工具卡死拖垮整轮
            val versionCmd = probe.versionCommand
            val version = if (versionCmd == null) {
                "echo \"__TX__${probe.id}__VER__\"; "
            } else {
                val shellCmd = versionCmd
                    .replace("{cmd}", "\$res")
                    .replace("{path}", "\$res")
                // 注意：sh -c 必须用双引号包裹，否则内层 shell 看不到外层展开的 $res（实测踩坑：
                // 单引号写法导致 CMake/Ninja 版本探测输出 "--version: not found" 而被误判待确认）
                "if [ -n \"\$res\" ]; then v=\$(timeout 5 sh -c \"$shellCmd\" 2>&1 | head -2 | tr '\\n' ' '); echo \"__TX__${probe.id}__VER__\$v\"; else echo \"__TX__${probe.id}__VER__\"; fi; "
            }

            sb.append(resolve).append(version)
        }
        return sb.toString()
    }

    /** 解析探针脚本输出。缺少 PATH 或 VER 标记的探针会被标记为 UNKNOWN（由调用方兜底）。 */
    private fun parseProbeOutput(
        stdout: String,
        probes: List<ToolchainProbe>,
    ): Map<String, ToolchainProbeResult> {
        val pathById = mutableMapOf<String, String>()
        val verById = mutableMapOf<String, String>()

        stdout.lineSequence()
            .map { it.trim() }
            .filter { it.startsWith(TAG) }
            .forEach { line ->
                val rest = line.removePrefix(TAG)
                val kind = if (rest.contains(PATH_MARK)) PATH_MARK else VER_MARK
                val idx = rest.indexOf(kind)
                if (idx <= 0) return@forEach
                val id = rest.substring(0, idx)
                val value = rest.substring(idx + kind.length).trim()
                if (id.isBlank()) return@forEach
                if (kind == PATH_MARK) pathById[id] = value else verById[id] = value
            }

        val out = mutableMapOf<String, ToolchainProbeResult>()
        probes.forEach { probe ->
            // 缺少 PATH 标记 → 该工具探测被截断/异常，判 UNKNOWN 而非 MISSING
            if (!pathById.containsKey(probe.id)) {
                out[probe.id] = unknownResult(probe)
                return@forEach
            }
            val resolved = pathById[probe.id]?.takeIf { it.isNotBlank() }
            val version = ToolchainVersion.extractVersion(verById[probe.id])
            val status = ToolchainVersion.resolveStatusWithRoot(probe, resolved, version, reachable = true)
            out[probe.id] = ToolchainProbeResult(
                probe = probe,
                resolvedPath = resolved,
                version = version,
                status = status,
                summary = "",
            )
        }
        return out
    }

    private fun unknownResult(probe: ToolchainProbe): ToolchainProbeResult = ToolchainProbeResult(
        probe = probe,
        status = ToolchainStatus.UNKNOWN,
        summary = "",
    )

    private companion object {
        // 15 个探针串行，含 jadx/apktool 等 JVM --version，给足 60 秒避免误判
        const val PROBE_TIMEOUT_MS = 60_000L
        const val TAG = "__TX__"
        const val PATH_MARK = "__PATH__"
        const val VER_MARK = "__VER__"
    }
}
