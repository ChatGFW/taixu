package top.wkbin.taixu.runtime.webchat

import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray

internal suspend fun handleExternalAgentGroupsRequest(
    exchange: AndroidHttpExchange,
    gateway: ExternalAgentRuntimeGateway,
    token: String,
    sendJson: (AndroidHttpExchange, Int, kotlinx.serialization.json.JsonObject) -> Unit,
    errorJson: (String) -> kotlinx.serialization.json.JsonObject,
    requestJson: (AndroidHttpExchange) -> kotlinx.serialization.json.JsonObject,
    getQueryParam: (AndroidHttpExchange, String) -> String?,
) {
    val path = exchange.requestURI.path.removePrefix("/runtime/v1/groups").trim('/')
    val parts = path.split('/').filter { it.isNotBlank() }
    when {
        parts.isEmpty() && exchange.requestMethod == "GET" -> {
            val groups = gateway.listGroups(token)
            sendJson(exchange, 200, buildJsonObject {
                putJsonArray("groups") {
                    groups.forEach { group ->
                        addJsonObject {
                            put("id", group.id)
                            put("name", group.name)
                        }
                    }
                }
            })
        }
        parts.size == 2 && parts[1] == "context" && exchange.requestMethod == "GET" -> {
            val limit = getQueryParam(exchange, "limit")?.toIntOrNull()
                ?: ExternalAgentRuntimeServiceDefaults.DEFAULT_CONTEXT_LIMIT
            val messages = gateway.groupContext(token, parts[0], limit)
            sendJson(exchange, 200, buildJsonObject {
                put("groupId", parts[0])
                putJsonArray("messages") {
                    messages.forEach { message ->
                        addJsonObject {
                            put("id", message.id)
                            put("senderId", message.senderId)
                            put("senderName", message.senderName)
                            put("senderType", message.senderType)
                            put("content", message.content)
                            put("timestamp", message.timestamp)
                            put("seq", message.seq)
                        }
                    }
                }
            })
        }
        parts.size == 2 && parts[1] == "messages" && exchange.requestMethod == "POST" -> {
            val content = requestJson(exchange)["content"]?.jsonPrimitive?.content.orEmpty()
            val messageId = gateway.postMessage(token, parts[0], content)
            sendJson(exchange, 200, buildJsonObject { put("messageId", messageId) })
        }
        else -> sendJson(exchange, 404, errorJson("未知路径"))
    }
}
