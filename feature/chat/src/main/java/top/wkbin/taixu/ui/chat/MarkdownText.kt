package top.wkbin.taixu.ui.chat

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import top.wkbin.taixu.ui.components.LocalSandboxHostRoots as ComponentsLocalSandboxHostRoots
import top.wkbin.taixu.ui.components.MarkdownText as ComponentsMarkdownText
import top.wkbin.taixu.ui.components.clearMarkdownCachesForTest as componentsClearMarkdownCachesForTest
import top.wkbin.taixu.ui.components.markdownBlocksPreview as componentsMarkdownBlocksPreview

/**
 * Re-export of [ComponentsMarkdownText] for backward compatibility in feature:chat.
 */
@Composable
fun MarkdownText(
    markdown: String,
    modifier: Modifier = Modifier,
    contentCacheKey: String? = null,
) {
    ComponentsMarkdownText(
        markdown = markdown,
        modifier = modifier,
        contentCacheKey = contentCacheKey,
    )
}

val LocalSandboxHostRoots get() = ComponentsLocalSandboxHostRoots

internal fun markdownBlocksPreview(
    markdown: String,
    contentCacheKey: String? = null,
    largeCacheMinChars: Int = 128_000,
): String = componentsMarkdownBlocksPreview(markdown, contentCacheKey, largeCacheMinChars)

internal fun clearMarkdownCachesForTest() {
    componentsClearMarkdownCachesForTest()
}
