package top.wkbin.taixu.runtime.bridge

import top.wkbin.taixu.runtime.privilege.PrivilegeInfo

/**
 * `taixu-host doctor` 的正文：把"某条能力命令为什么不可用"一次讲清。
 *
 * 只读设备状态，不含任何凭据内容；与 health 同等暴露面（本机回环、免认证）。
 */
internal fun hostDoctorReport(
    info: PrivilegeInfo,
    adbConnected: Boolean,
    guiPresent: Boolean,
    port: Int,
): String = buildString {
    appendLine("TaiXu host bridge doctor")
    appendLine("bridge            : ok (127.0.0.1:$port)")
    appendLine("privilege         : ${info.mode.id} · ${if (info.modeActive) "active" else "inactive"}")
    appendLine("shizuku / root    : ${info.shizukuAvailable} / ${info.rootAvailable}")
    appendLine("wireless-adb      : ${if (adbConnected) "connected" else "disconnected"}")
    appendLine("gui-controller    : ${if (guiPresent) "present" else "absent"}")
    appendLine("default-actions   : ${SandboxCredentialStore.SANDBOX_ACTIONS.sorted().joinToString(",")}")
    appendLine("control-actions   : ${SandboxCredentialStore.CONTROL_ACTIONS.sorted().joinToString(",")} (full access only)")
}
