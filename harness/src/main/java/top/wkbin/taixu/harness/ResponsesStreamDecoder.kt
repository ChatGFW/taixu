package top.wkbin.taixu.harness

import kotlinx.serialization.json.*

internal class ResponsesStreamDecoder(
    private val json: Json,
    private val model: ModelConfig,
    private val inputTokens: Int,
    onReasoning: (String) -> Unit,
    private val onToolProgress: (ToolCallStreamProgress) -> Unit,
    onDelta: (String) -> Unit,
) : ProviderStreamDecoder {
    // 单轮流式输出硬失败预算：正文/思考经 demuxer 计入，工具入参在累积点计入。
    private val budget = StreamBudget()
    private val demuxer = ThinkTagStreamDemuxer(onReasoning, onDelta, budget = budget)
    private val receivedText = StringBuilder()
    // item_id -> 工具调用累积器（Responses 以 item_id 区分同一轮多个 function_call）
    private val toolCalls = mutableMapOf<String, ToolCallAccumulator>()
    private var usage = ChatUsage()
    private val completedItems = sortedMapOf<Int, JsonObject>()
    private var nativeOutput: JsonArray? = null
    private var completed = false
    private var result: ChatResult? = null

    override fun accept(data: String): ProviderDecodeStatus {
        check(result == null) { "Stream already finalized" }
        val event = runCatching { json.parseToJsonElement(data) as? JsonObject }.getOrNull()
            ?: return ProviderDecodeStatus(false)
        when (event["type"]?.jsonPrimitive?.contentOrNull) {
            // 正文增量
            "response.output_text.delta" -> {
                event["delta"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotEmpty() }
                    ?.let { receivedText.append(it); demuxer.onContentChunk(it) }
            }
            // 推理增量：detail 推理与 summary 摘要都作为推理内容回传
            "response.reasoning_text.delta", "response.reasoning_summary_text.delta" -> {
                event["delta"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotEmpty() }
                    ?.let { demuxer.onExplicitReasoningChunk(it) }
            }
            // function_call item 开始：携带 call_id 与 name
            "response.output_item.added" -> {
                val item = event["item"] as? JsonObject ?: return ProviderDecodeStatus(true)
                if (item["type"]?.jsonPrimitive?.contentOrNull == "function_call") {
                    val itemId = item["id"]?.jsonPrimitive?.contentOrNull.orEmpty()
                    val accum = toolCalls.getOrPut(itemId) { ToolCallAccumulator() }
                    accum.id = item["call_id"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }
                        ?: itemId
                    accum.name = item["name"]?.jsonPrimitive?.contentOrNull.orEmpty()
                    accum.publishProgress(onToolProgress)
                }
            }
            // function_call 参数增量分片
            "response.function_call_arguments.delta" -> {
                val itemId = event["item_id"]?.jsonPrimitive?.contentOrNull.orEmpty()
                val chunk = event["delta"]?.jsonPrimitive?.contentOrNull
                if (!chunk.isNullOrEmpty()) {
                    val accum = toolCalls.getOrPut(itemId) { ToolCallAccumulator() }
                    budget.add(chunk)
                    accum.arguments.append(chunk)
                    accum.publishProgress(onToolProgress)
                }
            }
            // 兼容部分网关不下发 arguments.delta、只在 item.done 里带完整参数的情况
            "response.output_item.done" -> {
                val item = event["item"] as? JsonObject ?: return ProviderDecodeStatus(true)
                (event["output_index"]?.jsonPrimitive?.contentOrNull?.toIntOrNull())?.let { completedItems[it] = item }
                if (item["type"]?.jsonPrimitive?.contentOrNull == "function_call") {
                    val itemId = item["id"]?.jsonPrimitive?.contentOrNull.orEmpty()
                    val accum = toolCalls.getOrPut(itemId) { ToolCallAccumulator() }
                    if (accum.id.isBlank()) {
                        accum.id = item["call_id"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }
                            ?: itemId
                    }
                    if (accum.name.isBlank()) accum.name = item["name"]?.jsonPrimitive?.contentOrNull.orEmpty()
                    val args = item["arguments"]?.jsonPrimitive?.contentOrNull
                    if (args != null && accum.arguments.isEmpty() && args.isNotBlank()) {
                        budget.add(args)
                        accum.arguments.append(args)
                    }
                    accum.publishProgress(onToolProgress)
                }
            }
            // 结束：携带最终 usage（与完整 output）
            "response.completed" -> {
                val finalResponse = event["response"] as? JsonObject
                completed = finalResponse?.get("status")?.jsonPrimitive?.contentOrNull in listOf(null, "completed")
                nativeOutput = finalResponse?.get("output") as? JsonArray
                if (nativeOutput == null && completedItems.isNotEmpty() &&
                    completedItems.keys.toList() == completedItems.keys.indices.toList()
                ) nativeOutput = JsonArray(completedItems.values.toList())
                ((event["response"] as? JsonObject)?.get("usage") as? JsonObject)
                    ?.let { usage = parseResponsesUsage(it) }
                return ProviderDecodeStatus(true, true)
            }
            "response.failed" -> {
                val error = (event["response"] as? JsonObject)?.get("error") as? JsonObject
                val message = error?.get("message")?.jsonPrimitive?.contentOrNull ?: "响应处理失败"
                throw providerStreamError(error?.toString() ?: message)
            }
            "response.incomplete" -> {
                val incompleteResponse = event["response"] as? JsonObject
                val reason = (incompleteResponse?.get("incomplete_details") as? JsonObject)
                    ?.get("reason")?.jsonPrimitive?.contentOrNull
                requireOutputCompletion(reason, inputTokens, receivedText.length + demuxer.fullReasoning.length)
                throw IllegalStateException("Responses 响应未完成：${reason ?: "未知原因"}")
            }
            "error" -> {
                throw providerStreamError(event.toString())
            }
            else -> Unit // response.created / in_progress / content_part.* 等无需处理
        }

        return ProviderDecodeStatus(true)
    }

    override fun finish(): ChatResult {
        result?.let { return it }
        requireStreamCompletion(completed, "Responses")
        val finalResult = nativeOutput?.takeIf { completed && it.isNotEmpty() }?.let {
            parseResponsesFinalResponse(json, buildJsonObject { put("output", it) }.toString(), model)
        }
        // Some gateways provide only completed output, or omit the last text delta.
        val emittedText = receivedText.toString()
        finalResult?.content?.takeIf { it.startsWith(emittedText) && it.length > emittedText.length }
            ?.let { demuxer.onContentChunk(it.substring(emittedText.length)) }
        demuxer.flush()
        toolCalls.values.forEach { it.publishProgress(onToolProgress, force = true) }
        // 无参数函数可能不下发 arguments 分片，空串兜底为 "{}"
        val calls = toolCalls.values.map {
            ApiToolCallSpec(it.id, it.name, it.arguments.toString().ifBlank { "{}" })
        }
        return ChatResult(
            content = demuxer.fullText.toString().ifEmpty { null },
            toolCalls = finalResult?.toolCalls ?: calls,
            reasoningContent = demuxer.fullReasoning.toString().ifEmpty { finalResult?.reasoningContent },
            usage = usage,
            responsesTurn = if (completed) ResponsesTurn.capture(model, nativeOutput) else null,
        ).also { result = it }
    }

}
