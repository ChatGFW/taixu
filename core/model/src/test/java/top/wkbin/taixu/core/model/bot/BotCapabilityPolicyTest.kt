package top.wkbin.taixu.core.model.bot

import org.junit.Assert.*
import org.junit.Test

class BotCapabilityPolicyTest {
    @Test fun `intersection keeps narrower servers tools and deadline`() {
        val parent = BotCapabilityPolicy(listOf("git", "web"), mapOf("git" to listOf("read", "write")), 200)
        val child = BotCapabilityPolicy(listOf("git", "other"), mapOf("git" to listOf("read", "delete")), 300)
        val result = parent.intersect(child)
        assertEquals(listOf("git"), result.servers)
        assertEquals(listOf("read"), result.tools["git"])
        assertEquals(200L, result.expiresAt)
        assertFalse(result.isExpired(199))
        assertTrue(result.isExpired(200))
    }

    @Test fun `unrestricted policy preserves restrictions and explicit empty tool sets`() {
        val restricted = BotCapabilityPolicy(listOf("git"), mapOf("git" to emptyList()), 100)
        val unrestricted = BotCapabilityPolicy(null, emptyMap())
        assertEquals(restricted, unrestricted.intersect(restricted))
        assertEquals(restricted, restricted.intersect(unrestricted))
        assertEquals(emptyList<String>(), restricted.intersect(BotCapabilityPolicy(emptyList(), emptyMap())).servers)
    }
}
