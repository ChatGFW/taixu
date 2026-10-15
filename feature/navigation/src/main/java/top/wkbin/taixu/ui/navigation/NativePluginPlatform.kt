package top.wkbin.taixu.ui.navigation

import top.wkbin.taixu.core.datastore.PluginPreferences
import top.wkbin.taixu.plugin.runtime.PluginController
import top.wkbin.taixu.plugin.runtime.PluginRuntime
import top.wkbin.taixu.plugin.runtime.PluginSelectionStore
import top.wkbin.taixu.plugin.ui.NativePagePlugin
import top.wkbin.taixu.plugin.ui.NativePluginPageRegistry
import top.wkbin.taixu.plugin.ui.PluginPages
import top.wkbin.taixu.ui.browser.BrowserPlugin
import top.wkbin.taixu.ui.terminal.TerminalPlugin

/** Explicit host assembly; factories register descriptions and do not start PTY or WebView engines. */
class NativePluginPlatform {
    val plugins: List<NativePagePlugin> = listOf(TerminalPlugin.create(), BrowserPlugin.create())
    val pages: PluginPages = NativePluginPageRegistry(plugins)

    fun controller(preferences: PluginPreferences): PluginController = PluginRuntime(
        pages.catalog, plugins.associate { it.descriptor.id to it.activate },
        object : PluginSelectionStore {
            override suspend fun disabledPlugins() = preferences.disabledPlugins()
            override suspend fun setEnabled(id: String, enabled: Boolean) = preferences.setEnabled(id, enabled)
        },
    )
}
