package top.wkbin.taixu.core.browser

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class HostActionPolicyPresetsTest {

    @Before
    fun setUp() {
        HostActionPolicy.resetToDefault()
    }

    @After
    fun tearDown() {
        HostActionPolicy.resetToDefault()
    }

    @Test
    fun `every preset validates`() {
        HostActionPolicyPresets.ALL.forEach { preset ->
            val json = HostActionPolicy.encodePretty(preset.document)
            assertTrue(preset.id, HostActionPolicy.validateAndParse(json).isSuccess)
        }
    }

    @Test
    fun `unattended nav preset blocks schedule navigate`() {
        HostActionPolicy.install(HostActionPolicyPresets.BLOCK_UNATTENDED_NAV.document)
        val denied = HostActionPolicy.evaluate(
            ActionContext(
                toolName = "browser.navigate",
                pageUrl = "https://example.com/",
                pageHost = "example.com",
                initiatorKind = "schedule",
            ),
        )
        assertTrue(denied is PolicyDecision.Deny)

        val personOk = HostActionPolicy.evaluate(
            ActionContext(
                toolName = "browser.navigate",
                pageUrl = "https://example.com/",
                pageHost = "example.com",
                initiatorKind = "person",
            ),
        )
        assertEquals(PolicyDecision.Allow, personOk)
    }
}
