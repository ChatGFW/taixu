package top.wkbin.taixu.ui.settings

import java.net.Inet4Address
import java.net.NetworkInterface

/** 探测本机局域网 IPv4（用于展示可被外部设备访问的连接地址）；失败返回 null。 */
internal fun detectLanIp(): String? = try {
    NetworkInterface.getNetworkInterfaces()
        ?.asSequence()
        ?.filter { !it.isLoopback && it.isUp }
        ?.flatMap { it.inetAddresses.asSequence() }
        ?.firstOrNull { it is Inet4Address && !it.isLoopbackAddress }
        ?.hostAddress
        ?.takeIf { it.isNotBlank() && it != "127.0.0.1" }
} catch (_: Exception) {
    null
}
