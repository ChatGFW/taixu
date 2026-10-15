package top.wkbin.taixu.ui.navigation

import androidx.compose.runtime.Composable
import top.wkbin.taixu.plugin.api.PluginExtensionRef
import top.wkbin.taixu.plugin.ui.PluginPageHost
import top.wkbin.taixu.plugin.ui.PluginPages
import top.wkbin.taixu.ui.terminal.TerminalPlugin
import top.wkbin.taixu.ui.browser.BrowserPlugin
import top.wkbin.taixu.ui.browser.BrowserViewModel
import top.wkbin.taixu.plugin.runtime.PluginController

/** New plugins use this generic destination; existing saved terminal destinations remain valid. */
@Composable
internal fun RegisteredPluginPage(pages: PluginPages, destination: PluginPageDestination, onBack: () -> Unit) {
    PluginPageHost(
        pages = pages,
        reference = PluginExtensionRef(destination.pluginId, destination.pageId),
        arguments = destination.arguments,
        onBack = onBack,
    )
}

/** Compatibility adapter shared by the old terminal route and the chat's embedded pane. */
@Composable
internal fun TerminalPluginPage(pages: PluginPages, project: String, embedded: Boolean = false, onBack: () -> Unit) {
    PluginPageHost(
        pages = pages,
        reference = TerminalPlugin.page,
        arguments = mapOf(TerminalPlugin.PROJECT_ARGUMENT to project),
        embedded = embedded,
        onBack = onBack,
    )
}

@Composable
internal fun BrowserPluginPage(pages: PluginPages, embedded: Boolean = false, onBack: (() -> Unit)?) {
    PluginPageHost(
        pages = pages, reference = BrowserPlugin.page, embedded = embedded,
        arguments = mapOf(BrowserPlugin.SHOW_EXIT_ARGUMENT to (onBack != null).toString()), onBack = onBack ?: {},
    )
}

internal fun browserPluginBack(controller: PluginController, model: BrowserViewModel): Boolean =
    controller.acquire(BrowserPlugin.page.pluginId)?.use { model.handleBackImmediate() } ?: false

@Composable
internal fun ManagedPluginsScreen(platform: NativePluginPlatform, controller: PluginController, onOpen: (PluginExtensionRef) -> Unit, onOpenTool: (String) -> Unit, onBack: () -> Unit) {
    top.wkbin.taixu.plugin.ui.PluginManagerScreen(platform.plugins, controller, onOpen, onBack) {
        top.wkbin.taixu.ui.settings.PackagedPluginInventorySection(onOpenTool)
    }
}
