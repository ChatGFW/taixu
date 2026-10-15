package top.wkbin.taixu.ui.navigation

import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test
import org.koin.dsl.koinApplication
import top.wkbin.taixu.di.feature.navigation.navigationModule
import top.wkbin.taixu.plugin.api.PluginExtensionRef
import top.wkbin.taixu.plugin.ui.PluginPages
import top.wkbin.taixu.ui.terminal.TerminalPlugin
import top.wkbin.taixu.ui.browser.BrowserPlugin

class PluginNavigationTest {
    @Test
    fun realCompositionResolvesTerminalWithoutCreatingPtyOrViewModel() {
        val application = koinApplication {
            allowOverride(false)
            modules(navigationModule)
        }
        try {
            val pages = application.koin.get<PluginPages>()
            assertSame(pages, application.koin.get<PluginPages>())
            assertNotNull(pages.catalog.plugin(TerminalPlugin.page.pluginId))
            assertNotNull(pages.resolve(TerminalPlugin.page))
            assertNotNull(pages.resolve(BrowserPlugin.page))
            val platform = application.koin.get<NativePluginPlatform>()
            assertEquals(2, platform.plugins.size)
            platform.plugins.forEach { plugin ->
                assertTrue(plugin.entries.isNotEmpty())
                plugin.entries.forEach { entry ->
                    assertNotNull(pages.resolve(PluginExtensionRef(plugin.descriptor.id, entry.pageId)))
                }
            }
            assertNull(pages.resolve(PluginExtensionRef("test.absent", "main")))
        } finally {
            application.close()
        }
    }

    @Test
    fun genericPageDestinationRestoresIdentityAndArguments() {
        val destination = PluginPageDestination("taixu.terminal", "main", mapOf("project" to "项目/a b"))
        val encoded = Json.encodeToString(PluginPageDestination.serializer(), destination)
        assertEquals(destination, Json.decodeFromString(PluginPageDestination.serializer(), encoded))
    }

    @Test
    fun savedTerminalRouteStillAcceptsOldFields() {
        val restored = Json.decodeFromString(TerminalDestination.serializer(), """{"toolId":"bash","project":"existing"}""")
        assertEquals(TerminalDestination("bash", "existing"), restored)
    }

    @Test fun pluginManagerRouteCanBeRestoredWithoutAnExecutableAction() {
        val encoded = Json.encodeToString(PluginManagerDestination.serializer(), PluginManagerDestination)
        assertEquals(PluginManagerDestination, Json.decodeFromString(PluginManagerDestination.serializer(), encoded))
    }
}
