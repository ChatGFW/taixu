package top.wkbin.taixu.ui.chat

import kotlinx.serialization.json.buildJsonObject
import org.junit.Assert.*
import org.junit.Test
import top.wkbin.taixu.harness.HarnessTool
import top.wkbin.taixu.harness.ToolCall

class ToolUiRegistryTest {
    private fun call(tool: HarnessTool, raw: String? = null) = ToolCall(id = "test", createdAt = 0L, tool = tool, args = buildJsonObject { }, rawToolName = raw)

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
}
