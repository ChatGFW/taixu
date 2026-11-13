package top.wkbin.taixu.ui.chat

import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.*
import org.junit.Test
import top.wkbin.taixu.harness.HarnessTool
import top.wkbin.taixu.harness.ToolCall

class ToolUiRegistryTest {
    private fun call(tool: HarnessTool, raw: String? = null, action: String? = null, ack: String? = null, goal: String? = null) =
        ToolCall(
            id = "test",
            createdAt = 0L,
            tool = tool,
            args = buildJsonObject {
                action?.let { put("action", it) }
                ack?.let { put("ack", it) }
                goal?.let { put("goal", it) }
            },
            rawToolName = raw,
        )

    @Test fun `unknown extension keeps generic inspector and file tools retain diff preview`() {
        val registry = ToolUiRegistry()
        assertNull(registry.resolve(call(HarnessTool.MCP, "unknown")).previewLabel)
        assertNotNull(registry.resolve(call(HarnessTool.EDIT)).previewLabel)
        assertNotNull(registry.resolve(call(HarnessTool.READ)).previewLabel)
    }

    @Test fun `exact extension registration cannot replace built in tool by raw name`() {
        val custom = object : ToolUiRenderer { }
        val registry = ToolUiRegistry(mapOf("plugin.demo.inspect" to custom))
        assertSame(custom, registry.resolve(call(HarnessTool.MCP, "plugin.demo.inspect")))
        assertNotSame(custom, registry.resolve(call(HarnessTool.MCP, "plugin.demo.inspect.more")))
        assertNotSame(custom, registry.resolve(call(HarnessTool.EDIT, "plugin.demo.inspect")))
    }

    @Test fun `host twin task exposes ack as timeline target`() {
        val registry = ToolUiRegistry()
        val renderer = registry.resolve(call(HarnessTool.HOST, action = "virtual_screen_task", ack = "这就去发", goal = "发 hello"))
        assertEquals("这就去发", renderer.target(ToolUiContext(call(HarnessTool.HOST, action = "virtual_screen_task", ack = "这就去发", goal = "发 hello"), null, "", false)))
        assertEquals(
            "在 QQ 发 hello",
            registry.resolve(call(HarnessTool.HOST, action = "virtual_screen_task", goal = "在 QQ 发 hello"))
                .target(ToolUiContext(call(HarnessTool.HOST, action = "virtual_screen_task", goal = "在 QQ 发 hello"), null, "", false)),
        )
        assertNull(registry.resolve(call(HarnessTool.HOST, action = "app_launch")).target(ToolUiContext(call(HarnessTool.HOST, action = "app_launch"), null, "", false)))
    }
}
