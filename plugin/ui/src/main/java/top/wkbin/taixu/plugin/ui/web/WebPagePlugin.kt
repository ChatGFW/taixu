package top.wkbin.taixu.plugin.ui.web

import android.content.Context
import androidx.compose.runtime.Composable
import top.wkbin.taixu.plugin.api.PluginDescriptor
import top.wkbin.taixu.plugin.api.PluginExtension
import top.wkbin.taixu.plugin.api.PluginExtensionPoint
import top.wkbin.taixu.plugin.runtime.PluginCapability
import top.wkbin.taixu.plugin.ui.*
import top.wkbin.taixu.ui.components.RuntimeIconName

/** Host-registered operation. An untrusted document cannot install an executor. */
class WebPluginCapability(
    val capability: PluginCapability,
    val title: @Composable () -> String,
    val description: @Composable () -> String,
    val execute: suspend (Context) -> String,
)

/** Trusted static Web adapter using the existing PAGE lifecycle, independent of .txplugin. */
class WebPagePlugin(
    val id: String,
    val version: String,
    val title: @Composable () -> String,
    val description: @Composable () -> String,
    capabilities: List<WebPluginCapability>,
    val loadAssets: (Context) -> Map<String, ByteArray>,
) {
    val capabilities: List<WebPluginCapability> = java.util.Collections.unmodifiableList(capabilities.toList())
    init {
        require(this.capabilities.size <= 32)
        require(this.capabilities.map { it.capability }.distinct().size == this.capabilities.size)
        require(this.capabilities.all { it.capability.action.length in 1..128 && it.capability.target.length in 1..256 })
    }

    fun create() = NativePagePlugin(
        descriptor = PluginDescriptor(id, version, extensions = listOf(PluginExtension("main", PluginExtensionPoint.PAGE))),
        pages = listOf(NativePluginPage("main") { WebPluginPage(this, it) }),
        title = title,
        description = description,
        entries = listOf(NativePluginEntry("settings", "main", PluginEntryLocation.SETTINGS, title)),
        icon = RuntimeIconName.Globe,
    )
}
