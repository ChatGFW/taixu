package top.wkbin.taixu.plugin.runtime

/** Typed input policy shared by wire decoding and native admission. */
object PluginCapabilityInput {
    val CLIPBOARD_WRITE = PluginCapability("host.clipboard-write", "clipboard")
    const val MAX_TEXT_LENGTH = 1024

    fun valid(call: PluginCapabilityCall): Boolean = if (call.capability == CLIPBOARD_WRITE) {
        call.text != null && call.text.length in 1..MAX_TEXT_LENGTH && call.text.none { it == '\u0000' }
    } else call.text == null

    /** Keep control / directional characters from changing the native permission prompt. */
    fun preview(text: String): String = buildString {
        append('"')
        text.forEach { char ->
            when {
                char == '"' || char == '\\' -> { append('\\'); append(char) }
                Character.isISOControl(char) || Character.getType(char) == Character.FORMAT.toInt() ->
                    append("\\u" + char.code.toString(16).padStart(4, '0'))
                else -> append(char)
            }
        }
        append('"')
    }
}
