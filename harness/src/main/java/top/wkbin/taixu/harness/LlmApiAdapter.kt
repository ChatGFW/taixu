package top.wkbin.taixu.harness

import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import top.wkbin.taixu.harness.core.LlmApi

/** Shared wire contract. Authentication and provider-specific fields stay in each adapter. */
internal interface LlmApiAdapter {
    val api: LlmApi

    suspend fun chat(model: ModelConfig, messages: List<ApiMessage>): ChatResult

    suspend fun chatStream(
        model: ModelConfig,
        messages: List<ApiMessage>,
        onReasoning: (String) -> Unit = {},
        onToolProgress: (ToolCallStreamProgress) -> Unit = {},
        onDelta: (String) -> Unit,
    ): ChatResult
}

/** Immutable registry: no silent fallback and no ambiguous protocol registration. */
internal class LlmApiRegistry(adapters: List<LlmApiAdapter>) {
    private val byApi = adapters.associateBy { it.api }

    init {
        require(byApi.size == adapters.size) { "Duplicate LLM API adapter" }
        require(byApi.keys == LlmApi.entries.toSet()) { "Missing LLM API adapter" }
    }

    fun forModel(model: ModelConfig): LlmApiAdapter = byApi.getValue(model.api)

    companion object {
        fun builtIn(client: OkHttpClient, json: Json): LlmApiRegistry = LlmApiRegistry(
            listOf(ChatApi(client, json), ResponsesApi(client, json), AnthropicApi(client, json)),
        )
    }
}
