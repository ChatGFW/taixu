package top.wkbin.taixu.core.database.repository

import org.junit.Assert.*
import org.junit.Test

class BotCapabilityCodecTest {
    @Test fun `absolute expiry survives storage and old records have no deadline`() {
        val raw = encodeCapabilityServers(null, emptyMap(), 12345)
        assertEquals(12345L, decodeCapabilityExpiry(raw))
        assertNull(decodeCapabilityServers(raw))
        assertNull(decodeCapabilityExpiry(null))
        assertNull(decodeCapabilityExpiry("[]"))
        assertEquals(emptyList<String>(), decodeCapabilityServers("""{"version":1,"servers":null,"tools":{},"expiresAt":-1}"""))
    }

    @Test fun `old unrestricted and service array records remain readable`() {
        assertNull(decodeCapabilityServers(null))
        assertEquals(listOf("git"), decodeCapabilityServers("[\"git\"]"))
        assertEquals(emptyMap<String, List<String>>(), decodeCapabilityTools("[\"git\"]"))
        assertEquals("[\"git\"]", encodeCapabilityServers(listOf("git")))
    }

    @Test fun `tool restrictions survive encoding including deny all and exact case`() {
        val tools = mapOf("git" to listOf("Read", "read"), "browser" to emptyList())
        val raw = encodeCapabilityServers(listOf("git", "browser"), tools)
        assertEquals(listOf("git", "browser"), decodeCapabilityServers(raw))
        assertEquals(tools, decodeCapabilityTools(raw))
    }

    @Test fun `all servers can have individually restricted tools`() {
        val tools = mapOf("git" to listOf("status"))
        val raw = encodeCapabilityServers(null, tools)
        assertNull(decodeCapabilityServers(raw))
        assertEquals(tools, decodeCapabilityTools(raw))
    }

    @Test fun `malformed or unsupported policies deny all services`() {
        listOf("{", "null", "{}", "[12]", """{"version":2,"servers":null,"tools":{}}""",
            """{"servers":null,"tools":{"git":12}}""").forEach { raw ->
            assertEquals(raw, emptyList<String>(), decodeCapabilityServers(raw))
        }
    }

    @Test fun `removing all tool restrictions returns to legacy encoding`() {
        assertNull(encodeCapabilityServers(null, emptyMap()))
        assertEquals("[]", encodeCapabilityServers(emptyList(), emptyMap()))
    }
}
