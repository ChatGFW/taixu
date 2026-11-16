package top.wkbin.taixu.core.tools

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.OkHttpClient
import top.wkbin.taixu.core.network.withOpenCodeSessionHeaders

/** 单项探针结果：success + 可展示细节（失败原因或成功摘要）。 */
data class ConnectionProbeOutcome(val success: Boolean, val detail: String)

/** 三项连接测试结果：非流式补全 / 流式补全 / 工具调用，各自独立判定。 */
data class ConnectionThreeWayResult(
    val plain: ConnectionProbeOutcome,
    val stream: ConnectionProbeOutcome,
    val tool: ConnectionProbeOutcome,
)

/**
 * 三项连接探针（借鉴多厂商聚合客户端的连接测试面板）：在单项探活之上，
 * 分别独立验证「非流式补全」「流式补全（SSE 首事件）」「工具调用往返」——
 * 中转站/网关常见的半残形态（能聊天不能流、不支持 tools）只有分项探测才暴露。
 * 每项独立 runCatching，互不影响；实现按协议分支（OpenAI 兼容 / Anthropic Messages / Responses）。
 */
class AgentConnectionThreeWayProbe(private val http: OkHttpClient) {

    suspend fun test(
        baseUrl: String,
        model: String,
        apiKey: String?,
        useResponsesApi: Boolean = false,
        protocol: ProviderProtocol = ProviderProtocol.OPENAI,
        providerName: String? = null,
    ): ConnectionThreeWayResult = withContext(Dispatchers.IO) {
        val cleanBaseUrl = ProviderEndpointPolicy.normalizeUrl(baseUrl)
        require(cleanBaseUrl.isNotBlank() && ProviderEndpointPolicy.isSafeBaseUrl(cleanBaseUrl)) { "Base URL 不安全或为空" }
        val isAnthropic = protocol == ProviderProtocol.ANTHROPIC ||
            cleanBaseUrl.trimEnd('/').contains("api.anthropic.com") ||
            providerName?.contains("anthropic", ignoreCase = true) == true ||
            providerName?.contains("claude", ignoreCase = true) == true
        val tester = AgentModelConnectionTester(http)
        val plain = runCatching {
            tester.test(cleanBaseUrl, model, apiKey, useResponsesApi, protocol, providerName)
            ConnectionProbeOutcome(true, "非流式补全正常")
        }.getOrElse { ConnectionProbeOutcome(false, it.message ?: it::class.simpleName.orEmpty()) }

        val stream = runCatching {
            probeStream(cleanBaseUrl, model, apiKey, useResponsesApi, isAnthropic)
            ConnectionProbeOutcome(true, "流式响应正常")
        }.getOrElse { ConnectionProbeOutcome(false, it.message ?: it::class.simpleName.orEmpty()) }

        val tool = runCatching {
            probeTool(cleanBaseUrl, model, apiKey, useResponsesApi, isAnthropic)
            ConnectionProbeOutcome(true, "工具调用往返正常")
        }.getOrElse { ConnectionProbeOutcome(false, it.message ?: it::class.simpleName.orEmpty()) }

        ConnectionThreeWayResult(plain, stream, tool)
    }

