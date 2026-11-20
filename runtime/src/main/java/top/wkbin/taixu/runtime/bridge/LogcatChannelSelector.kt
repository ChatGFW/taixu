package top.wkbin.taixu.runtime.bridge

import top.wkbin.taixu.core.model.ExecutionMode
import top.wkbin.taixu.runtime.bridge.adb.EmbeddedAdbManager
import top.wkbin.taixu.runtime.bridge.adb.LogcatCommand
import top.wkbin.taixu.runtime.privilege.PrivilegeManager

/**
 * `/api/logcat` 的通道选择。
 *
 * 优先使用内置无线 ADB；只有 ADB 通道本身不可用时才回退到 Root/Shizuku，
 * 使「Root 已激活但无线调试没连」这类场景仍能抓到日志——这正是本类存在的理由：
 * 旧实现只有 ADB 一条路径，请求会死在连接失败上。
 *
 * 独立成文件是为了让 [HostBridge] 保持在架构棘轮的文件行数上限内。
 */
internal object LogcatChannelSelector {
    const val ADB_CHANNEL = "wireless-adb"
    const val PRIVILEGE_CHANNEL = "privilege"

    data class Result(
        val outcome: EmbeddedAdbManager.ShellOutcome,
        val channel: String,
    )

    suspend fun capture(
        adb: EmbeddedAdbManager,
        privilege: PrivilegeManager,
        request: EmbeddedAdbManager.LogcatRequest,
    ): Result {
        val adbOutcome = adb.captureLogcat(request, explicitPort = null)
        // exitCode 非 null 说明 ADB 已实际执行并拿到退出码（例如「目标应用未运行」exit 3）。
        // 这类失败换通道也得不到不同结果，重跑只会掩盖真实退出码。
        if (adbOutcome.exitCode != null) return Result(adbOutcome, ADB_CHANNEL)

        // PRoot 下 modeActive 恒为 true，但 executeShellCommand 会直接拒绝执行。
        // 此时回退只会把 ADB 的原始错误覆盖成 PRoot 拒绝，故先用生效模式把这种情况挡掉。
        if (privilege.state.value.effectiveMode == ExecutionMode.PROOT) {
            return Result(adbOutcome, ADB_CHANNEL)
        }

        // 到这里才付出特权探测的代价（Root 模式下 getPrivilegeInfo 会实跑 su 探测，
        // 探测失败会被 PrivilegeManager 降级为 PRoot），因此不能无条件提前调用。
        val info = privilege.getPrivilegeInfo()
        val privilegedReady = info.modeActive &&
            info.mode != ExecutionMode.PROOT &&
            (info.shizukuAvailable || info.rootAvailable)
        if (!privilegedReady) return Result(adbOutcome, ADB_CHANNEL)

        val lines = request.lines.coerceIn(1, EmbeddedAdbManager.MAX_LOG_LINES)
        val privileged = privilege.executeShellCommand(LogcatCommand.build(request, lines))
        // 特权路径自身执行失败（exitCode == -1：命令为空、超时、进程异常）。
        // 保留原 ADB 的 ShellOutcome，避免用一条无关失败掩盖真实的 ADB 错误。
        if (privileged.exitCode == -1) return Result(adbOutcome, ADB_CHANNEL)

        val raw = privileged.stdout + privileged.stderr
        // 与 ADB 路径同口径：仅在成功时过滤关键词。
        // 失败输出若被滤空，「目标应用未运行」「Permission denied」会被误分类为 internal。
        val output = if (privileged.success && request.keyword.isNotEmpty()) {
            LogcatCommand.filter(raw, request.keyword)
        } else {
            raw
        }
        val outcome = EmbeddedAdbManager.ShellOutcome(
            exitCode = privileged.exitCode,
            output = output,
            success = privileged.success,
        )
        return Result(outcome, PRIVILEGE_CHANNEL)
    }
}
