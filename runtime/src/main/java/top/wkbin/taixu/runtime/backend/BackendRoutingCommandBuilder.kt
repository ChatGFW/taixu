package top.wkbin.taixu.runtime.backend

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import top.wkbin.taixu.core.model.ExecutionMode
import top.wkbin.taixu.core.model.StorageMountBinding
import top.wkbin.taixu.runtime.shell.SessionConfig
import top.wkbin.taixu.runtime.shell.ShellCommand
import java.io.File

/**
 * 按当前 [ExecutionMode] 在 PRoot 与 Droidspaces 两个后端之间路由。
 *
 * 为什么不直接在 Koin 里 `if (mode == ROOT)` 选一个：`effectiveExecutionMode` 是
 * `Flow<ExecutionMode>`，而 Koin 单例在启动时创建一次。用户在设置页切换模式后，
 * 若绑定时求值，切换将不生效，直到重启 App。改为运行期路由后，两个后端常驻、
 * 按调用时的快照分派——切换即时生效，且不需要把 `Flow` 拉进 `GuestCommandBuilder`
 * 的接口（builder 仍是纯函数，可单测）。
 *
 * 模式语义（依据 `ExecutionMode` 既有承诺而非文案推断）：
 * - [ExecutionMode.ROOT]：容器后端。该模式的 `capabilities` 已声明「原生 ext4 语义 /
 *   零 syscall 拦截 / 支持 Docker」，此前并无实现；本路由是该承诺的兑现入口。
 * - 其余模式：仍走 PRoot。`SHIZUKU` 只提权宿主自动化，不改变 Linux 运行方式。
 */
class BackendRoutingCommandBuilder(
    private val proot: GuestCommandBuilder,
    private val droidspaces: GuestCommandBuilder,
    modeFlow: Flow<ExecutionMode>,
    scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
) : GuestCommandBuilder {

    /** 初值取 PRoot：与历史行为一致，且在 Flow 首帧到达前不会误用需要 root 的后端。 */
    private val mode = MutableStateFlow(ExecutionMode.PROOT)

    init {
        modeFlow.onEach { mode.value = it }.launchIn(scope)
    }

    /** 当前生效的后端（供健康检查与诊断日志读取）。 */
    val activeBackend: GuestCommandBuilder
        get() = if (mode.value == ExecutionMode.ROOT) droidspaces else proot

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
    ): List<String> = activeBackend.build(
        prootBinary = prootBinary,
        rootfsDir = rootfsDir,
        workspaceDir = workspaceDir,
        homeDir = homeDir,
        optDir = optDir,
        tmpDir = tmpDir,
        attachmentsDir = attachmentsDir,
        command = command,
        mounts = mounts,
        emulatorBinary = emulatorBinary,
        hostCredential = hostCredential,
    )

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
    ): List<String> = activeBackend.buildInteractive(
        prootBinary = prootBinary,
        rootfsDir = rootfsDir,
        workspaceDir = workspaceDir,
        homeDir = homeDir,
        optDir = optDir,
        tmpDir = tmpDir,
        attachmentsDir = attachmentsDir,
        config = config,
        ptyMarker = ptyMarker,
        nativePty = nativePty,
        mounts = mounts,
        emulatorBinary = emulatorBinary,
        hostCredential = hostCredential,
    )
}
