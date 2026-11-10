package top.wkbin.taixu.harness

import top.wkbin.taixu.harness.core.LlmApi
import java.io.IOException
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.InternalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
/**
 * OpenAI Responses API（POST /responses）适配层：把内部统一的 OpenAI 风格 [ApiMessage] 列表
 * 翻译成 Responses API 的 `input` items 结构，并把响应（含 SSE 流式）映射回 [ChatResult]。
 *
 * 与 [ChatApi]（chat/completions）的主要协议差异：
 * - 系统提示词放在顶层 `instructions`，不在 messages/input 里；
 * - 历史消息用 `input` items 表示：`{role:user|assistant, content:[...]}`、
 *   `{type:"function_call", call_id, name, arguments}`、
 *   `{type:"function_call_output", call_id, output}`；
 * - 工具定义为扁平结构 `{type:"function", name, description, parameters}`；
 * - 流式事件为 response.* 事件族：`response.output_text.delta`（正文增量）、
 *   `response.reasoning_text.delta` / `response.reasoning_summary_text.delta`（推理增量）、
 *   `response.output_item.added` / `response.function_call_arguments.delta`（工具调用分片）、
 *   `response.completed`（最终 usage 与完整输出）。
 */
internal class ResponsesApi(
    private val okHttpClient: OkHttpClient,
    private val json: Json,
) : LlmApiAdapter {
    override val api = LlmApi.OPENAI_RESPONSES
    @OptIn(InternalCoroutinesApi::class)
    override suspend fun chat(model: ModelConfig, messages: List<ApiMessage>): ChatResult =
        withContext(Dispatchers.IO) {
            val call = okHttpClient.newCall(buildResponsesRequest(model, messages, stream = false))
            // 与流式路径一致：取消时立即关闭 socket，避免"停止"后阻塞到读超时
            val cancelHandle = coroutineContext[Job]?.invokeOnCompletion(onCancelling = true) { call.cancel() }
            try {
                call.execute().use { response ->
                    val body = response.body.string()
                    if (!response.isSuccessful) {
                        if (response.code == 429) {
                            throw ProviderClient.rateLimitException(response.code, body, response.header("Retry-After"))
                        }
                        if (response.code in 500..599) {
                            throw ProviderClient.transientHttpException(response.code, body, response.header("Retry-After"))
                        }
                        throw httpError(response.code, body)
                    }
                    parseResponsesFinalResponse(json, body, model)
                }
            } finally {
                cancelHandle?.dispose()
            }
        }

    @OptIn(InternalCoroutinesApi::class)
    override suspend fun chatStream(
        model: ModelConfig,
        messages: List<ApiMessage>,
        onReasoning: (String) -> Unit,
        onToolProgress: (ToolCallStreamProgress) -> Unit,
        onDelta: (String) -> Unit,
    ): ChatResult = withContext(Dispatchers.IO) {
        val call = okHttpClient.newCall(buildResponsesRequest(model, messages, stream = true))
        // 与 ChatApi 一致：取消时立即关闭 socket，保证"停止"秒级生效
        val cancelHandle = coroutineContext[Job]?.invokeOnCompletion(onCancelling = true) { call.cancel() }
        val firstEventTimeoutMs = firstEventTimeoutMs(messages, model)
        val firstEventState = AtomicInteger(ProviderClient.FIRST_EVENT_WAITING)
        val firstEventWatchdog = launch { awaitFirstEventTimeout(firstEventTimeoutMs, firstEventState) { call.cancel() } }
        try {
            call.execute().use { response ->
                if (!response.isSuccessful) {
                    val rawBody = response.body.string().take(512)
                    if (response.code == 429) {
                        throw ProviderClient.rateLimitException(response.code, rawBody, response.header("Retry-After"))
                    }
                    if (response.code in 500..599) {
                        throw ProviderClient.transientHttpException(response.code, rawBody, response.header("Retry-After"))
                    }
                    throw httpError(response.code, rawBody)
                }
                val source = response.body.source()
                val decoder = ResponsesStreamDecoder(json, model, ProviderClient.estimateApiMessageTokens(messages),
                    onReasoning, onToolProgress, onDelta)
                while (true) {
                    val line = source.readUtf8Line() ?: break
                    observeStreamActivity(line, firstEventState, firstEventWatchdog)
                    if (!line.startsWith("data:")) continue
                    val status = decoder.accept(line.removePrefix("data:").trim())
                    if (status.recognized && firstEventState.compareAndSet(
                            ProviderClient.FIRST_EVENT_WAITING, ProviderClient.FIRST_EVENT_RECEIVED)) firstEventWatchdog.cancel()
                    if (status.terminal) break
                }
                decoder.finish()
            }
        } catch (io: IOException) {
            throw streamFailure(io, firstEventState.get(), firstEventTimeoutMs, okHttpClient.readTimeoutMillis.toLong())
        } finally {
            firstEventWatchdog.cancel()
            cancelHandle?.dispose()
        }
    }

    /** 非 429/5xx 的 HTTP 错误：413 与上下文超限文案走可自愈类型，供引擎紧急压缩后重试。 */
    private fun httpError(code: Int, body: String): Exception {
        val detail = extractError(body)
        val message = "Responses 请求失败 HTTP $code：$detail"
        return when (ProviderClient.contextOverflowException(code, body)) {
            is LlmContextOverflowException -> LlmContextOverflowException(message)
            is LlmInvalidOutputTokensException -> LlmInvalidOutputTokensException(message)
            else -> IllegalStateException(message)
        }
    }

    private fun extractError(body: String): String {
        val message = runCatching {
            val obj = json.parseToJsonElement(body) as? JsonObject
            val error = (obj?.get("error") as? JsonObject)
                ?: (obj?.get("response") as? JsonObject)?.get("error") as? JsonObject
            error?.get("message")?.jsonPrimitive?.contentOrNull
        }.getOrNull()
        return message ?: body.take(512)
    }

}
