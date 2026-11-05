package top.wkbin.taixu.runtime.bridge

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import top.wkbin.taixu.runtime.bridge.HostBridgeHttp.HttpResponse
import top.wkbin.taixu.runtime.bridge.HostCapabilityActions.Outcome

/**
 * 高危能力端点的参数解析。
 *
 * 只做"取字段 → 交给 [HostCapabilityActions]"：执行、权限检查与失败分类都在执行器里，
 * 避免解析分支各自漏掉一层校验。
 */
internal class HostControlEndpoints(
    private val json: Json,
    private val capabilities: HostCapabilityActions,
) {

    suspend fun handle(action: String, body: String): HttpResponse = when (action) {
        "host.settings" -> settings(body)
        "host.app" -> app(body)
        else -> screen(body)
    }

    private suspend fun settings(body: String): HttpResponse {
        val obj = parse(body) ?: return badBody()
        val namespace = obj.text("namespace")
        val key = obj.text("key")
        return when (obj.text("op").lowercase()) {
            "get" -> respond(capabilities.settingsGet(namespace, key))
            "put" -> respond(capabilities.settingsPut(namespace, key, obj.text("value")))
            else -> usage("op 仅支持 get / put")
        }
    }

    private suspend fun app(body: String): HttpResponse {
        val obj = parse(body) ?: return badBody()
        val pkg = obj.text("package")
        return when (obj.text("op").lowercase()) {
            "start" -> respond(capabilities.appStart(pkg))
            "stop" -> respond(capabilities.appStop(pkg))
            "freeze" -> respond(capabilities.appFreeze(pkg, freeze = true))
            "unfreeze" -> respond(capabilities.appFreeze(pkg, freeze = false))
            else -> usage("op 仅支持 start / stop / freeze / unfreeze")
        }
    }

    private suspend fun screen(body: String): HttpResponse {
        val obj = parse(body) ?: return badBody()
        return when (obj.text("op").lowercase()) {
            "observe" -> respond(capabilities.screenObserve())
            "tap" -> respond(capabilities.screenTap(obj.int("x") ?: return usage("缺少 x"), obj.int("y") ?: return usage("缺少 y")))
            "swipe" -> respond(
                capabilities.screenSwipe(
                    x1 = obj.int("x1") ?: return usage("缺少 x1"),
                    y1 = obj.int("y1") ?: return usage("缺少 y1"),
                    x2 = obj.int("x2") ?: return usage("缺少 x2"),
                    y2 = obj.int("y2") ?: return usage("缺少 y2"),
                    durationMs = obj.int("duration_ms")?.toLong() ?: 300L,
                ),
            )
            "text" -> respond(capabilities.screenText(obj.text("text")))
            "key" -> respond(capabilities.screenKey(obj.text("key")))
            else -> usage("op 仅支持 observe / tap / swipe / text / key")
        }
    }

    private fun parse(body: String): JsonObject? =
        runCatching { json.parseToJsonElement(body).jsonObject }.getOrNull()

    private fun JsonObject.text(key: String): String = this[key]?.jsonPrimitive?.content.orEmpty()

    private fun JsonObject.int(key: String): Int? = this[key]?.jsonPrimitive?.content?.trim()?.toIntOrNull()

    private fun respond(outcome: Outcome): HttpResponse = when (outcome) {
        is Outcome.Ok -> HttpResponse(200, HostJson.ok(outcome.output))
        is Outcome.Rejected -> HttpResponse(200, HostJson.error(outcome.message, outcome.failure))
    }

    private fun badBody() = usage("Invalid JSON body")

    private fun usage(message: String) = HttpResponse(400, HostJson.error(message, HostFailure.USAGE))
}
