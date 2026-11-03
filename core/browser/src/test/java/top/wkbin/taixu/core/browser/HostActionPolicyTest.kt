package top.wkbin.taixu.core.browser

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class HostActionPolicyTest {

    @Before
    fun setUp() {
        HostActionPolicy.resetToDefault()
    }

    @After
    fun tearDown() {
        HostActionPolicy.resetToDefault()
    }

    @Test
    fun `default policy allows ordinary navigation`() {
        val d = HostActionPolicy.evaluate(
            ActionContext(
                toolName = "browser.navigate",
                pageUrl = "https://example.com/",
                pageHost = "example.com",
            ),
        )
        assertEquals(PolicyDecision.Allow, d)
    }

    @Test
    fun `deny rules win before allow`() {
        HostActionPolicy.install(
            ActionPolicyDocument(
                deny = listOf("contains(page.host, 'evil')"),
                allow = listOf("true"),
            ),
        )
        val denied = HostActionPolicy.evaluate(
            ActionContext("browser.navigate", "https://evil.test/", "evil.test"),
        )
        assertTrue(denied is PolicyDecision.Deny)

        val allowed = HostActionPolicy.evaluate(
            ActionContext("browser.navigate", "https://ok.test/", "ok.test"),
        )
        assertEquals(PolicyDecision.Allow, allowed)
    }

    @Test
    fun `empty allow fails closed`() {
        HostActionPolicy.install(ActionPolicyDocument(deny = emptyList(), allow = emptyList()))
        val d = HostActionPolicy.evaluate(ActionContext("browser.snapshot"))
        assertTrue(d is PolicyDecision.Deny)
    }

    @Test
    fun `broken deny denies broken allow does not permit`() {
        HostActionPolicy.install(
            ActionPolicyDocument(
                deny = listOf("not a valid expression!!!"),
                allow = listOf("true"),
            ),
        )
        assertTrue(
            HostActionPolicy.evaluate(ActionContext("browser.navigate", "https://a.test/", "a.test"))
                is PolicyDecision.Deny,
        )

        HostActionPolicy.install(
            ActionPolicyDocument(
                deny = emptyList(),
                allow = listOf("%%%broken%%%", "page.host == 'ok.test'"),
            ),
        )
        assertEquals(
            PolicyDecision.Allow,
            HostActionPolicy.evaluate(ActionContext("browser.open", "https://ok.test/", "ok.test")),
        )
        assertTrue(
            HostActionPolicy.evaluate(ActionContext("browser.open", "https://no.test/", "no.test"))
                is PolicyDecision.Deny,
        )
    }

    @Test
    fun `cloud metadata is hard denied`() {
        val d = HostActionPolicy.evaluate(
            ActionContext(
                toolName = "browser.navigate",
                pageUrl = "http://169.254.169.254/latest/meta-data/",
                pageHost = "169.254.169.254",
            ),
        )
        assertTrue(d is PolicyDecision.Deny)
        assertTrue((d as PolicyDecision.Deny).reason.contains("元数据"))
    }

    @Test
    fun `constrainsPageHost tracks rules that mention the host`() {
        HostActionPolicy.resetToDefault()
        assertFalse(HostActionPolicy.constrainsPageHost())
        HostActionPolicy.install(HostActionPolicyPresets.BLOCK_UNATTENDED_NAV.document)
        assertFalse(HostActionPolicy.constrainsPageHost())
        HostActionPolicy.install(HostActionPolicyPresets.ALLOWLIST_EXAMPLE.document)
        assertTrue(HostActionPolicy.constrainsPageHost())
    }

    @Test
    fun `hostOf strips scheme userinfo and port`() {
        assertEquals("example.com", HostActionPolicy.hostOf("https://user:pw@example.com:8443/x"))
        assertEquals("::1", HostActionPolicy.hostOf("http://[::1]/80/"))
    }

    @Test
    fun `installJson replaces only when parse succeeds`() {
        HostActionPolicy.install(
            ActionPolicyDocument(deny = listOf("tool.name == 'browser.navigate'"), allow = listOf("true")),
        )
        val fail = HostActionPolicy.installJson("{not-json")
        assertTrue(fail.isFailure)
        assertTrue(
            HostActionPolicy.evaluate(ActionContext("browser.navigate")) is PolicyDecision.Deny,
        )

        val ok = HostActionPolicy.installJson("""{"deny":[],"allow":["true"]}""")
        assertTrue(ok.isSuccess)
        assertEquals(PolicyDecision.Allow, HostActionPolicy.evaluate(ActionContext("browser.navigate")))
    }

    @Test
    fun `validateAndParse rejects empty allow and broken rules without installing`() {
        HostActionPolicy.resetToDefault()
        assertTrue(HostActionPolicy.validateAndParse("""{"deny":[],"allow":[]}""").isFailure)
        assertTrue(HostActionPolicy.validateAndParse("""{"deny":["%%%"],"allow":["true"]}""").isFailure)
        assertEquals(PolicyDecision.Allow, HostActionPolicy.evaluate(ActionContext("browser.snapshot")))
    }

    @Test
    fun `installFromStored blank falls back to default`() {
        HostActionPolicy.install(ActionPolicyDocument(deny = listOf("true"), allow = listOf("true")))
        HostActionPolicy.installFromStored("  ")
        assertEquals(PolicyDecision.Allow, HostActionPolicy.evaluate(ActionContext("browser.navigate")))
    }

    @Test
    fun `encodePretty round trips`() {
        val raw = HostActionPolicy.encodePretty(
            ActionPolicyDocument(
                deny = listOf("contains(page.host, 'evil')"),
                allow = listOf("true"),
            ),
        )
        val parsed = HostActionPolicy.validateAndParse(raw)
        assertTrue(parsed.isSuccess)
        assertEquals(listOf("contains(page.host, 'evil')"), parsed.getOrThrow().deny)
    }
}
