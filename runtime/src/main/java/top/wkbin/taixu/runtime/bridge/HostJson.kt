package top.wkbin.taixu.runtime.bridge

import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** 宿主桥接的统一错误信封；失败类别一律走 `failure` 字段，HTTP 状态只表达传输层结果。 */
internal object HostJson {
    fun error(message: String, failure: HostFailure? = null): String = buildJsonObject {
        put("success", false)
        failure?.let { put("failure", it.wire) }
        put("error", message)
    }.toString()

    fun ok(output: String): String = buildJsonObject {
        put("success", true)
        if (output.isNotEmpty()) put("output", output)
    }.toString()
}
