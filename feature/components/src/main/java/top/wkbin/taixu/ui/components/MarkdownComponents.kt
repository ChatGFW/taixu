package top.wkbin.taixu.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import top.wkbin.taixu.core.markdown.MdCodeBlock
import top.wkbin.taixu.core.markdown.MdRemoteMedia
import top.wkbin.taixu.core.markdown.MdTable

/** Trusted native rendering hooks. They do not change parsed content or grant tool permissions. */
class MarkdownComponents(
    val codeBlock: @Composable (MdCodeBlock) -> Unit = { CodeBlock(it) },
    val table: @Composable (MdTable) -> Unit = { TableBlock(it) },
    val media: @Composable (MdRemoteMedia, String?) -> Unit = { block, key -> RemoteMediaBlock(block, key) },
)

val LocalMarkdownComponents = staticCompositionLocalOf { MarkdownComponents() }
