package top.wkbin.taixu.runtime.bridge

import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import top.wkbin.taixu.runtime.bridge.adb.EmbeddedAdbManager

/**
 * `/api/logcat` 的响应信封。
 *
 * 无线 ADB 与宿主特权两条通道共用同一份拼装逻辑，保证同一请求换通道后
 * 字段集合与失败归类完全一致；`channel` 用来区分结果出自哪条通道。
 *
 * 独立成文件是为了让 [HostBridge] 保持在架构棘轮的文件行数上限内。
 */
internal object HostLogcatJson {
    fun of(result: EmbeddedAdbManager.ShellOutcome, channel: String): String {
        val exitCode = result.exitCode ?: if (result.success) 0 else 1
        return buildJsonObject {
            put("success", result.success)
            put("exitCode", exitCode)
            if (!result.success) {
                HostFailure.ofShellExit(exitCode, result.output)?.let { put("failure", it.wire) }
            }
            put("output", result.output)
            put("channel", channel)
        }.toString()
    }
}
