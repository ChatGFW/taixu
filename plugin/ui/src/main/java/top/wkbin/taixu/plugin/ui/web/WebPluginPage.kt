package top.wkbin.taixu.plugin.ui.web

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.webkit.WebViewFeature
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import top.wkbin.taixu.plugin.runtime.*
import top.wkbin.taixu.plugin.ui.PluginPageRequest
import top.wkbin.taixu.plugin.ui.R
import top.wkbin.taixu.ui.components.*

@Composable
internal fun WebPluginPage(plugin: WebPagePlugin, request: PluginPageRequest) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val colors = MaterialTheme.colorScheme
    val themeCss = remember(colors) {
        fun cssColor(color: androidx.compose.ui.graphics.Color) = "#%06x".format(color.toArgb() and 0xffffff)
        ":root { --surface: ${cssColor(colors.surface)}; --on-surface: ${cssColor(colors.onSurface)}; " +
            "--primary: ${cssColor(colors.primary)}; --on-primary: ${cssColor(colors.onPrimary)}; " +
            "--container: ${cssColor(colors.surfaceContainerHigh)}; }"
    }
    val policy = remember(plugin, request.activation) { WebPluginPolicy() }
    val session = remember(plugin, context, request.activation, policy) {
        PluginCapabilitySession(
            PluginCapabilityIdentity(plugin.id, plugin.version, request.activation?.generation ?: 0,
                policy.origin, "android:local"),
            plugin.capabilities.associate { it.capability to suspend { it.execute(context) } },
            isAvailable = { request.activation?.let { it.pluginId == plugin.id && it.isAccepting } == true },
        )
    }
    val prompt by session.prompt.collectAsState()
    val resources by produceState<Result<WebPluginResources>?>(null, plugin, policy, context) {
        value = try {
            Result.success(withContext(Dispatchers.IO) {
                val assets = plugin.loadAssets(context) + ("theme.css" to themeCss.toByteArray(Charsets.UTF_8))
                WebPluginResources(policy, assets)
            })
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            Result.failure(IllegalStateException("Web plugin assets unavailable"))
        }
    }
    var view by remember(session) { mutableStateOf<WebPluginView?>(null) }
    DisposableEffect(session) {
        onDispose { session.close(); view?.dispose(); view = null }
    }
    Scaffold(topBar = { RuntimeTopBar(plugin.title(), request.onBack) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            RuntimeCard(Modifier.fillMaxWidth().padding(12.dp)) {
                Text(stringResource(R.string.ui_web_plugin_scope))
                RuntimeTextButton(onClick = { session.revoke() }) {
                    Text(stringResource(R.string.ui_web_plugin_revoke))
                }
            }
            if (!WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)) {
                Text(stringResource(R.string.ui_web_plugin_unavailable), Modifier.padding(16.dp))
            } else if (resources == null) {
                Text(stringResource(R.string.ui_web_plugin_loading), Modifier.padding(16.dp))
            } else if (resources?.isFailure == true) {
                Text(stringResource(R.string.ui_web_plugin_load_failed), Modifier.padding(16.dp))
            } else key(session) {
                AndroidView(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    factory = { androidContext ->
                        WebPluginView(androidContext, policy, session, scope, resources!!.getOrThrow()).also { view = it }
                    },
                    onRelease = { it.dispose() },
                )
            }
        }
    }
    prompt?.let { pending ->
        val capability = plugin.capabilities.single { it.capability == pending.call.capability }
        RuntimeAlertDialog(
            onDismissRequest = { session.decide(pending.token, PluginGrantChoice.DENY) },
            title = { Text(capability.title()) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(capability.description())
                    Text("${pending.identity.pluginId} · ${pending.identity.version}\n${pending.call.capability.action}\n${pending.call.capability.target}\n${pending.identity.environment}")
                }
            },
            confirmButton = {
                Column {
                    RuntimeTextButton(onClick = { session.decide(pending.token, PluginGrantChoice.ONCE) }) {
                        Text(stringResource(R.string.ui_web_plugin_allow_once))
                    }
                    RuntimeTextButton(onClick = { session.decide(pending.token, PluginGrantChoice.SESSION) }) {
                        Text(stringResource(R.string.ui_web_plugin_allow_session))
                    }
                }
            },
            dismissButton = {
                RuntimeTextButton(onClick = { session.decide(pending.token, PluginGrantChoice.DENY) }) {
                    Text(stringResource(R.string.ui_web_plugin_deny))
                }
            },
        )
    }
}
