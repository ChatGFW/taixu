package top.wkbin.taixu.harness

import kotlinx.serialization.json.*

internal class ChatCompletionsStreamDecoder(
    private val json: Json,
    private val inputTokens: Int,
    onReasoning: (String) -> Unit,
    private val onToolProgress: (ToolCallStreamProgress) -> Unit,
    onDelta: (String) -> Unit,
) : ProviderStreamDecoder {
    // 单轮流式输出硬失败预算：正文/思考经 demuxer 计入，工具入参在累积点计入。
    private val budget = StreamBudget()
    private val demuxer = ThinkTagStreamDemuxer(onReasoning, onDelta, budget = budget)
    private val toolCalls = mutableMapOf<Int, ToolCallAccumulator>()
    // 网关不分发 index 时的流式调用归并状态：id → 槽位、下一个可用的合成槽、
    // 最近活跃槽（承接无 id 的 arguments 延续分片）
    private val slotByCallId = mutableMapOf<String, Int>()
    private var nextSyntheticSlot = 0
    private var lastActiveSlot: Int? = null
    private var usage = ChatUsage()
    private var completed = false
    private var finishReason: String? = null
    private var result: ChatResult? = null

    override fun accept(data: String): ProviderDecodeStatus {
        check(result == null) { "Stream already finalized" }
        if (data == "[DONE]") { completed = true; return ProviderDecodeStatus(true, true) }
        val root = runCatching { json.parseToJsonElement(data) as? JsonObject }.getOrNull()
        if (root?.get("error") is JsonObject) throw providerStreamError(data)
        // usage 块位于 chunk 顶层（stream_options.include_usage 时由最后一个 chunk 携带；
        // DeepSeek/OpenRouter 等默认就会发）。后面的块覆盖前面的，保留最终值。
        (root?.get("usage") as? JsonObject)?.let { block ->
            parseUsageBlock(block)?.let { parsed -> usage = parsed }
        }
        val choice = root
            ?.get("choices")?.let { it as? JsonArray }?.firstOrNull() as? JsonObject
            ?: return ProviderDecodeStatus(root != null)
        choice["finish_reason"]?.jsonPrimitive?.contentOrNull?.let {
            finishReason = it
            completed = true
        }
        val delta = choice["delta"] as? JsonObject
        delta?.get("content")?.let { it as? JsonPrimitive }?.contentOrNull?.let { chunk ->
            if (chunk.isNotEmpty()) {
                demuxer.onContentChunk(chunk)
            }
        }
        // 推理增量：兼容 reasoning_content（DeepSeek/GLM 等）与 reasoning（OpenRouter 等网关）两种字段名
        val reasoningChunk = (delta?.get("reasoning_content") ?: delta?.get("reasoning"))
            ?.let { it as? JsonPrimitive }?.contentOrNull
        reasoningChunk?.let { chunk ->
            if (chunk.isNotEmpty()) {
                demuxer.onExplicitReasoningChunk(chunk)
            }
        }
        // 调用边界判据：显式 index 优先；网关不分发 index 时以「id 变化」识别新调用
        // ——tool call 首个分片必带非空 id，后续 arguments 分片不带 id。若按元素
        // 迭代序号 fallback（每个 chunk 恒为 0），跨 chunk 的多个并行调用会全部
        // 落进同一槽位：id 相互覆盖、arguments 拼成非法 JSON、其中一个调用静默丢失。
        delta?.get("tool_calls")?.let { it as? JsonArray }?.forEach { call2 ->
            val callObj = call2 as? JsonObject ?: return@forEach
            val explicitIndex = callObj["index"]?.let { it as? JsonPrimitive }?.contentOrNull?.toIntOrNull()
            val callId = callObj["id"]?.let { it as? JsonPrimitive }?.contentOrNull
                ?.takeIf { it.isNotEmpty() }
            val slot: Int = when {
                explicitIndex != null -> explicitIndex
                callId != null -> slotByCallId[callId] ?: run {
                    var candidate = nextSyntheticSlot++
                    while (candidate in toolCalls) candidate = nextSyntheticSlot++
                    slotByCallId[callId] = candidate
                    candidate
                }
                // 无 index 无 id 的分片：延续最近活跃调用（arguments 增量）
                lastActiveSlot != null -> lastActiveSlot!!
                else -> {
                    var candidate = nextSyntheticSlot++
                    while (candidate in toolCalls) candidate = nextSyntheticSlot++
                    candidate
                }
            }
            lastActiveSlot = slot
            val accum = toolCalls.getOrPut(slot) { ToolCallAccumulator() }
            callId?.let {
                accum.id = it
                slotByCallId.putIfAbsent(callId, slot)
            }
            val function = callObj["function"] as? JsonObject
            function?.get("name")?.let { it as? JsonPrimitive }?.contentOrNull
                ?.takeIf { it.isNotEmpty() }?.let { accum.name = it }
            function?.get("arguments")?.let { it as? JsonPrimitive }?.contentOrNull
                ?.let { budget.add(it); accum.arguments.append(it) }
            accum.publishProgress(onToolProgress)
        }
        return ProviderDecodeStatus(true)
    }

    override fun finish(): ChatResult {
        result?.let { return it }
        requireStreamCompletion(completed, "Chat Completions")
        requireOutputCompletion(finishReason, inputTokens, demuxer.fullText.length + demuxer.fullReasoning.length)
        demuxer.flush()
        toolCalls.values.forEach { it.publishProgress(onToolProgress, force = true) }
        // 部分 OpenAI 兼容端对无参数函数不下发 arguments 分片，空串须兜底为 "{}"
        val calls = toolCalls.values.map {
            ApiToolCallSpec(
                it.id.ifBlank { ToolCallIdNormalizer.normalize(null) },
                it.name,
                it.arguments.toString().ifBlank { "{}" },
            )
    }
    return ChatResult(
        content = demuxer.fullText.toString().ifEmpty { null },
        toolCalls = calls,
        reasoningContent = demuxer.fullReasoning.toString().ifEmpty { null },
        usage = usage,
    ) .also { result = it }
    }

    /** 手工解析流式 chunk 顶层 usage（各 Provider 字段不统一，DTO 反而脆）。 */
    private fun parseUsageBlock(block: JsonObject): ChatUsage? {
        val input = block["prompt_tokens"]?.let { it as? JsonPrimitive }?.contentOrNull?.toLongOrNull() ?: return null
        return ChatUsage(
            inputTokens = input,
            outputTokens = block["completion_tokens"]?.let { it as? JsonPrimitive }?.contentOrNull?.toLongOrNull() ?: 0,
            reasoningTokens = (block["completion_tokens_details"] as? JsonObject)
                ?.get("reasoning_tokens")?.let { it as? JsonPrimitive }?.contentOrNull?.toLongOrNull() ?: 0,
            cacheReadTokens = (block["prompt_tokens_details"] as? JsonObject)
                ?.get("cached_tokens")?.let { it as? JsonPrimitive }?.contentOrNull?.toLongOrNull()
                ?: block["prompt_cache_hit_tokens"]?.let { it as? JsonPrimitive }?.contentOrNull?.toLongOrNull() ?: 0,
            cacheWriteTokens = block["prompt_cache_miss_tokens"]?.let { it as? JsonPrimitive }?.contentOrNull?.toLongOrNull() ?: 0,
        )
    }

}
