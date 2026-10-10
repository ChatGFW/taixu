package top.wkbin.taixu.harness

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import top.wkbin.taixu.harness.directory.CapabilityScriptRunner

/**
 * codemode 脚本运行器合同：编排价值（批量/循环合并轮次）与安全边界
 * （Java 全禁、deadline 熔断、内层调用照常走审批与留痕）同时锁定。
 */
class CapabilityScriptRunnerTest {

    private fun runner(
        redactor: (String) -> String = { it },
        inner: suspend (String, String, JsonObject) -> Pair<Boolean, String> = { _, _, _ -> true to "ok" },
    ) = CapabilityScriptRunner(redactor, inner)

    @Test
    fun `script orchestrates multiple capability calls in one round`() = runBlocking {
        val calls = mutableListOf<Pair<String, String>>()
        val runner = runner(inner = { server, tool, _ ->
            calls += server to tool
            true to "结果-$tool"
        })
        val code = """
            var a = capability.call("host", "virtual_screen_wait", {duration_ms: 0});
            var b = capability.call("host", "virtual_screen_close", {});
            JSON.stringify({first: a.output, second: b.output, allOk: a.ok && b.ok})
        """.trimIndent()

        val (ok, output) = runner.execute(code, 10_000)

        assertTrue(ok)
        assertEquals(listOf("host" to "virtual_screen_wait", "host" to "virtual_screen_close"), calls)
        assertTrue(output.contains("结果-virtual_screen_wait"))
        assertTrue(output.contains("结果-virtual_screen_close"))
        assertTrue(output.contains("allOk\":true"))
    }

    @Test
    fun `class shutter denies java interop entirely`() = runBlocking {
        val (ok, output) = runner().execute("java.lang.Runtime.getRuntime().exec('id');", 10_000)

        assertFalse("脚本绝不能触达 Java 运行时", ok)
        assertTrue(output.contains("脚本执行失败"))
    }

    @Test
    fun `deadline interrupts infinite loops`() = runBlocking {
        val started = System.currentTimeMillis()
        val (ok, output) = runner().execute("while(true){}", 1_500)

        assertFalse(ok)
        assertTrue(output.contains("超时"))
        assertTrue("deadline 必须熔断而不是挂死", System.currentTimeMillis() - started < 15_000)
    }

    @Test
    fun `blocked inner call surfaces ok=false with guidance`() = runBlocking {
        val runner = runner(inner = { _, tool, _ ->
            if (tool == "screen_click") false to "宿主能力域没有工具「screen_click」。请直接调用 host 工具。" else true to "ok"
        })
        val (ok, output) = runner.execute(
            "var r = capability.call(\"host\", \"screen_click\", {x:1,y:2}); r.ok + \":\" + r.output",
            10_000,
        )

        assertTrue("被拒绝的内层调用不是脚本失败，脚本能感知并改道", ok)
        assertTrue(output.startsWith("false:"))
        assertTrue(output.contains("host 工具"))
    }

    @Test
    fun `inner errors are visible to the script`() = runBlocking {
        val runner = runner(inner = { _, _, _ -> false to "MCP[server] 连接失败" })
        val (ok, output) = runner.execute(
            "var results = []; for (var i = 0; i < 3; i++) { var r = capability.call(\"srv\", \"tool\" + i, {}); results.push(r.ok); } results.join(\",\")",
            10_000,
        )

        assertTrue(ok)
        assertEquals("false,false,false", output)
    }

    @Test
    fun `oversized code is rejected without execution`() = runBlocking {
        val big = "x".repeat(CapabilityScriptRunner.MAX_CODE_CHARS + 1)
        val (ok, output) = runner().execute(big, 10_000)

        assertFalse(ok)
        assertTrue(output.contains("脚本过长"))
    }

    @Test
    fun `result objects are json stringified`() = runBlocking {
        val runner = runner()
        val (ok, output) = runner.execute(
            "var a = capability.call(\"host\", \"virtual_screen_close\", {}); ({done: a.ok, via: \"script\"})",
            10_000,
        )

        assertTrue(ok)
        assertTrue(output.contains("\"done\":true"))
        assertTrue(output.contains("\"via\":\"script\""))
    }
}
