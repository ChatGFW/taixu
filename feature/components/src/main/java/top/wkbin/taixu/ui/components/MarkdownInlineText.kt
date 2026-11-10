package top.wkbin.taixu.ui.components

import top.wkbin.taixu.core.markdown.*
import android.util.LruCache
import androidx.compose.foundation.background
import top.wkbin.taixu.ui.miuix.RuntimeTheme
import top.yukonga.miuix.kmp.basic.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.sp

private val inlineSpanCache = object : LruCache<String, AnnotatedString>(512_000) {
    override fun sizeOf(key: String, value: AnnotatedString): Int = key.length + value.length
}
internal val LocalMarkdownCacheEnabled = staticCompositionLocalOf { true }

@Composable
internal fun InlineText(
    text: String,
    style: TextStyle,
    bold: Boolean = false,
    color: Color = Color.Unspecified,
    modifier: Modifier = Modifier,
) {
    val linkColor = RuntimeTheme.colorScheme.primary
    val isDark = RuntimeTheme.colorScheme.surface.luminance() < 0.5f
    val cacheEnabled = LocalMarkdownCacheEnabled.current
    val inlineCodeBg = if (isDark) Color(0xFF21262D) else Color(0xFFEFF1F4)
    val inlineCodeColor = if (isDark) Color(0xFF79C0FF) else Color(0xFF0969DA)
    val annotated = remember(text, bold, linkColor, isDark, cacheEnabled) {
        buildInline(text, bold, linkColor, inlineCodeBg, inlineCodeColor, cacheEnabled)
    }
    Text(annotated, style = style, color = color, modifier = modifier)
}

private fun buildInline(
    text: String,
    forceBold: Boolean,
    linkColor: Color,
    inlineCodeBg: Color,
    inlineCodeColor: Color,
    cacheEnabled: Boolean,
): AnnotatedString {
    val cacheKey = if (cacheEnabled && text.length <= MARKDOWN_CACHE_MAX_CHARS) "$forceBold|${linkColor.value}|${inlineCodeBg.value}|${inlineCodeColor.value}|$text" else null
    if (cacheKey != null) inlineSpanCache.get(cacheKey)?.let { return it }

    val built = buildAnnotatedString {
        if (forceBold) pushStyle(SpanStyle(fontWeight = FontWeight.Bold))
        val linkStyles = TextLinkStyles(
            style = SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline),
            hoveredStyle = SpanStyle(color = linkColor.copy(alpha = 0.8f)),
            pressedStyle = SpanStyle(color = linkColor.copy(alpha = 0.65f)),
        )
        var i = 0
        while (i < text.length) {
            when {
                text.startsWith("~~", i) -> {
                    val end = text.indexOf("~~", i + 2)
                    if (end != -1) {
                        pushStyle(SpanStyle(textDecoration = TextDecoration.LineThrough))
                        append(text, i + 2, end)
                        pop()
                        i = end + 2
                    } else { append(text[i]); i++ }
                }
                text.startsWith("**", i) -> {
                    val end = text.indexOf("**", i + 2)
                    if (end != -1) {
                        pushStyle(SpanStyle(fontWeight = FontWeight.Bold))
                        append(text, i + 2, end)
                        pop()
                        i = end + 2
                    } else { append(text[i]); i++ }
                }
                text[i] == '`' -> {
                    val end = text.indexOf('`', i + 1)
                    if (end != -1) {
                        pushStyle(
                            SpanStyle(
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 12.sp,
                                background = inlineCodeBg,
                                color = inlineCodeColor,
                            ),
                        )
                        append(text, i + 1, end)
                        pop()
                        i = end + 1
                    } else { append(text[i]); i++ }
                }
                text.startsWith("[", i) -> {
                    val link = inlineMarkdownLinkRegex.find(text, i)
                    if (link != null && link.range.first == i) {
                        withLink(LinkAnnotation.Url(link.groupValues[2], linkStyles)) {
                            append(link.groupValues[1])
                        }
                        i = link.range.last + 1
                    } else { append(text[i]); i++ }
                }
                text.regionMatches(i, "https://", 0, 8, ignoreCase = true) ||
                    text.regionMatches(i, "http://", 0, 7, ignoreCase = true) -> {
                    val match = inlineWebUrlRegex.find(text, i)
                    if (match != null && match.range.first == i) {
                        val url = trimUrlPunctuation(match.value)
                        withLink(LinkAnnotation.Url(url, linkStyles)) { append(url) }
                        i += url.length
                    } else { append(text[i]); i++ }
                }
                text[i] == '*' -> {
                    val end = text.indexOf('*', i + 1)
                    if (end != -1 && text.getOrNull(end + 1) != '*') {
                        pushStyle(SpanStyle(fontStyle = FontStyle.Italic))
                        append(text, i + 1, end)
                        pop()
                        i = end + 1
                    } else { append(text[i]); i++ }
                }
                else -> { append(text[i]); i++ }
            }
        }
        if (forceBold) pop()
    }
    if (cacheKey != null) inlineSpanCache.put(cacheKey, built)
    return built
}


internal fun clearInlineMarkdownCache() = inlineSpanCache.evictAll()
