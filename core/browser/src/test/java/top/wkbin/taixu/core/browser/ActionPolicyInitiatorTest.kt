package top.wkbin.taixu.core.browser

import org.junit.Assert.assertEquals
import org.junit.Test

class ActionPolicyInitiatorTest {
    @Test
    fun `normalize known kinds and fall back to person`() {
        assertEquals("schedule", ActionPolicyInitiator.normalize(" Schedule "))
        assertEquals("handoff", ActionPolicyInitiator.normalize("handoff"))
        assertEquals("workflow", ActionPolicyInitiator.normalize("workflow"))
        assertEquals("system", ActionPolicyInitiator.normalize("system"))
        assertEquals("person", ActionPolicyInitiator.normalize("person"))
        assertEquals("person", ActionPolicyInitiator.normalize("routine"))
        assertEquals("person", ActionPolicyInitiator.normalize(""))
        assertEquals("person", ActionPolicyInitiator.normalize(null))
    }
}
