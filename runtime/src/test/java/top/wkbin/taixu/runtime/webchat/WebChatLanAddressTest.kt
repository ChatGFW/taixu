package top.wkbin.taixu.runtime.webchat

import java.net.InetAddress
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WebChatLanAddressTest {

    @Test
    fun `prefers wifi 192_168 over cellular 10 and vpn 172`() {
        val ip = resolveWebChatLanIpv4(
            interfaces = FixedNetworks(
                NetIface("ccmni1", isUp = true, isLoopback = false, addresses = listOf(inet("10.55.116.37"))),
                NetIface("tun0", isUp = true, isLoopback = false, addresses = listOf(inet("172.19.0.1"))),
                NetIface("wlan0", isUp = true, isLoopback = false, addresses = listOf(inet("192.168.110.117"))),
            ),
        )
        assertEquals("192.168.110.117", ip)
    }

    @Test
    fun `skips down wifi and falls back to ethernet private range`() {
        val ip = resolveWebChatLanIpv4(
            interfaces = FixedNetworks(
                NetIface("wlan0", isUp = false, isLoopback = false, addresses = listOf(inet("192.168.1.2"))),
                NetIface("eth0", isUp = true, isLoopback = false, addresses = listOf(inet("172.16.0.8"))),
                NetIface("tun0", isUp = true, isLoopback = false, addresses = listOf(inet("172.19.0.1"))),
            ),
        )
        assertEquals("172.16.0.8", ip)
    }

    @Test
    fun `returns loopback when only vpn and cellular exist`() {
        val ip = resolveWebChatLanIpv4(
            interfaces = FixedNetworks(
                NetIface("ccmni1", isUp = true, isLoopback = false, addresses = listOf(inet("10.55.116.37"))),
                NetIface("tun0", isUp = true, isLoopback = false, addresses = listOf(inet("172.19.0.1"))),
            ),
        )
        assertEquals("127.0.0.1", ip)
    }

    @Test
    fun `interface score rejects vpn and cellular names`() {
        assertNull(interfaceScore("tun0"))
        assertNull(interfaceScore("ccmni1"))
        assertNull(interfaceScore("rmnet_data0"))
        assertTrue((interfaceScore("wlan0") ?: 0) > (interfaceScore("dummy0") ?: 0))
    }

    @Test
    fun `private 172 detection is rfc1918 only`() {
        assertTrue(isPrivate172("172.16.0.1"))
        assertTrue(isPrivate172("172.31.255.255"))
        assertEquals(0, ipv4Score("172.15.0.1"))
        assertEquals(0, ipv4Score("172.32.0.1"))
    }

    private fun inet(host: String): InetAddress = InetAddress.getByName(host)

    private class FixedNetworks(private vararg val items: NetIface) : NetworkEnumeration {
        override fun list(): List<NetIface> = items.toList()
    }
}
