package top.wkbin.taixu.plugin.ui.web

import kotlinx.serialization.json.*
import top.wkbin.taixu.plugin.runtime.PluginCapability
import top.wkbin.taixu.plugin.runtime.PluginCapabilityCall
import top.wkbin.taixu.plugin.runtime.PluginCapabilityResult

/** No identity, environment, shell, credential or authorization fields accepted on the wire. */
internal object WebPluginMessages {
    fun decode(text: String): PluginCapabilityCall {
        require(text.length <= 4096)
        // Bound depth before parsing while allowing punctuation inside string values.
        var quoted = false
        var escaped = false
        var depth = 0
        var fields = 0
        for (character in text) {
            if (quoted) {
                if (escaped) escaped = false
                else if (character == '\\') escaped = true
                else if (character == '"') quoted = false
            } else when (character) {
                '"' -> quoted = true
                '{' -> { depth++; require(depth == 1) }
                '}' -> { depth--; require(depth == 0) }
                '[' , ']' -> error("Nested values are unsupported")
                ':' -> fields++
            }
        }
        require(fields == 3) // Reject duplicate fields even when the JSON parser would keep the last.
        val root = Json.parseToJsonElement(text).jsonObject
        require(root.keys == setOf("id", "action", "target"))
        fun string(key: String, limit: Int): String {
            val value = root.getValue(key).jsonPrimitive
            require(value.isString && value.content.length in 1..limit)
            return value.content
        }
        val id = string("id", 64)
        require(id.matches(Regex("[A-Za-z0-9_-]+")))
        return PluginCapabilityCall(id, PluginCapability(string("action", 128), string("target", 256)))
    }

    fun encode(result: PluginCapabilityResult): String = buildJsonObject {
        put("id", result.id)
        put("success", result.success)
        put("text", result.text)
    }.toString()
}
