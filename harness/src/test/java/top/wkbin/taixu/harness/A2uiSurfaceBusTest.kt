package top.wkbin.taixu.harness

import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * A2uiSurfaceBus 单测：覆盖 render_surface 工具入参校验、载荷发布、
 * 同 surfaceId 覆盖与缓存上限淘汰（与 ToolExecutor 的对接契约一致）。
 */
class A2uiSurfaceBusTest {

    @Before
    fun reset() = A2uiSurfaceBus.clear()

    @Test
    fun `合法参数发布成功并进入总线`() {
        val (success, output) = A2uiSurfaceBus.publishFromTool(validArgs("s1", "看板一"))
        assertTrue(success)
        assertTrue(output.contains("s1"))
        val surfaces = A2uiSurfaceBus.surfaces.value
        assertEquals(1, surfaces.size)
        assertEquals("s1", surfaces.first().surfaceId)
        assertEquals("看板一", surfaces.first().title)
    }

    @Test
    fun `同 surfaceId 再次发布覆盖旧条目`() {
        A2uiSurfaceBus.publishFromTool(validArgs("s1", "旧标题"))
        A2uiSurfaceBus.publishFromTool(validArgs("s1", "新标题"))
        val surfaces = A2uiSurfaceBus.surfaces.value
        assertEquals(1, surfaces.size)
        assertEquals("新标题", surfaces.first().title)
    }

    @Test
    fun `非法 surfaceId 校验失败且不入总线`() {
        val (success, output) = A2uiSurfaceBus.publishFromTool(validArgs("bad id!", "标题"))
        assertFalse(success)
        assertTrue(output.contains("surfaceId"))
        assertTrue(A2uiSurfaceBus.surfaces.value.isEmpty())
    }

    @Test
    fun `messages 不是 JSON 数组时校验失败`() {
        val args = buildJsonObject {
            put("surfaceId", "s1")
            put("messages", """{"version":"v0.9"}""")
        }
        val (success, output) = A2uiSurfaceBus.publishFromTool(args)
        assertFalse(success)
        assertTrue(output.contains("数组"))
    }

    @Test
    fun `超过缓存上限丢最旧`() {
        repeat(A2uiSurfaceBus.MAX_CACHED_SURFACES + 2) { index ->
            A2uiSurfaceBus.publishFromTool(validArgs("s$index", "界面$index"))
        }
        val surfaces = A2uiSurfaceBus.surfaces.value
        assertEquals(A2uiSurfaceBus.MAX_CACHED_SURFACES, surfaces.size)
        // 新载荷在前：最后发布的 s{MAX+1} 居首，最旧的 s0/s1 被淘汰
        assertEquals("s${A2uiSurfaceBus.MAX_CACHED_SURFACES + 1}", surfaces.first().surfaceId)
        assertEquals("s2", surfaces.last().surfaceId)
    }

    @Test
    fun `clear 清空总线`() {
        A2uiSurfaceBus.publishFromTool(validArgs("s1", "标题"))
        A2uiSurfaceBus.clear()
        assertTrue(A2uiSurfaceBus.surfaces.value.isEmpty())
    }

    private fun validArgs(surfaceId: String, title: String) = buildJsonObject {
        put("surfaceId", surfaceId)
        put("title", title)
        put("messages", A2uiSurfaceBus.sampleMessagesJson())
    }
}
