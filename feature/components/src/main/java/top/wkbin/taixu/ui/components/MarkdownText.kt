package top.wkbin.taixu.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.text.selection.SelectionContainer
import top.wkbin.taixu.ui.miuix.HorizontalDivider
import top.wkbin.taixu.ui.miuix.RuntimeTheme
import top.wkbin.taixu.core.model.ChunkedText
import androidx.compose.runtime.key
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import top.wkbin.taixu.core.markdown.*
import top.wkbin.taixu.ui.theme.ChatReadingTypography
import top.wkbin.taixu.ui.theme.chatReadingTypography

internal val LocalMarkdownReadingTypography = staticCompositionLocalOf<ChatReadingTypography?> { null }

/**
 * 轻量 markdown 渲染组件：支持标题、段落、粗体/斜体、行内代码、
 * 代码块（带语言徽章、一键复制与语法高亮）、无序/有序列表、引用、链接、
 * 远程图片与表格。远程图片由 Coil 加载，网页链接由 Compose LinkAnnotation 打开。
 *
 * 正文字阶走 [chatReadingTypography]：略小、行距更松，标题 SemiBold/Medium 而不强制 Bold。
 */
@Composable
fun MarkdownText(
    markdown: String,
    modifier: Modifier = Modifier,
    contentCacheKey: String? = null,
) {
    val blocks = remember(contentCacheKey, markdown) {
        resolveMarkdownBlocks(markdown, contentCacheKey)
    }
    MarkdownBlocks(blocks, modifier, contentCacheKey)
}

/** Incremental live renderer: retained blocks are not reparsed as the active tail grows. */
@Composable
fun StreamingMarkdownText(
    text: ChunkedText,
    modifier: Modifier = Modifier,
    contentCacheKey: String? = null,
) {
    val parser = remember(contentCacheKey) { IncrementalMarkdown { parseMarkdownBlocks(it, cache = false) } }
    val blocks = remember(parser, text) { parser.update(text) }
    CompositionLocalProvider(LocalMarkdownCacheEnabled provides false) {
        MarkdownBlocks(blocks, modifier, contentCacheKey)
    }
}

@Composable
private fun MarkdownBlocks(blocks: List<MdBlock>, modifier: Modifier, cacheKey: String?) {
    val reading = LocalMarkdownReadingTypography.current ?: chatReadingTypography()
    CompositionLocalProvider(LocalMarkdownReadingTypography provides reading) {
        SelectionContainer {
            Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                blocks.forEachIndexed { index, block ->
                    key(index) { MarkdownBlock(block, cacheKey?.let { "$it:media:$index" }) }
                }
            }
        }
    }
}

@Composable
private fun MarkdownBlock(block: MdBlock, mediaCacheKey: String?) {
    val reading = LocalMarkdownReadingTypography.current ?: chatReadingTypography()
    when (block) {
        is MdParagraph -> InlineText(block.text, reading.body)
        is MdHeading -> InlineText(
            block.text,
            when (block.level) {
                1 -> reading.heading1
                2 -> reading.heading2
                3 -> reading.heading3
                else -> reading.heading4
            },
            // 字重已在 heading* 中设好，勿再叠 Bold，否则标题过重。
            bold = false,
        )
        is MdCodeBlock -> LocalMarkdownComponents.current.codeBlock(block)
        is MdList -> ListBlock(block)
        is MdQuote -> QuoteBlock(block)
        is MdTable -> LocalMarkdownComponents.current.table(block)
        is MdRemoteMedia -> LocalMarkdownComponents.current.media(block, mediaCacheKey)
        is MdHr -> HorizontalDivider(color = RuntimeTheme.colorScheme.outlineVariant)
    }
}

fun markdownBlocksPreview(
    markdown: String,
    contentCacheKey: String? = null,
    largeCacheMinChars: Int = MARKDOWN_CACHE_MAX_CHARS,
): String {
    return resolveMarkdownBlocks(markdown, contentCacheKey, largeCacheMinChars).joinToString("\n") { block ->
        when (block) {
            is MdParagraph -> "P:${block.text}"
            is MdHeading -> "H${block.level}:${block.text}"
            is MdCodeBlock -> "C:${block.language}:${block.code}"
            is MdList -> "L:${block.items.joinToString("|")}"
            is MdQuote -> "Q:${block.lines.joinToString("|")}"
            is MdTable -> "T:${block.headers.joinToString("|")}"
            is MdRemoteMedia -> "M:${block.url}"
            is MdHr -> "HR"
        }
    }
}

fun clearMarkdownCachesForTest() {
    clearParsedMarkdownCaches()
    clearInlineMarkdownCache()
}
