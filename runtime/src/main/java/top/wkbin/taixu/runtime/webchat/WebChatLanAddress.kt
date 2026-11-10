package top.wkbin.taixu.runtime.webchat

import java.net.Inet4Address
import java.net.InetAddress
import java.net.NetworkInterface

/**
 * 协作台对外展示用的局域网 IPv4。
 *
 * 枚举顺序不稳定，且手机常同时存在 Wi-Fi / 蜂窝 / VPN(tun) 地址；
 * 若把 `10.*`（蜂窝 CGNAT）或 `172.*`（VPN）优先返回，电脑端会打开不可达 URL。
 */
internal fun resolveWebChatLanIpv4(
    interfaces: NetworkEnumeration = SystemNetworkInterfaces,
): String {
    var bestHost: String? = null
    var bestScore = Int.MIN_VALUE
    for (network in interfaces.list()) {
        if (!network.isUp || network.isLoopback) continue
        val ifaceScore = interfaceScore(network.name) ?: continue
        for (address in network.addresses) {
            if (address !is Inet4Address || address.isLoopbackAddress || address.isAnyLocalAddress) continue
            val host = address.hostAddress?.takeIf { it.isNotBlank() } ?: continue
            val score = ifaceScore + ipv4Score(host)
            if (score > bestScore) {
                bestScore = score
                bestHost = host
            }
        }
    }
    return bestHost ?: "127.0.0.1"
}

/** 跳过 VPN / 蜂窝等非局域网口；Wi-Fi / 有线优先。返回 null 表示忽略该网卡。 */
internal fun interfaceScore(name: String): Int? {
    val n = name.lowercase()
    if (n == "lo" || n.startsWith("lo")) return null
    if (n.startsWith("tun") || n.startsWith("ppp") || n.startsWith("ipsec") || n.startsWith("wg")) return null
    if (n.startsWith("dummy")) return null
    // MediaTek / Qualcomm 蜂窝口：ccmni* / rmnet*
    if (n.startsWith("ccmni") || n.startsWith("rmnet") || n.startsWith("sit") || n.startsWith("rev_rmnet")) return null
    return when {
        n.startsWith("wlan") || n.startsWith("wifi") -> 300
        n.startsWith("eth") || n.startsWith("en") -> 280
        n.startsWith("ap") || n.startsWith("softap") || n.startsWith("swlan") -> 260
        else -> 100
    }
}

/** RFC1918：192.168 > 172.16/12 > 10/8（10/8 在 Android 上常被蜂窝占用）。 */
internal fun ipv4Score(host: String): Int = when {
    host.startsWith("192.168.") -> 50
    isPrivate172(host) -> 40
    host.startsWith("10.") -> 20
    else -> 0
}

internal fun isPrivate172(host: String): Boolean {
    val parts = host.split('.')
    if (parts.size != 4 || parts[0] != "172") return false
    val second = parts[1].toIntOrNull() ?: return false
    return second in 16..31
}

internal data class NetIface(
    val name: String,
    val isUp: Boolean,
    val isLoopback: Boolean,
    val addresses: List<InetAddress>,
)

internal fun interface NetworkEnumeration {
    fun list(): List<NetIface>
}

internal object SystemNetworkInterfaces : NetworkEnumeration {
    override fun list(): List<NetIface> {
        return NetworkInterface.getNetworkInterfaces()?.toList().orEmpty().map { network ->
            NetIface(
                name = network.name.orEmpty(),
                isUp = runCatching { network.isUp }.getOrDefault(false),
                isLoopback = runCatching { network.isLoopback }.getOrDefault(false),
                addresses = network.inetAddresses?.toList().orEmpty(),
            )
        }
    }
}
