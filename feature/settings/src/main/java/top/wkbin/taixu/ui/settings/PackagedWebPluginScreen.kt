package top.wkbin.taixu.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import top.wkbin.taixu.feature.settings.R
import top.wkbin.taixu.plugin.api.PluginApiRange
import top.wkbin.taixu.plugin.runtime.PluginCapability
import top.wkbin.taixu.plugin.ui.web.WebPagePlugin
import top.wkbin.taixu.plugin.ui.web.WebPluginCapability
import top.wkbin.taixu.plugin.ui.web.WebPluginHost
import top.wkbin.taixu.ui.components.RuntimeTopBar
import top.wkbin.taixu.runtime.bridge.HostBridge
import top.wkbin.taixu.runtime.bridge.PluginHostScope

/** Restored routes are checked against the currently selected package identity and exact version. */
@Composable
fun PackagedWebPluginScreen(toolId: String, version: String, pluginId: String, onBack: () -> Unit, model: WebPluginsViewModel = koinViewModel()) {
    val bridge = koinInject<HostBridge>()
    val sources = koinInject<PackagedWebPluginSources>()
    val packages by model.packages.collectAsStateWithLifecycle()
    val expected = packages.find { it.id == toolId && it.version == version && it.plugin?.id == pluginId }
    if (expected == null) {
        Scaffold(topBar = { RuntimeTopBar(stringResource(R.string.web_packages_title), onBack) }) { padding ->
            Text(stringResource(R.string.web_packages_unavailable), Modifier.padding(padding).padding(16.dp))
        }
    } else {
        val plugin = remember(expected, model.repository, bridge, sources) {
            val proot = expected.plugin!!.webSource == "PROOT_STATIC"
            WebPagePlugin(
                id = expected.plugin!!.id, version = expected.version,
                title = { expected.name }, description = {
                    if (proot) expected.description + "\n" + stringResource(R.string.web_packages_proot_source)
                    else expected.description
                },
                capabilities = expected.plugin!!.capabilities.map {
                    val clipboard = it.name == "CLIPBOARD_WRITE" && it.target == "clipboard"
                    require(clipboard || it.name == "APP_INFO" && it.target == "application")
                    WebPluginCapability(PluginCapability(if (clipboard) "host.clipboard-write" else "host.app-info", it.target),
                        title = { stringResource(if (clipboard) R.string.web_packages_clipboard_title else R.string.web_packages_permission_title) },
                        description = { stringResource(if (clipboard) R.string.web_packages_clipboard_write else R.string.web_packages_app_info) },
                        allowSession = !clipboard,
                    ) { _, identity, call ->
                        val caller = currentCoroutineContext()
                        val scope = PluginHostScope(identity.pluginId, identity.version, identity.generation,
                            identity.origin, identity.environment)
                        val available = { caller.isActive && model.repository.isCurrent(expected) }
                        if (clipboard) bridge.writePluginClipboard(scope, requireNotNull(call.text), available)
                        else {
                            val info = bridge.readPluginAppInfo(scope, available)
                            JsonObject(Json.parseToJsonElement(info).jsonObject +
                                ("pluginApi" to JsonPrimitive(PluginApiRange.CURRENT))).toString()
                        }
                    }
                },
                isAvailable = { model.repository.isCurrent(expected) },
                openSource = if (proot) { { _ -> sources.openProot(expected) } } else null,
                loadAssets = { model.repository.snapshot(expected) },
            )
        }
        WebPluginHost(plugin, onBack)
    }
}
