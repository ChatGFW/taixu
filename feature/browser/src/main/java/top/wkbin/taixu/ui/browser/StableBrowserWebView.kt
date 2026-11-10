package top.wkbin.taixu.ui.browser

import android.view.ViewGroup
import android.webkit.WebView
import android.widget.FrameLayout
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView

/** FrameLayout 宿主稳定嵌入池化 WebView；重组只 re-parent，避免 WebView 根节点反复摘挂闪白。 */
@Composable
internal fun StableBrowserWebView(
    resolveWebView: () -> WebView?,
    modifier: Modifier = Modifier,
) {
    val currentWebView = resolveWebView()
    AndroidView(
        factory = { context ->
            FrameLayout(context).also { host ->
                resolveWebView()?.let { webView ->
                    (webView.parent as? ViewGroup)?.removeView(webView)
                    host.addView(webView, matchParent())
                }
            }
        },
        update = { host ->
            val webView = currentWebView ?: return@AndroidView
            if (webView.parent === host) return@AndroidView
            (webView.parent as? ViewGroup)?.removeView(webView)
            host.removeAllViews()
            host.addView(webView, matchParent())
        },
        onRelease = { it.removeAllViews() },
        modifier = modifier,
    )
}

private fun matchParent() = FrameLayout.LayoutParams(
    ViewGroup.LayoutParams.MATCH_PARENT,
    ViewGroup.LayoutParams.MATCH_PARENT,
)
