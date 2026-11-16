package top.wkbin.taixu.core.tools

import java.util.UUID
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@RunWith(Parameterized::class)
class OpenCodeConnectionHeadersTest(private val protocol: ProviderProtocol, private val responses: Boolean) {
    private lateinit var server: MockWebServer
    private lateinit var client: OkHttpClient
    private val endpoint = "https://opencode.ai/zen/go/v1"

    @Before fun setUp() {
        server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                if (request.getHeader("x-opencode-session").isNullOrBlank()) {
                    return MockResponse().setResponseCode(400).setBody("MissingSessionID")
                }
                return if (request.body.readUtf8().contains("\"stream\":true")) {
                    MockResponse().setBody("data: {\"text\":\"OK\"}\n\n")
                } else {
                    MockResponse().setBody("""{"content":[{"type":"tool_use"}],"output":[{"type":"function_call"}],"choices":[{"message":{"content":"OK","tool_calls":[]}}]}""")
                }
            }
        }
        server.start()
        client = OkHttpClient.Builder().addInterceptor { chain ->
            val request = chain.request()
            chain.proceed(request.newBuilder().url(server.url(request.url.encodedPath)).build())
        }.build()
    }

    @After fun tearDown() { server.shutdown() }

    private fun takeId(): String {
        val request = server.takeRequest()
        assertEquals("TaiXu", request.getHeader("User-Agent"))
        val id = requireNotNull(request.getHeader("x-opencode-session"))
        assertEquals(id, UUID.fromString(id).toString())
        return id
    }

    @Test fun `connection check and catalog receive their own session id`() = runTest {
        val tester = AgentModelConnectionTester(client)
        tester.test(endpoint, "fixture", "fake-key", useResponsesApi = responses, protocol = protocol)
        val first = takeId()
        tester.test(endpoint, "fixture", "fake-key", useResponsesApi = responses, protocol = protocol)
        assertNotEquals(first, takeId())
        tester.test(endpoint, "", "fake-key", useResponsesApi = responses, protocol = protocol)
        assertNotEquals(first, takeId())
    }

    @Test fun `all three settings probes include OpenCode headers`() = runTest {
        val result = AgentConnectionThreeWayProbe(client)
            .test(endpoint, "fixture", "fake-key", useResponsesApi = responses, protocol = protocol)
        assertTrue(result.plain.detail, result.plain.success)
        assertTrue(result.stream.detail, result.stream.success)
        assertTrue(result.tool.detail, result.tool.success)
        repeat(3) { takeId() }
    }

    companion object {
        @JvmStatic @Parameterized.Parameters(name = "{0}, responses={1}")
        fun protocols(): List<Array<Any>> = listOf(
            arrayOf(ProviderProtocol.OPENAI, false),
            arrayOf(ProviderProtocol.OPENAI, true),
            arrayOf(ProviderProtocol.ANTHROPIC, false),
        )
    }
}
