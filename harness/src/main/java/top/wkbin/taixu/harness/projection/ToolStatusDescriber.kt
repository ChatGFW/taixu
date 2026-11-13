package top.wkbin.taixu.harness.projection

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import top.wkbin.taixu.harness.HarnessTool
import top.wkbin.taixu.harness.text.HashlineEditArgs
/** 会话抽屉 / 状态点所展示的"正在执行什么"动作描述。 */
object ToolStatusDescriber {
    const val MAX_STATUS_ARG_LENGTH = 60

    private fun arg(args: JsonObject, name: String): String? =
        runCatching { args[name]?.jsonPrimitive?.content }.getOrNull()?.takeIf { it.isNotBlank() }

    fun describe(tool: HarnessTool, args: JsonObject, rawToolName: String? = null): String = when (tool) {
        HarnessTool.BASE -> {
            val command = arg(args, "command")?.lineSequence()?.first()?.trim().orEmpty()
            if (command.isEmpty()) "执行命令" else "执行命令：${command.take(MAX_STATUS_ARG_LENGTH)}"
        }
        HarnessTool.PROCESS -> "管理后台进程：${arg(args, "action") ?: "process"}${arg(args, "id")?.let { " · ${it.take(MAX_STATUS_ARG_LENGTH)}" }.orEmpty()}"
        HarnessTool.HOST -> {
            val action = arg(args, "action") ?: "host"
            when (action) {
                "screen_observe" -> "正在感知屏幕控件与前台应用…"
                "screen_click" -> "正在模拟点击屏幕坐标 (${arg(args, "x")}, ${arg(args, "y")})…"
                "screen_double_click" -> "正在双击屏幕坐标 (${arg(args, "x")}, ${arg(args, "y")})…"
                "screen_long_press" -> "正在长按屏幕坐标 (${arg(args, "x")}, ${arg(args, "y")})…"
                "screen_swipe" -> "正在滑动屏幕 (${arg(args, "x1")}, ${arg(args, "y1")}) ➔ (${arg(args, "x2")}, ${arg(args, "y2")})…"
                "screen_scroll" -> "正在滚动屏幕（${arg(args, "direction")}）…"
                "paste_text" -> "正在粘贴文本到焦点控件…"
                "screen_input_text" -> "正在向当前输入框打字：${arg(args, "text")?.take(20)}…"
                "screen_key" -> "正在触发系统按键：${arg(args, "key")}…"
                "app_launch" -> "正在调起宿主应用：${arg(args, "package")}…"
                "screen_capture" -> "正在截取屏幕画面…"
                "virtual_screen_screenshot" -> "正在查看分身屏幕…"
                "virtual_screen_click" -> "正在点击虚拟屏相对坐标 (${arg(args, "x")}, ${arg(args, "y")})…"
                "virtual_screen_input_text" -> "正在向虚拟屏输入：${arg(args, "text")?.take(20)}…"
                "virtual_screen_task" -> {
                    val spoken = arg(args, "ack") ?: arg(args, "goal")
                    if (spoken.isNullOrBlank()) "正在开始分身任务…"
                    else "分身任务：${spoken.take(MAX_STATUS_ARG_LENGTH)}…"
                }
                else -> "正在使用宿主权限：$action${arg(args, "command")?.let { " · ${it.take(MAX_STATUS_ARG_LENGTH)}" }.orEmpty()}"
            }
        }
        HarnessTool.DOWNLOAD -> arg(args, "destination")?.let { "下载文件：${it.takeLast(MAX_STATUS_ARG_LENGTH)}" } ?: "下载文件"
        HarnessTool.SEARCH_WEB -> "正在联网搜索：${arg(args, "query")?.take(MAX_STATUS_ARG_LENGTH).orEmpty()}"
        HarnessTool.READ -> arg(args, "path")?.let { "读取文件：${it.takeLast(MAX_STATUS_ARG_LENGTH)}" } ?: "读取文件"
        HarnessTool.WRITE -> arg(args, "path")?.let { "写入文件：${it.takeLast(MAX_STATUS_ARG_LENGTH)}" } ?: "写入文件"
        HarnessTool.EDIT -> {
            val path = arg(args, "path")?.takeLast(MAX_STATUS_ARG_LENGTH)
            val hashline = HashlineEditArgs.opsLabel(args)
            when {
                path != null && hashline != null -> "编辑文件：$path · $hashline"
                path != null -> "编辑文件：$path"
                hashline != null -> "编辑文件 · $hashline"
                else -> "编辑文件"
            }
        }
        HarnessTool.MEMORY -> "正在存取长期记忆：${arg(args, "key") ?: arg(args, "action") ?: "memory"}"
        HarnessTool.PLAN -> "正在更新任务执行规划：${arg(args, "goal") ?: arg(args, "action") ?: "plan"}"
        HarnessTool.SCRATCHPAD -> "正在记录工作草稿便签：${arg(args, "key") ?: arg(args, "action") ?: "scratchpad"}"
        HarnessTool.HISTORY_SEARCH -> "正在检索历史消息：${arg(args, "query")?.take(MAX_STATUS_ARG_LENGTH).orEmpty()}"
        HarnessTool.HISTORY_READ -> "正在读取历史消息：${arg(args, "message_id") ?: arg(args, "index") ?: "history"}"
        HarnessTool.BUILD_SCRIPT -> "正在管理构建脚本：${arg(args, "action") ?: "build_script"}${arg(args, "name")?.let { " · $it" }.orEmpty()}"
        HarnessTool.SUBAGENT -> "正在派发并执行子智能体协同任务…"
        HarnessTool.MCP -> "正在调用 MCP 插件工具：${rawToolName ?: "mcp"}…"
        HarnessTool.LOAD_RULE -> "正在加载规则块：${arg(args, "rule") ?: "load_rule"}…"
        HarnessTool.LOAD_SKILL -> "正在加载技能：${arg(args, "name") ?: "load_skill"}…"
        HarnessTool.COMPRESS -> "正在压缩会话历史…"
        HarnessTool.ASK_USER -> "正在向你提问…"
        HarnessTool.RENDER_SURFACE -> "正在生成 A2UI 原生界面：${arg(args, "title") ?: arg(args, "surfaceId") ?: "surface"}"
    }
}
