package top.wkbin.taixu.plugin.ui.web

import android.os.Build
import androidx.compose.ui.res.stringResource
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import top.wkbin.taixu.plugin.api.PluginApiRange
import top.wkbin.taixu.plugin.api.PluginExtensionRef
import top.wkbin.taixu.plugin.runtime.PluginCapability
import top.wkbin.taixu.plugin.ui.R

/** Inert at registration; native confirmation permits the example's read operation. */
object WebPluginDemo {
    val page = PluginExtensionRef("taixu.web-demo", "main")
    fun create() = WebPagePlugin(
        id = page.pluginId, version = "1.0.0",
        title = { stringResource(R.string.ui_web_plugin_title) },
        description = { stringResource(R.string.ui_web_plugin_description) },
        capabilities = listOf(WebPluginCapability(
            PluginCapability("host.app-info", "application"),
            title = { stringResource(R.string.ui_web_plugin_permission) },
            description = { stringResource(R.string.ui_web_plugin_permission_detail) },
        ) { context ->
            buildJsonObject {
                put("package", context.packageName)
                put("androidApi", Build.VERSION.SDK_INT)
                put("pluginApi", PluginApiRange.CURRENT)
            }.toString()
        }),
        loadAssets = { context ->
            listOf("index.html", "plugin.js", "plugin.css").associateWith { name ->
                context.assets.open("taixu-web-demo/$name").use { it.readBytes() }
            }
        },
    ).create()
}
