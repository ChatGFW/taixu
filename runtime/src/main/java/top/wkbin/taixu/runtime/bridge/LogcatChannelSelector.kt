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
 *
 * 判定逻辑（[needsProbe] / [decide] / [acceptPrivilegeResult] / [shapePrivilegeOutput]）
 * 均为纯函数，不碰 IO，因此可被单测锁住；[capture] 只按判定结果执行特权命令。
 */
internal object LogcatChannelSelector {
    const val ADB_CHANNEL = "wireless-adb"
    const val PRIVILEGE_CHANNEL = "privilege"

    data class Result(
        val outcome: EmbeddedAdbManager.ShellOutcome,
        val channel: String,
    )

    /** 决策结果：要么直接用 ADB 结果，要么去跑特权通道。 */
    enum class Decision {
        USE_ADB,
        USE_PRIVILEGE,
    }

    /** 特权快照的最小化投影，使判定可测且不依赖 PrivilegeManager 的构造。 */
    data class PrivilegeInfoSnapshot(
        val modeActive: Boolean,
        val mode: ExecutionMode,
        val shizukuAvailable: Boolean,
        val rootAvailable: Boolean,
    ) {
        val ready: Boolean
            get() = modeActive && mode != ExecutionMode.PROOT && (shizukuAvailable || rootAvailable)
    }

    /**
     * 是否还需要进一步探测特权。
     *
     * 返回 `false` 表示可以就地判定「不回退」，**不得**调用 `getPrivilegeInfo()`：
     * Root 模式下该调用会实跑 `su -c echo ok`，探测失败会把生效模式降级为 PRoot/DEGRADED，
     * 哪怕无线 ADB 本身已经成功——所以能提前挡掉的必须提前挡掉。
     */
    fun needsProbe(adbExitCode: Int?, effectiveMode: ExecutionMode): Boolean {
        // ADB 已实际执行并拿到退出码（例如「目标应用未运行」exit 3）。
        // 换通道得不到不同结果，重跑只会掩盖真实退出码。
        if (adbExitCode != null) return false
        // PRoot 下 modeActive 恒为 true，但 executeShellCommand 会直接拒绝执行。
        // 此时回退只会把 ADB 的原始错误覆盖成 PRoot 拒绝。
        if (effectiveMode == ExecutionMode.PROOT) return false
        return true
    }

    /** 拿到特权快照后的最终判定。仅当特权确实就绪时才回退。 */
    fun decide(
        adbExitCode: Int?,
        effectiveMode: ExecutionMode,
        privilegeInfo: PrivilegeInfoSnapshot,
    ): Decision {
        if (!needsProbe(adbExitCode, effectiveMode)) return Decision.USE_ADB
        return if (privilegeInfo.ready) Decision.USE_PRIVILEGE else Decision.USE_ADB
    }

    /**
     * 特权路径结果是否应被采用。
     * exitCode == -1 表示特权路径自身没跑起来（命令为空、超时、进程异常、PRoot 拒绝），
     * 此时保留原 ADB 结果，避免用一条无关失败掩盖真实原因。
     */
    fun acceptPrivilegeResult(privilegeExitCode: Int): Boolean = privilegeExitCode != -1

    /** 与 ADB 路径同口径：仅在成功时过滤关键词，避免失败原因被滤空后误分类为 internal。 */
    fun shapePrivilegeOutput(success: Boolean, raw: String, keyword: String): String =
        if (success && keyword.isNotEmpty()) LogcatCommand.filter(raw, keyword) else raw

    suspend fun capture(
        adb: EmbeddedAdbManager,
        privilege: PrivilegeManager,
        request: EmbeddedAdbManager.LogcatRequest,
    ): Result {
        val adbOutcome = adb.captureLogcat(request, explicitPort = null)

        // 先做无需探测的判定，避免 ADB 成功路径上白跑一次 su 探测。
        if (!needsProbe(adbOutcome.exitCode, privilege.state.value.effectiveMode)) {
            return Result(adbOutcome, ADB_CHANNEL)
        }

        val info = privilege.getPrivilegeInfo()
        val snapshot = PrivilegeInfoSnapshot(
            modeActive = info.modeActive,
            mode = info.mode,
            shizukuAvailable = info.shizukuAvailable,
            rootAvailable = info.rootAvailable,
        )
        if (decide(adbOutcome.exitCode, privilege.state.value.effectiveMode, snapshot) == Decision.USE_ADB) {
            return Result(adbOutcome, ADB_CHANNEL)
        }

        val lines = request.lines.coerceIn(1, EmbeddedAdbManager.MAX_LOG_LINES)
        val privileged = privilege.executeShellCommand(LogcatCommand.build(request, lines))
        if (!acceptPrivilegeResult(privileged.exitCode)) return Result(adbOutcome, ADB_CHANNEL)

        val raw = privileged.stdout + privileged.stderr
        val outcome = EmbeddedAdbManager.ShellOutcome(
            exitCode = privileged.exitCode,
            output = shapePrivilegeOutput(privileged.success, raw, request.keyword),
            success = privileged.success,
        )
        return Result(outcome, PRIVILEGE_CHANNEL)
    }
}
