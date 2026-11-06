package top.wkbin.taixu.harness

import kotlinx.serialization.json.*

/** 非流式响应：解析 output 数组（message / function_call / reasoning）与顶层 usage。 */
internal fun parseResponsesFinalResponse(json: Json, body: String, model: ModelConfig): ChatResult {
    if (!ProviderClient.looksLikeJsonResponse(body)) {
        throw IllegalStateException(ProviderClient.formatHttpErrorMessage(200, body))
    }
    val root = json.parseToJsonElement(body).jsonObject
    val text = StringBuilder()
    val reasoning = StringBuilder()
    val calls = mutableListOf<ApiToolCallSpec>()
    (root["output"]?.jsonArray.orEmpty()).forEach { element ->
        val item = element as? JsonObject ?: return@forEach
        when (item["type"]?.jsonPrimitive?.contentOrNull) {
            "message" -> {
                (item["content"]?.jsonArray.orEmpty()).forEach { part ->
                    val partObj = part as? JsonObject ?: return@forEach
                    if (partObj["type"]?.jsonPrimitive?.contentOrNull == "output_text") {
                        text.append(partObj["text"]?.jsonPrimitive?.contentOrNull.orEmpty())
                    }
                }
            }
            "function_call" -> calls += ApiToolCallSpec(
                id = item["call_id"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }
                    ?: item["id"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }
                    ?: ToolCallIdNormalizer.normalize(null),
                name = item["name"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                argumentsJson = item["arguments"]?.jsonPrimitive?.contentOrNull?.ifBlank { "{}" } ?: "{}",
            )
            "reasoning" -> {
                (item["summary"]?.jsonArray.orEmpty()).forEach { part ->
                    val partObj = part as? JsonObject ?: return@forEach
                    if (partObj["type"]?.jsonPrimitive?.contentOrNull == "summary_text") {
                        reasoning.append(partObj["text"]?.jsonPrimitive?.contentOrNull.orEmpty())
                    }
                }
            }
        }
    }
    return ChatResult(
        content = text.toString().ifEmpty { null },
        toolCalls = calls,
        reasoningContent = reasoning.toString().ifEmpty { null },
        usage = (root["usage"] as? JsonObject)?.let { parseResponsesUsage(it) } ?: ChatUsage(),
        responsesTurn = if (root["status"]?.jsonPrimitive?.contentOrNull in listOf(null, "completed"))
            ResponsesTurn.capture(model, root["output"] as? JsonArray) else null,
    )
}

/** Responses API usage：input/output_tokens + cached/reasoning details。 */
internal fun parseResponsesUsage(usage: JsonObject): ChatUsage = ChatUsage(
    inputTokens = usage["input_tokens"]?.jsonPrimitive?.longOrNull ?: 0,
    outputTokens = usage["output_tokens"]?.jsonPrimitive?.longOrNull ?: 0,
    reasoningTokens = (usage["output_tokens_details"] as? JsonObject)
        ?.get("reasoning_tokens")?.jsonPrimitive?.longOrNull ?: 0,
    cacheReadTokens = (usage["input_tokens_details"] as? JsonObject)
        ?.get("cached_tokens")?.jsonPrimitive?.longOrNull ?: 0,
)
