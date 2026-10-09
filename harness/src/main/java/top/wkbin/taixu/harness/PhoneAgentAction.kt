package top.wkbin.taixu.harness

/**
 * AutoGLM 一类手机操作模型的动作。坐标是 0..1000 的相对值，左上角为原点。
 */
internal sealed class PhoneAgentAction {
    data class Tap(val x: Int, val y: Int) : PhoneAgentAction()
    data class DoubleTap(val x: Int, val y: Int) : PhoneAgentAction()
    data class LongPress(val x: Int, val y: Int) : PhoneAgentAction()
    data class Swipe(val x1: Int, val y1: Int, val x2: Int, val y2: Int) : PhoneAgentAction()
    data class Type(val text: String) : PhoneAgentAction()
    data class Launch(val app: String) : PhoneAgentAction()
    data class Finish(val message: String) : PhoneAgentAction()
    data class TakeOver(val message: String) : PhoneAgentAction()
    data object Back : PhoneAgentAction()
    data object Home : PhoneAgentAction()
    data object Wait : PhoneAgentAction()
}

private val callStart = Regex("""(?<![A-Za-z0-9_])(?:do|finish)\(""")
private val pair = Regex("""\[\s*(\d+)\s*,\s*(\d+)\s*]""")

/** 从模型原文里取出最后一个 do(...) / finish(...)。认不出时返回 null。 */
internal fun parsePhoneAgentAction(raw: String): PhoneAgentAction? {
    val match = callStart.findAll(raw).lastOrNull() ?: return null
    val call = raw.substring(match.range.first).trim()
    val open = call.indexOf('(')
    if (open < 0) return null
    val kind = call.substring(0, open).trim()
    val body = call.substring(open + 1)
    return if (kind == "finish") {
        PhoneAgentAction.Finish(quoted(body, "message").orEmpty().ifBlank { "已完成" })
    } else {
        parseDo(body)
    }
}

private fun parseDo(body: String): PhoneAgentAction? {
    val action = quoted(body, "action")?.trim().orEmpty()
    val points = pair.findAll(body).map { it.groupValues[1].toInt() to it.groupValues[2].toInt() }.toList()
    return when (action.lowercase()) {
        "tap" -> points.firstOrNull()?.let { PhoneAgentAction.Tap(it.first, it.second) }
        "double tap", "double_tap" -> points.firstOrNull()?.let { PhoneAgentAction.DoubleTap(it.first, it.second) }
        "long press", "long_press" -> points.firstOrNull()?.let { PhoneAgentAction.LongPress(it.first, it.second) }
        "swipe" -> if (points.size >= 2) {
            PhoneAgentAction.Swipe(points[0].first, points[0].second, points[1].first, points[1].second)
        } else {
            null
        }
        "type", "type_name" -> quoted(body, "text")?.let { PhoneAgentAction.Type(it) }
        "launch" -> quoted(body, "app")?.takeIf { it.isNotBlank() }?.let { PhoneAgentAction.Launch(it) }
        "back" -> PhoneAgentAction.Back
        "home" -> PhoneAgentAction.Home
        "wait" -> PhoneAgentAction.Wait
        "take_over", "take over" -> PhoneAgentAction.TakeOver(quoted(body, "message").orEmpty())
        else -> null
    }
}

/** 取 key="..." 的内容。action 取下一对引号；text / message 收到最后一个引号，避免正文里的逗号被截断。 */
private fun quoted(body: String, key: String): String? {
    val marker = "$key=\""
    val start = body.indexOf(marker)
    if (start < 0) return null
    val from = start + marker.length
    val end = if (key == "action") body.indexOf('"', from) else body.lastIndexOf('"')
    if (end <= from) return null
    return body.substring(from, end).replace("\\n", "\n").replace("\\\"", "\"")
}

internal fun phoneAgentPoint(value: Int, size: Int): Int {
    if (size <= 1) return 0
    return (value.coerceIn(0, 1000) * (size - 1) / 1000)
}
