package top.wkbin.taixu.runtime.droidspaces

import top.wkbin.taixu.core.common.logging.AppLogger
import top.wkbin.taixu.core.common.shell.ShellQuote
import top.wkbin.taixu.core.model.StorageMountBinding
import top.wkbin.taixu.runtime.EnvironmentResolver
import top.wkbin.taixu.runtime.backend.GuestCommandBuilder
import top.wkbin.taixu.runtime.shell.SessionConfig
import top.wkbin.taixu.runtime.shell.ShellCommand
import java.io.File

/**
 * Droidspaces 容器后端：把客态命令交给 `droidspaces run` 在真实容器命名空间内执行。
 *
 * 与 PRoot 后端的本质差异：
 * - **需要 Root**（`su -c` 进入 UID 0），因此本构建器只在 `ExecutionMode.ROOT` 下装配；
 * - 客态是**真内核命名空间**，无 syscall 拦截开销、原生 ext4 语义（硬链接/lchown 正常）；
 * - 容器**生命周期独立于单次命令**：挂载 `-B` 只在 `start` 时生效，运行期无法追加。
 *
 * 因此本类不写 Python/Kotlin 侧的容器管理状态机，而是把「确保容器在跑」下沉到
 * 宿主编排脚本（见 [lifecyclePrelude]），保证 builder 仍是纯函数：同样入参必得同样 argv。
 */