    /** 流式探针：SSE 首批字节里必须出现 data:/event: 事件帧。 */
    private fun probeStream(baseUrl: String, model: String, apiKey: String?, useResponsesApi: Boolean, isAnthropic: Boolean) {
        val escaped = model.replace("\\", "\\\\").replace("\"", "\\\"")
        val (url, body) = when {
            useResponsesApi -> "${baseUrl.trimEnd('/')}/responses" to
                """{"model":"$escaped","input":[{"role":"user","content":[{"type":"input_text","text":"Say OK"}]}],"max_output_tokens":8,"stream":true}"""
            isAnthropic -> "${baseUrl.trimEnd('/')}/messages" to
                """{"model":"$escaped","max_tokens":16,"stream":true,"messages":[{"role":"user","content":"Say OK"}]}"""
            else -> "${baseUrl.trimEnd('/')}/chat/completions" to
                """{"model":"$escaped","messages":[{"role":"user","content":"Say OK"}],"max_tokens":8,"stream":true}"""
        }
        val request = baseRequest(url, apiKey, isAnthropic).post(body.toRequestBody("application/json".toMediaType())).build()
        http.newCall(request.withOpenCodeSessionHeaders()).execute().use { response ->
            check(response.isSuccessful) { "HTTP ${response.code}" }
            val head = response.body.source()
            var seen = StringBuilder()
            var lines = 0
            while (lines < 8) {
                val line = response.body.source().readUtf8Line() ?: break
                seen.append(line)
                lines++
                if (seen.contains("\"") && (line.startsWith("data:") || line.startsWith("event:"))) break
            }
            check(lines > 0 && (seen.contains("data:") || seen.contains("event:"))) {
                "未收到 SSE 事件帧：${seen.take(160)}"
            }
        }
    }

    /** 工具调用探针：强制指向探针函数，检查响应里出现结构化调用（tool_calls / tool_use / function_call）。 */
    private fun probeTool(baseUrl: String, model: String, apiKey: String?, useResponsesApi: Boolean, isAnthropic: Boolean) {
        val escaped = model.replace("\\", "\\\\").replace("\"", "\\\"")
        val (url, body) = when {
            useResponsesApi -> "${baseUrl.trimEnd('/')}/responses" to
                """{"model":"$escaped","input":[{"role":"user","content":"查询北京天气"}],"max_output_tokens":64,""" +
                """"tools":[{"type":"function","name":"get_weather","description":"查询天气","parameters":{"type":"object","properties":{"city":{"type":"string"}},"required":["city"]}}],"tool_choice":{"type":"function","name":"get_weather"}}"""
            isAnthropic -> "${baseUrl.trimEnd('/')}/messages" to
                """{"model":"$escaped","max_tokens":64,"messages":[{"role":"user","content":"查询北京天气"}],""" +
                """"tools":[{"name":"get_weather","description":"查询天气","input_schema":{"type":"object","properties":{"city":{"type":"string"}},"required":["city"]}}],"tool_choice":{"type":"tool","name":"get_weather"}}"""
            else -> "${baseUrl.trimEnd('/')}/chat/completions" to
                """{"model":"$escaped","messages":[{"role":"user","content":"查询北京天气"}],"max_tokens":64,""" +
                """"tools":[{"type":"function","function":{"name":"get_weather","description":"查询天气","parameters":{"type":"object","properties":{"city":{"type":"string"}},"required":["city"]}}}],"tool_choice":{"type":"function","function":{"name":"get_weather"}}}"""
        }
        val request = baseRequest(url, apiKey, isAnthropic).post(body.toRequestBody("application/json".toMediaType())).build()
        http.newCall(request.withOpenCodeSessionHeaders()).execute().use { response ->
            val text = response.body.string()
            check(response.isSuccessful) { "HTTP ${response.code}：${text.take(160)}" }
            // 探针只判定「结构化工具调用出现与否」，字符串标记即可（org.json 在 JVM 单测不可用）
            val called = when {
                useResponsesApi -> text.contains("function_call")
                isAnthropic -> text.contains("tool_use")
                else -> text.contains("tool_calls")
            }
            check(called) { "响应未包含结构化工具调用：${text.take(160)}" }
        }
    }

    private fun baseRequest(url: String, apiKey: String?, isAnthropic: Boolean) =
        okhttp3.Request.Builder().url(url)
            .header("Content-Type", "application/json")
            .apply {
                if (isAnthropic) {
                    header("anthropic-version", "2023-06-01")
                    if (!apiKey.isNullOrBlank()) header("x-api-key", apiKey)
                } else if (!apiKey.isNullOrBlank()) {
                    header("Authorization", "Bearer $apiKey")
                }
            }
}
