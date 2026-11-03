package top.wkbin.taixu.core.browser

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CelLiteTest {
    private val vars = mapOf(
        "tool.name" to "browser.navigate",
        "page.host" to "Example.COM",
        "page.url" to "https://Example.COM/path",
        "initiator.kind" to "schedule",
    )

    @Test
    fun `literals and logic`() {
        assertTrue(CelLite.eval("true", emptyMap()))
        assertFalse(CelLite.eval("false", emptyMap()))
        assertTrue(CelLite.eval("true && true", emptyMap()))
        assertFalse(CelLite.eval("true && false", emptyMap()))
        assertTrue(CelLite.eval("false || true", emptyMap()))
        assertFalse(CelLite.eval("!true", emptyMap()))
        assertTrue(CelLite.eval("!(false)", emptyMap()))
        // 左假 / 左真时右操作数仍须被解析消费
        assertFalse(CelLite.eval("false && (tool.name == 'x' || tool.name == 'y')", vars))
        assertTrue(CelLite.eval("true || (tool.name == 'nope')", vars))
        assertTrue(
            CelLite.eval(
                "initiator.kind == 'schedule' && (tool.name == 'browser.navigate' || tool.name == 'browser.open')",
                vars,
            ),
        )
    }

    @Test
    fun `equality and field resolve`() {
        assertTrue(CelLite.eval("tool.name == 'browser.navigate'", vars))
        assertFalse(CelLite.eval("tool.name == 'browser.click'", vars))
        assertTrue(CelLite.eval("tool.name != 'browser.click'", vars))
        assertTrue(CelLite.eval("initiator.kind == \"schedule\"", vars))
    }

    @Test
    fun `contains and matches are case insensitive`() {
        assertTrue(CelLite.eval("contains(page.host, 'example')", vars))
        assertTrue(CelLite.eval("contains(page.url, 'PATH')", vars))
        assertFalse(CelLite.eval("contains(page.host, 'evil')", vars))
        assertTrue(CelLite.eval("matches(page.host, '^example\\\\.com$')", vars))
    }

    @Test(expected = IllegalStateException::class)
    fun `bare field is not a boolean`() {
        CelLite.eval("page.host", vars)
    }
}
