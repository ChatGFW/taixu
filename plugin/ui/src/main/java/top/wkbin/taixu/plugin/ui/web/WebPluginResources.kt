package top.wkbin.taixu.plugin.ui.web

import android.webkit.WebResourceResponse
import java.io.ByteArrayInputStream

/** Immutable, bounded asset snapshot. Every request gets a response, including forbidden URLs. */
internal class WebPluginResources(private val policy: WebPluginPolicy, assets: Map<String, ByteArray>) {
    init {
        require(assets.containsKey("index.html") && assets.size <= 64)
        val themeBytes = assets["theme.css"]?.size ?: 0
        require(themeBytes <= 4096)
        require(assets.values.sumOf { it.size.toLong() } - themeBytes <= 2 * 1024 * 1024)
        require(assets.keys.all { policy.assetPath("${policy.origin}/$it") == it })
    }
    private val assets = assets.mapValues { it.value.copyOf() }

    fun response(url: String, method: String): WebResourceResponse {
        val path = policy.assetPath(url)
        val bytes = if (method == "GET") assets[path] else null
        val mime = when (path?.substringAfterLast('.')) {
            "html" -> "text/html"
            "js" -> "application/javascript"
            "css" -> "text/css"
            "png" -> "image/png"
            "svg" -> "image/svg+xml"
            else -> "application/octet-stream"
        }
        return WebResourceResponse(mime, "UTF-8", if (bytes == null) 403 else 200,
            if (bytes == null) "Forbidden" else "OK", mapOf(
                "Content-Security-Policy" to WebPluginPolicy.CSP,
                "X-Content-Type-Options" to "nosniff", "Cache-Control" to "no-store",
                "Referrer-Policy" to "no-referrer",
            ), ByteArrayInputStream(bytes ?: ByteArray(0)))
    }
}
