package top.wkbin.taixu.ui.workspace

import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import io.github.rosemoe.sora.lang.EmptyLanguage
import io.github.rosemoe.sora.lang.analysis.SimpleAnalyzeManager
import io.github.rosemoe.sora.lang.styling.MappedSpans
import io.github.rosemoe.sora.lang.styling.SpanFactory
import io.github.rosemoe.sora.lang.styling.Styles
import io.github.rosemoe.sora.lang.styling.TextStyle
import io.github.rosemoe.sora.lang.styling.span.SpanConstColorResolver
import io.github.rosemoe.sora.lang.styling.span.SpanExtAttrs
import io.github.rosemoe.sora.widget.schemes.EditorColorScheme

internal const val WORKSPACE_HIGHLIGHT_LIMIT = 500 * 1024

internal class WorkspaceSyntaxLanguage(extension: String, isDark: Boolean) : EmptyLanguage() {
    private val analyzer = object : SimpleAnalyzeManager<Unit>() {
        override fun analyze(text: StringBuilder, delegate: Delegate<Unit>): Styles {
            if (delegate.isCancelled || text.length > WORKSPACE_HIGHLIGHT_LIMIT) return Styles()
            // Sora's columns exclude line separators; analysis uses the same logical lines
            // without touching the file buffer or the separators that will be saved.
            val source = text.toString().replace("\r\n", "\n").replace('\r', '\n')
            val styles = SyntaxHighlighter.highlight(source, extension, isDark).spanStyles.drop(1)
            val spans = MappedSpans.Builder()
            var line = 0
            var lineStart = 0
            var offset = 0

            fun addSpan(position: Int, style: SpanStyle?) {
                while (offset < position) {
                    if (source[offset++] == '\n') {
                        line++
                        lineStart = offset
                    }
                }
                val span = if (style == null) {
                    SpanFactory.obtainNoExt(position - lineStart, EditorColorScheme.TEXT_NORMAL.toLong())
                } else {
                    val flags = TextStyle.makeStyle(
                        EditorColorScheme.TEXT_NORMAL, 0,
                        (style.fontWeight ?: FontWeight.Normal) >= FontWeight.SemiBold,
                        style.fontStyle == FontStyle.Italic,
                        false,
                    )
                    SpanFactory.obtain(position - lineStart, flags).apply {
                        setSpanExt(SpanExtAttrs.EXT_COLOR_RESOLVER, SpanConstColorResolver(style.color.toArgb()))
                    }
                }
                spans.add(line, span)
            }

            styles.forEachIndexed { index, range ->
                if (delegate.isCancelled) return Styles()
                addSpan(range.start, range.item)
                if (styles.getOrNull(index + 1)?.start != range.end) addSpan(range.end, null)
            }
            while (offset < source.length) {
                if (source[offset++] == '\n') line++
            }
            spans.determine(line)
            return Styles(spans.build())
        }
    }

    override fun getAnalyzeManager() = analyzer
}
