package top.wkbin.taixu.harness

import java.io.EOFException
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

/** Direct protocol replay: no HTTP stack or API key is needed. Wire framing is tested separately. */
class ProviderStreamDecoderTest {
    private val json = Json { ignoreUnknownKeys = true }
    private val model = ModelConfig("test", "test", "offline", "https://invalid.example", null)

    private fun decoder(protocol: String, text: StringBuilder = StringBuilder(), reasoning: StringBuilder = StringBuilder()): ProviderStreamDecoder =
        when (protocol) {
            "chat" -> ChatCompletionsStreamDecoder(json, 0, { reasoning.append(it) }, {}, { text.append(it) })
            "anthropic" -> AnthropicStreamDecoder(json, 0, { reasoning.append(it) }, {}, { text.append(it) }, StreamBudget())
            else -> ResponsesStreamDecoder(json, model, 0, { reasoning.append(it) }, {}, { text.append(it) })
        }

    private fun events(protocol: String) = requireNotNull(javaClass.getResourceAsStream("/sse/$protocol-interleaved.sse"))
        .bufferedReader().use { reader -> reader.readLines().filter { it.startsWith("data:") }.map { it.removePrefix("data:").trim() } }

    @Test fun `three protocols preserve fixture semantics and finish only once`() {
        for (protocol in listOf("chat", "anthropic", "responses")) {
            val text = StringBuilder()
            val reasoning = StringBuilder()
            val decoder = decoder(protocol, text, reasoning)
            events(protocol).forEach { decoder.accept(it) }
            val result = decoder.finish()
            assertEquals(protocol, "你好", result.content)
            assertEquals("你好", text.toString())
            assertEquals("思考", reasoning.toString())
            assertEquals("思考", result.reasoningContent)
            val calls = result.toolCalls.associateBy { it.id }
            assertEquals(setOf("c1", "c2"), calls.keys)
            assertEquals("read", calls.getValue("c1").name)
            assertEquals("""{"path":"你好.txt"}""", calls.getValue("c1").argumentsJson)
            assertEquals("""{"command":"pwd"}""", calls.getValue("c2").argumentsJson)
            assertSame(result, decoder.finish())
            assertEquals("你好", text.toString())
        }
    }

    @Test fun `partial EOF cannot return tool calls and independent streams cannot share completion`() {
        for (protocol in listOf("chat", "anthropic", "responses")) {
            val incomplete = decoder(protocol)
            events(protocol).dropLast(1).forEach { incomplete.accept(it) }
            try { incomplete.finish(); fail("$protocol accepted EOF") } catch (_: EOFException) { }
            val complete = decoder(protocol)
            events(protocol).forEach { complete.accept(it) }
            complete.finish()
            try { decoder(protocol).finish(); fail("$protocol shared completion") } catch (_: EOFException) { }
        }
    }

    @Test fun `malformed payload does not count as first protocol event`() {
        for (protocol in listOf("chat", "anthropic", "responses")) {
            assertFalse(decoder(protocol).accept("not json").recognized)
        }
    }
}
