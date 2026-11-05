package top.wkbin.taixu.harness

import kotlinx.serialization.json.*

internal class AnthropicStreamDecoder(
    private val json: Json,
    private val inputTokens: Int,
    private val onReasoning: (String) -> Unit,
    private val onToolProgress: (ToolCallStreamProgress) -> Unit,
    private val onDelta: (String) -> Unit,
    private val budget: StreamBudget,
) : ProviderStreamDecoder {
    private val text = StringBuilder()
    private val reasoningText = StringBuilder()
    // index -> 工具调用累积器（Claude 以 content block index 标识每个 tool_use）
    private val toolCalls = mutableMapOf<Int, ToolCallAccumulator>()
    private var usage = ChatUsage()
    var stopReason: String? = null
        private set
    private var completed = false
    private var result: ChatResult? = null

    override fun accept(data: String): ProviderDecodeStatus {
        check(result == null) { "Stream already finalized" }
        val event = runCatching { json.parseToJsonElement(data) as? JsonObject }.getOrNull()
            ?: return ProviderDecodeStatus(false)
        when (event["type"]?.jsonPrimitive?.contentOrNull) {
            "message_start" -> {
                val messageUsage = (event["message"] as? JsonObject)?.get("usage") as? JsonObject
                if (messageUsage != null) usage = parseUsage(messageUsage)
            }
            "message_delta" -> {
                val messageUsage = event["usage"] as? JsonObject
                val outputTokens = messageUsage?.get("output_tokens")?.jsonPrimitive?.longOrNull
                if (outputTokens != null) usage = usage.copy(outputTokens = outputTokens)
                // pause_turn：服务端长思考/服务端工具暂停，客户端需原样回放续跑
                (event["delta"] as? JsonObject)?.get("stop_reason")
                    ?.jsonPrimitive?.contentOrNull?.let { stopReason = it }
            }
            "content_block_start" -> {
                val index = event["index"]?.jsonPrimitive?.contentOrNull?.toIntOrNull() ?: 0
                val block = event["content_block"] as? JsonObject
                if (block != null && block["type"]?.jsonPrimitive?.contentOrNull == "tool_use") {
                    toolCalls.getOrPut(index) { ToolCallAccumulator() }.apply {
                        id = block["id"]?.jsonPrimitive?.contentOrNull.orEmpty()
                        name = block["name"]?.jsonPrimitive?.contentOrNull.orEmpty()
                        publishProgress(onToolProgress)
                    }
                }
            }
            "content_block_delta" -> {
                val index = event["index"]?.jsonPrimitive?.contentOrNull?.toIntOrNull() ?: 0
                val delta = event["delta"] as? JsonObject ?: return ProviderDecodeStatus(true)
                when (delta["type"]?.jsonPrimitive?.contentOrNull) {
                    "text_delta" -> delta["text"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotEmpty() }?.let { chunk ->
                        budget.add(chunk)
                        text.append(chunk)
                        onDelta(chunk)
                    }
                    "thinking_delta" -> delta["thinking"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotEmpty() }?.let { chunk ->
                        budget.add(chunk)
                        if (reasoningText.length < ProviderClient.MAX_STREAM_REASONING_CHARS) {
                            reasoningText.append(chunk.take(ProviderClient.MAX_STREAM_REASONING_CHARS - reasoningText.length))
                        }
                        onReasoning(chunk)
                    }
                    "input_json_delta" -> delta["partial_json"]?.jsonPrimitive?.contentOrNull?.let { chunk ->
                        toolCalls.getOrPut(index) { ToolCallAccumulator() }.apply {
                            arguments.append(chunk).also { budget.add(chunk) }
                            publishProgress(onToolProgress)
                        }
                    }
                }
            }
            "message_stop" -> {
                completed = true
                return ProviderDecodeStatus(true, true)
            }
            "error" -> throw providerStreamError(event.toString())
            else -> Unit
        }

        return ProviderDecodeStatus(true)
    }

    override fun finish(): ChatResult {
        result?.let { return it }
        requireStreamCompletion(completed, "Anthropic")
        requireOutputCompletion(stopReason, inputTokens, text.length + reasoningText.length)
        toolCalls.values.forEach { it.publishProgress(onToolProgress, force = true) }
        return ChatResult(
            content = text.toString().ifEmpty { null },
            // 无参数的工具调用（input={}）不会下发 input_json_delta，累积结果为空串，须兜底为 "{}"
            toolCalls = toolCalls.values.map {
                ApiToolCallSpec(it.id, it.name, it.arguments.toString().ifBlank { "{}" })
            },
            reasoningContent = reasoningText.toString().ifEmpty { null },
            usage = usage,
        ).also { result = it }
    }

    private fun parseUsage(usage: JsonObject): ChatUsage = ChatUsage(
        inputTokens = usage["input_tokens"]?.jsonPrimitive?.longOrNull ?: 0,
        outputTokens = usage["output_tokens"]?.jsonPrimitive?.longOrNull ?: 0,
        cacheReadTokens = usage["cache_read_input_tokens"]?.jsonPrimitive?.longOrNull ?: 0,
        cacheWriteTokens = usage["cache_creation_input_tokens"]?.jsonPrimitive?.longOrNull ?: 0,
    )

}