class DroidspacesCommandBuilder private constructor(
    private val environmentResolver: EnvironmentResolver,
    private val logWarning: (String) -> Unit,
    private val config: DroidspacesConfig,
) : GuestCommandBuilder {

    constructor(
        environmentResolver: EnvironmentResolver,
        logger: AppLogger,
    ) : this(environmentResolver, { message -> logger.w(message) }, DroidspacesConfig())

    /** JVM 单测无需 Android 日志与真实设备，仅校验纯参数构造。 */
    internal constructor(
        environmentResolver: EnvironmentResolver,
    ) : this(environmentResolver, {}, DroidspacesConfig())

    override fun build(
        prootBinary: File,
        rootfsDir: File,
        workspaceDir: File,
        homeDir: File,
        optDir: File,
        tmpDir: File,
        attachmentsDir: File,
        command: ShellCommand,
        mounts: List<StorageMountBinding>,
        emulatorBinary: File?,
        hostCredential: Map<String, String>,
    ): List<String> {
        // QEMU 兼容模式依赖 PRoot 的 `-q` 用户态仿真；Droidspaces 复用宿主内核，
        // 无法提供 x86_64 指令翻译。静默放行会得到「命令看似成功、实际架构不符」的假成功。
        require(emulatorBinary == null) {
            "Droidspaces 后端不支持 x86_64 QEMU 兼容模式；请在设置中切回 PRoot 后端，或改用 native ARM64 发行版"
        }
        val distro = distroIdOf(rootfsDir)
        val environment = environmentResolver.merge(provider = command.environment) + hostCredential
        val inner = guestCommand(
            commandLine = command.commandLine,
            workingDirectory = command.workingDirectory,
            environment = environment,
            pty = command.forcePty,
        )
        return hostArgv(
            distro = distro,
            rootfsDir = rootfsDir,
            workspaceDir = workspaceDir,
            optDir = optDir,
            tmpDir = tmpDir,
            attachmentsDir = attachmentsDir,
            mounts = mounts,
            inner = inner,
        )
    }

    override fun buildInteractive(
        prootBinary: File,
        rootfsDir: File,
        workspaceDir: File,
        homeDir: File,
        optDir: File,
        tmpDir: File,
        attachmentsDir: File,
        config: SessionConfig,
        ptyMarker: String?,
        nativePty: Boolean,
        mounts: List<StorageMountBinding>,
        emulatorBinary: File?,
        hostCredential: Map<String, String>,
    ): List<String> {
        require(emulatorBinary == null) {
            "Droidspaces 后端不支持 x86_64 QEMU 兼容模式；请在设置中切回 PRoot 后端，或改用 native ARM64 发行版"
        }
        if (config.usePty && !nativePty) {
            // 协议会话（MCP STDIO）必须保持纯管道，否则 banner/提示符会污染输出流。
            logWarning("Droidspaces: 交互会话缺少原生 PTY，已退化为 run + script 伪终端")
        }
        val distro = distroIdOf(rootfsDir)
        val columns = config.columns.coerceIn(20, 400)
        val rows = config.rows.coerceIn(5, 200)
        val environment = environmentResolver.merge(
            provider = config.environment,
            interactive = true,
        ) + hostCredential
        val bannerPrelude = if (config.showBanner) "cat $GUEST_MOTD 2>/dev/null; " else ""
        val markerPrelude = ptyMarker?.also {
            require(PTY_MARKER.matches(it)) { "PTY marker path is invalid" }
        }?.let { "tty > $it; " }.orEmpty()
        // `droidspaces run` 不分配 PTY（实测 NOTTY），几何信息只能口头下发；
        // 需要真 TTY 的交互场景应走 `enter`，此处保持与 PRoot 后端的 script 退化一致。
        val geometry = if (config.usePty) "stty cols $columns rows $rows 2>/dev/null; " else ""
        val inner = guestCommand(
            commandLine = bannerPrelude + markerPrelude + geometry + config.commandLine,
            workingDirectory = config.workingDirectory,
            environment = environment,
            pty = false,
        )
        return hostArgv(
            distro = distro,
            rootfsDir = rootfsDir,
            workspaceDir = workspaceDir,
            optDir = optDir,
            tmpDir = tmpDir,
            attachmentsDir = attachmentsDir,
            mounts = mounts,
            inner = inner,
        )
    }

    /** 客态侧命令行：用 `env` 前缀注入变量，避免为每次执行落盘 `-E` 环境文件。 */
    private fun guestCommand(
        commandLine: String,
        workingDirectory: String,
        environment: Map<String, String>,
        pty: Boolean,
    ): String {
        val exports = environment.entries
            .sortedBy { it.key }
            .joinToString(" ") { (key, value) ->
                require(ENVIRONMENT_KEY.matches(key)) { "Invalid environment variable name: $key" }
                "$key=${ShellQuote.of(value)}"
            }
        val body = "cd -- ${ShellQuote.of(workingDirectory)} && exec $commandLine"
        val wrapped = if (pty) wrapInPty(body) else body
        // 变量为空时不加 `env` 前缀：避免 `env  sh -lc` 这类双空格与多余的 exec 层。
        return if (exports.isBlank()) {
            "sh -lc ${ShellQuote.of(wrapped)}"
        } else {
            "env $exports sh -lc ${ShellQuote.of(wrapped)}"
        }
    }

    /**
     * 宿主侧 argv：`su -c '<宿主编排脚本>'`。
     *
     * 脚本先确保容器处于运行态（`run` 要求容器已运行，见 Droidspaces `container.c`），
     * 再 `exec` 进入目标命令——`exec` 让 su 进程被直接替换，避免多一层 shell 残留
     * 导致退出码与信号转发失真。
     */
    private fun hostArgv(
        distro: String,
        rootfsDir: File,
        workspaceDir: File,
        optDir: File,
        tmpDir: File,
        attachmentsDir: File,
        mounts: List<StorageMountBinding>,
        inner: String,
    ): List<String> {
        val container = containerName(distro)
        val script = lifecyclePrelude(
            container = container,
            rootfsDir = rootfsDir,
            binds = bindArguments(workspaceDir, optDir, tmpDir, attachmentsDir, mounts),
        ) +
            "exec " + ShellQuote.of(config.cliPath) +
            " --name=" + ShellQuote.of(container) +
            " run -u root -- " + inner
        return listOf("su", "-c", script)
    }

    /**
     * 客态目录视图必须与 PRoot 后端**逐项对齐**，否则插件、工具链、MCP 能力全部失效：
     *
     * | PRoot `-b` | Droidspaces `-B` |
     * |---|---|
     * | `<base>/workspace` → `/workspace` | 同名 |
     * | `<base>/distros/<id>/opt/taixu` → `/opt/taixu` | 同名 |
     * | `<base>/tmp` → `/tmp` | 同名 |
     * | `<base>/attachments` → `/attachments` | 同名 |
     *
     * 宿主的 `/dev` `/proc` `/sys` 由容器自身提供，无需绑定。
     * 用户存储挂载沿用 [StorageMountBinding.validationError] 的统一校验，非法项直接跳过，
     * 与 PRoot 侧防挂载逃逸的最后防线保持一致。
     */
    private fun bindArguments(
        workspaceDir: File,
        optDir: File,
        tmpDir: File,
        attachmentsDir: File,
        mounts: List<StorageMountBinding>,
    ): List<String> {
        val binds = mutableListOf(
            "${workspaceDir.absolutePath}:/workspace",
            "${optDir.absolutePath}:/opt/taixu",
            "${tmpDir.absolutePath}:/tmp",
            "${attachmentsDir.absolutePath}:/attachments",
        )
        mounts.filter { it.enabled }.forEach { binding ->
            val error = StorageMountBinding.validationError(binding)
            if (error != null) {
                logWarning("存储挂载绑定不合法，已跳过：${binding.hostPath}（$error）")
                return@forEach
            }
            val guest = StorageMountBinding.normalizeGuestPath(binding.guestPath) ?: return@forEach
            val host = File(binding.hostPath)
            if (!host.isDirectory || !host.canRead()) {
                logWarning("宿主挂载目录不可访问，已跳过：${binding.hostPath}")
                return@forEach
            }
            binds += "${host.absolutePath}:$guest"
        }
        return binds
    }

    /**
     * 容器存活兜底 + 挂载下发。
     *
     * 关键约束：Droidspaces 的 `-B` 只在 `start` 时读取，运行期无法追加。因此：
     * - 容器未运行 → `start` 带上本次挂载清单；
     * - 容器已运行 → 不重启（会打断其它会话），本次挂载差异不生效。
     *
     * 运行态判定不依赖 `--json`（当前主线 `show` 恒输出表格），而是按词边界匹配容器名，
     * 避免 `taixu-ubuntu` 命中 `taixu-ubuntu2` 这类前缀重叠的容器。
     */
    private fun lifecyclePrelude(
        container: String,
        rootfsDir: File,
        binds: List<String>,
    ): String {
        val cli = ShellQuote.of(config.cliPath)
        val rootfs = ShellQuote.of(rootfsDir.absolutePath)
        val bindFlags = binds.joinToString(" ") { "-B " + ShellQuote.of(it) }
        return buildString {
            append("BIN=").append(cli).append("; ")
            append("NAME=").append(ShellQuote.of(container)).append("; ")
            // 用 `[[:space:]]` 两侧断言做词边界匹配，POSIX grep -E 与 busybox 均支持。
            append("if \$BIN --name=\"\$NAME\" show 2>/dev/null ")
            append("| grep -Eq \"(^|[^A-Za-z0-9_.-])\$NAME([^A-Za-z0-9_.-]|\$)\"; then :; ")
            append("else \$BIN start --name=\"\$NAME\" -r ").append(rootfs)
            if (bindFlags.isNotBlank()) append(' ').append(bindFlags)
            append(" >/dev/null 2>&1 || true; fi; ")
        }
    }

    private fun wrapInPty(commandLine: String): String =
        "if command -v script >/dev/null 2>&1; then " +
            "exec script -qfec " + ShellQuote.of(commandLine) + " /dev/null; " +
            "else $commandLine; fi"

    /** 发行版标识取 rootfs 目录的父目录名（与 `RuntimePathManager.distros/` 布局一致）。 */
    private fun distroIdOf(rootfsDir: File): String {
        val id = rootfsDir.absoluteFile.parentFile?.name?.lowercase()?.trim().orEmpty()
        return id.ifBlank { "ubuntu" }
    }

    private fun containerName(distro: String): String = config.containerPrefix + distro

    companion object {
        const val GUEST_MOTD = "/opt/taixu/motd"
        val PTY_MARKER = Regex("/opt/taixu/\\.pty-[A-Za-z0-9-]{8,64}")
        val ENVIRONMENT_KEY = Regex("[A-Za-z_][A-Za-z0-9_]*")
    }
}

/**
 * Droidspaces 后端装配参数。
 *
 * [cliPath] 默认指向官方安装位置；[containerPrefix] 决定容器名（`taixu-<distro>`）。
 *
 * 关于环境变量：Droidspaces 的 `-E` 环境文件必须位于其 workspace 目录下
 * （`config.c` 有显式校验），为避免每次执行落盘临时文件，本实现改用 `env` 前缀注入。
 */
data class DroidspacesConfig(
    val cliPath: String = "/data/local/Droidspaces/bin/droidspaces",
    val containerPrefix: String = "taixu-",
)
