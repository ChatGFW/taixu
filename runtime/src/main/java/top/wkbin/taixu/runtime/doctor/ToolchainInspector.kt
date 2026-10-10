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
 * 设计要点：
 *  - 探针与判定分离，判定逻辑在 core/model 里可被 JVM 单测完整覆盖；
 *  - 沙箱不可达（未启动 / 探测异常）时整体如实返回 UNKNOWN，绝不误报「工具缺失」；
 *  - 版本命令统一加 `|| true`，避免工具存在但 `--version` 返回非 0 时被误判为缺失。
 */
class ToolchainInspector(
    private val linuxRuntime: LinuxRuntime,
) {

    /**
     * 逐项检测工具链。
     *
     * 为了不让 15 个探针串行拖慢体验，这里对每个探针各跑一条合并命令，
     * 一次性回读所有工具的存在性与版本，再在本地组装结论。
     */
    suspend fun inspect(): ToolchainReport = withContext(Dispatchers.IO) {
        val probes = SandboxToolchainCatalog.probes
        if (probes.isEmpty()) return@withContext ToolchainReport()

        // 一次探测拿到：每个探针的命中路径 + 版本行
        val raw = runCatching {
            linuxRuntime.execute(
                ShellCommand(
                    commandLine = buildProbeScript(probes),
                    timeoutMs = PROBE_TIMEOUT_MS,
                )
            )
        }.getOrNull()

        // 沙箱不可达：全部如实 UNKNOWN，不谎报缺失
        if (raw == null) {
            return@withContext ToolchainReport(
                results = probes.map {
                    ToolchainProbeResult(
                        probe = it,
                        status = ToolchainStatus.UNKNOWN,
                        summary = ToolchainVersion.describe(it, null, null, ToolchainStatus.UNKNOWN),
                    )
                },
            )
        }

        val parsed = parseProbeOutput(raw.stdout, probes)
        val results = probes.map { probe -> parsed[probe.id] ?: unknownResult(probe) }

        // root 依赖工具：即便就绪也显式标注，避免用户误以为装完即可驱动
        ToolchainReport(results = results)
    }

    /**
     * 生成合并探针脚本。
     *
     * 输出约定：每个探针输出两行，便于稳定解析且不依赖 shell 特有行为：
     * ```
     * __TX__<id>__PATH__<命中路径>
     * __TX__<id>__VER__<版本原文>
     * ```
     */
    private fun buildProbeScript(probes: List<ToolchainProbe>): String {
        val sb = StringBuilder()
        probes.forEach { probe ->
            // 1) 定位可执行文件：候选路径优先，再退 PATH 查找
            // sh 无 function return，用标记位实现「首个命中即停」
            val resolve = StringBuilder()
            resolve.append("res=\"\"; ")
            probe.candidatePaths.forEach { p ->
                resolve.append("if [ -z \"\$res\" ] && { [ -x \"$p\" ] || [ -f \"$p\" ]; }; then res=\"$p\"; fi; ")
            }
            probe.commands.forEach { cmd ->
                resolve.append("if [ -z \"\$res\" ]; then c=\$(command -v $cmd 2>/dev/null || true); [ -n \"\$c\" ] && res=\"\$c\"; fi; ")
            }
            resolve.append("echo \"__TX__${probe.id}__PATH__\$res\"; ")

            // 2) 取版本（仅当定位成功）
            // 版本命令里的 {cmd}/{path} 一律替换成 shell 变量 $res 的**裸引用**（不加引号、不加替换），
            // 这样探针自带的管道（如 zipalign 的 "| head -1"）仍能正常参与解析，
            // 不会与外层的 head/tr 叠加成双重管道。
            val versionCmd = probe.versionCommand
            val version = if (versionCmd == null) {
                ""
            } else {
                val shellCmd = versionCmd
                    .replace("{cmd}", "$res")
                    .replace("{path}", "$res")
                "if [ -n \"\$res\" ]; then v=\$($shellCmd 2>&1 | head -2 | tr '\\n' ' '); echo \"__TX__${probe.id}__VER__\$v\"; else echo \"__TX__${probe.id}__VER__\"; fi; "
            }

            sb.append(resolve).append(version)
        }
        return sb.toString()
    }

    /** 解析探针脚本输出，按探针 id 组装结论。 */
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
                // 格式: __TX__<id>__PATH__<值> 或 __TX__<id>__VER__<值>
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
            val resolved = pathById[probe.id]?.takeIf { it.isNotBlank() }
            val version = ToolchainVersion.extractVersion(verById[probe.id])
            val status = ToolchainVersion.resolveStatus(probe, resolved, version, reachable = true)
            out[probe.id] = ToolchainProbeResult(
                probe = probe,
                resolvedPath = resolved,
                version = version,
                status = status,
                summary = ToolchainVersion.describe(probe, resolved, version, status),
            )
        }
        return out
    }

    private fun unknownResult(probe: ToolchainProbe): ToolchainProbeResult = ToolchainProbeResult(
        probe = probe,
        status = ToolchainStatus.UNKNOWN,
        summary = ToolchainVersion.describe(probe, null, null, ToolchainStatus.UNKNOWN),
    )

    private companion object {
        const val PROBE_TIMEOUT_MS = 20_000L
        const val TAG = "__TX__"
        const val PATH_MARK = "__PATH__"
        const val VER_MARK = "__VER__"
    }
}
