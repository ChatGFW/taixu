package top.wkbin.taixu.harness

import java.util.UUID
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import top.wkbin.taixu.harness.bot.TaskBudgetContext
import top.wkbin.taixu.harness.core.LlmApi

/** All requests are redirected to a local fixture before DNS or socket creation. */
@RunWith(Parameterized::class)
class OpenCodeSessionHeadersTest(private val api: LlmApi) {
    private lateinit var server: MockWebServer
    private lateinit var client: OkHttpClient
    private lateinit var transport: ProviderTransport
    private val messages = listOf(ApiMessage("user", "hi"))
    private val json = Json { ignoreUnknownKeys = true }

    @Before fun setUp() {
        server = MockWebServer().apply { start() }
        client = OkHttpClient.Builder().addInterceptor { chain ->
            val request = chain.request()
            chain.proceed(request.newBuilder().url(server.url(request.url.encodedPath)).build())
        }.build()
        transport = ProviderTransport(client, json)
    }

    @After fun tearDown() { server.shutdown() }

    private fun model(headers: String = "") = ModelConfig(
        name = "OpenCode", provider = "custom", model = "fixture", apiKey = "fake-key",
        baseUrl = "https://opencode.ai/zen/go/v1",
        protocol = if (api == LlmApi.ANTHROPIC_MESSAGES) ApiProtocol.ANTHROPIC else ApiProtocol.OPENAI,
        responseApiEnabled = api == LlmApi.OPENAI_RESPONSES,
        customHeaders = headers,
    )

    private fun enqueueReply() = server.enqueue(MockResponse().setBody(when (api) {
        LlmApi.OPENAI_COMPLETIONS -> """{"choices":[{"message":{"role":"assistant","content":"ok"}}]}"""
        LlmApi.OPENAI_RESPONSES -> """{"id":"resp_1","output":[{"type":"message","role":"assistant","content":[{"type":"output_text","text":"ok"}]}]}"""
        LlmApi.ANTHROPIC_MESSAGES -> """{"content":[{"type":"text","text":"ok"}]}"""
    }))

    private fun enqueueStream() {
        val events = when (api) {
            LlmApi.OPENAI_COMPLETIONS -> listOf(
                """{"choices":[{"delta":{"content":"ok"}}]}""", "[DONE]",
            )
            LlmApi.OPENAI_RESPONSES -> listOf(
                """{"type":"response.output_text.delta","item_id":"msg_1","output_index":0,"delta":"ok"}""",
                """{"type":"response.completed","response":{"id":"resp_1"}}""",
            )
            LlmApi.ANTHROPIC_MESSAGES -> listOf(
                """{"type":"content_block_delta","index":0,"delta":{"type":"text_delta","text":"ok"}}""",
                """{"type":"message_stop"}""",
            )
        }
        server.enqueue(MockResponse().setHeader("Content-Type", "text/event-stream")
            .setBody(events.joinToString("\n\n", postfix = "\n\n") { "data: $it" }))
    }

    private fun takeId(): String = requireNotNull(server.takeRequest().getHeader("x-opencode-session"))
        .also { assertEquals(it, UUID.fromString(it).toString()) }

    @Test fun `conversation survives turns streaming and transport recreation`() = runBlocking {
        enqueueReply()
        transport.chat(model(), messages, sessionKey = "session-one")
        val first = takeId()
        enqueueStream()
        assertEquals("ok", transport.chatStream(model(), messages, sessionKey = "session-one") {}.content)
        assertEquals(first, takeId())
        enqueueReply()
        ProviderTransport(client, json).chat(model(), messages, sessionKey = "session-one")
        assertEquals(first, takeId())
        enqueueReply()
        transport.chat(model(), messages, sessionKey = "session-two")
        assertNotEquals(first, takeId())
    }

    @Test fun `parallel conversations keep independent headers`() = runBlocking {
        enqueueReply()
        enqueueReply()
        val one = async { transport.chat(model(), messages, sessionKey = "one") }
        val two = async { transport.chat(model(), messages, sessionKey = "two") }
        one.await()
        two.await()
        assertNotEquals(takeId(), takeId())
    }

    @Test fun `key rotation keeps one session even for unbound calls`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(429).setBody("rate limited"))
        enqueueReply()
        transport.chat(model().copy(apiKeys = listOf("key-one", "key-two")), messages)
        assertEquals(takeId(), takeId())
    }

    @Test fun `unbound calls get independent uuids`() = runBlocking {
        enqueueReply()
        transport.chat(model(), messages)
        val first = takeId()
        enqueueReply()
        transport.chat(model(), messages, sessionKey = " ")
        assertNotEquals(first, takeId())
    }

    @Test fun `auxiliary calls inherit conversation and explicit key takes precedence`() = runBlocking {
        enqueueReply()
        transport.chat(model(), messages, sessionKey = "parent")
        val parent = takeId()
        withContext(TaskBudgetContext("parent")) {
            enqueueReply()
            transport.chat(model(), messages)
            assertEquals(parent, takeId())
            enqueueReply()
            transport.chat(model(), messages, sessionKey = "child")
            assertNotEquals(parent, takeId())
        }
    }

    @Test fun `static session header becomes conversation scoped and custom headers survive`() = runBlocking {
        val config = model("X-OpenCode-Session: shared-fixed-value\nx-opencode-session: duplicate\nX-Gateway: keep\nUser-Agent: MyTaiXu/2")
        enqueueReply()
        transport.chat(config, messages, sessionKey = "one")
        val request = server.takeRequest()
        assertEquals(1, request.headers.values("x-opencode-session").size)
        assertNotEquals("shared-fixed-value", request.getHeader("x-opencode-session"))
        assertEquals("keep", request.getHeader("X-Gateway"))
        assertEquals("MyTaiXu/2", request.getHeader("User-Agent"))
        assertEquals("X-OpenCode-Session: shared-fixed-value\nx-opencode-session: duplicate\nX-Gateway: keep\nUser-Agent: MyTaiXu/2", config.customHeaders)
    }

    @Test fun `official endpoint identifies TaiXu and supports inference urls`() = runBlocking {
        enqueueReply()
        transport.chat(model().copy(baseUrl = "https://opencode.ai/inference/go/openai/v1"), messages, "one")
        val request = server.takeRequest()
        assertNotNull(request.getHeader("x-opencode-session"))
        assertEquals("TaiXu", request.getHeader("User-Agent"))
    }

    @Test fun `proxy with configured session header also gets conversation scoping`() = runBlocking {
        enqueueReply()
        transport.chat(model("x-opencode-session: old-value").copy(baseUrl = "https://proxy.example/v1"), messages, "one")
        assertNotEquals("old-value", takeId())
    }

    @Test fun `unrelated endpoints do not receive session header`() = runBlocking {
        for (endpoint in listOf("https://other.example/v1", "https://opencode.ai.other.example/v1", "https://other.example/opencode.ai")) {
            enqueueReply()
            transport.chat(model("X-Gateway: keep").copy(baseUrl = endpoint), messages, "one")
            val request = server.takeRequest()
            assertNull(request.getHeader("x-opencode-session"))
            assertEquals("keep", request.getHeader("X-Gateway"))
        }
    }

    companion object {
        @JvmStatic @Parameterized.Parameters(name = "{0}")
        fun protocols(): List<Array<Any>> = LlmApi.entries.map { arrayOf<Any>(it) }
    }
}
