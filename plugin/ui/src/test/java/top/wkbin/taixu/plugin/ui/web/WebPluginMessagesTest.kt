package top.wkbin.taixu.plugin.ui.web

import kotlinx.coroutines.async
import org.junit.Assert.*
import org.junit.Test
import top.wkbin.taixu.plugin.runtime.PluginCapabilityResult

class WebPluginMessagesTest {
    @Test fun decodesOnlyStructuredActionAndTarget() {
        val call = WebPluginMessages.decode("""{"id":"a-1","action":"host.app-info","target":"application"}""")
        assertEquals("a-1", call.id)
        assertEquals("host.app-info", call.capability.action)
    }
    @Test fun rejectsForgedAuthorizationIdentityNestedAndOversizedMessages() {
        listOf(
            """{"id":"a","action":"host.app-info","target":"application","approved":true}""",
            """{"id":"a","action":"host.app-info","target":"application","pluginId":"taixu.browser"}""",
            """{"id":"a","action":"host.app-info","target":"application","environment":"ssh"}""",
            """{"id":1,"action":"host.app-info","target":"application"}""",
            """{"id":"a","action":"shell","target":{"command":"id"}}""",
            """{"id":"a","action":"read","target":"first","target":"second"}""",
            """{"id":"a","action":"shell"}""", "[]", "{".repeat(4096),
        ).forEach { input ->
            assertTrue(input, runCatching { WebPluginMessages.decode(input) }.isFailure)
        }
    }
    @Test fun outputEscapesPluginTextWithoutJavaScriptEvaluation() {
        val text = "</script>\"\\\n"
        val encoded = WebPluginMessages.encode(PluginCapabilityResult("id", true, text))
        assertEquals(text, kotlinx.serialization.json.Json.parseToJsonElement(encoded)
            .let { it as kotlinx.serialization.json.JsonObject }.getValue("text")
            .let { it as kotlinx.serialization.json.JsonPrimitive }.content)
    }

    @Test fun punctuationInScalarTargetsDoesNotBecomeNestedData() {
        val call = WebPluginMessages.decode("""{"id":"a","action":"read","target":"project:{[example]}"}""")
        assertEquals("project:{[example]}", call.capability.target)
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    @Test fun messageToNativePermissionToStructuredReplyExecutesExactlyOnce() = kotlinx.coroutines.test.runTest {
        val call = WebPluginMessages.decode("""{"id":"a-1","action":"host.app-info","target":"application"}""")
        val identity = top.wkbin.taixu.plugin.runtime.PluginCapabilityIdentity(
            "example.web", "1.0.0", 1, "https://example.plugin.taixu.invalid", "android:local",
        )
        var executions = 0
        val session = top.wkbin.taixu.plugin.runtime.PluginCapabilitySession(identity, mapOf(call.capability to { executions++; "app-info" }))
        val result = async { session.call(call, identity.origin, true) }
        testScheduler.runCurrent()
        assertEquals(0, executions)
        val prompt = requireNotNull(session.prompt.value)
        session.decide(prompt.token, top.wkbin.taixu.plugin.runtime.PluginGrantChoice.ONCE)
        val reply = WebPluginMessages.encode(result.await())
        assertEquals("""{"id":"a-1","success":true,"text":"app-info"}""", reply)
        assertEquals(1, executions)
        assertFalse(session.call(call, identity.origin, true).success)
        assertEquals(1, executions)
        session.close()
    }
}
