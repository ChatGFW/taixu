package top.wkbin.taixu.harness

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * 主屏 GUI 默认拒绝：防止模型用 app_launch/screen_* 抢走真机前台。
 * 仅当显式 main_screen=true（用户要求操作当前真机）时放行。
 */
internal fun refuseMainScreenSteal(args: JsonObject, action: String): Pair<Boolean, String>? {
    val allow = args["main_screen"]?.jsonPrimitive?.content
        ?.equals("true", ignoreCase = true) == true
    if (allow) return null
    return false to (
        "已拦截主屏 $action：它会抢走用户前台，打断太墟。" +
            "多步手机操作请改用 host(action=\"virtual_screen_task\", goal=\"一句可执行目标\", " +
            "ack=\"这就去做\", allow=[\"send\"|\"delete\"] 按需, package=\"…可选\")；" +
            "只有调用它分身才会动，不要描述点击步骤。" +
            "仅当用户明确要求操作真机当前界面时，再传 main_screen=true 重试。"
        )
}
