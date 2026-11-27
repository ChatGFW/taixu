package top.wkbin.taixu.ui.workspace

import android.content.Context
import android.graphics.Typeface
import android.os.Build
import android.util.TypedValue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.viewinterop.AndroidView
import io.github.rosemoe.sora.event.ContentChangeEvent
import io.github.rosemoe.sora.lang.EmptyLanguage
import io.github.rosemoe.sora.widget.CodeEditor
import io.github.rosemoe.sora.widget.schemes.EditorColorScheme
import io.github.rosemoe.sora.widget.schemes.SchemeDarcula
import top.wkbin.taixu.ui.miuix.RuntimeTheme

@Composable
internal fun NativeWorkspaceEditor(
    file: EditorSessionController,
    state: EditorFileState,
    wordWrap: Boolean,
    editable: Boolean,
    modifier: Modifier = Modifier,
    onMounted: (CodeEditor) -> Unit,
) {
    val palette = RuntimeTheme.colorScheme
    val dark = palette.surface.luminance() < 0.45f
    val scheme = remember(palette) {
        (if (dark) SchemeDarcula() else EditorColorScheme()).apply {
            setColor(EditorColorScheme.WHOLE_BACKGROUND, palette.surface.toArgb())
            setColor(EditorColorScheme.LINE_NUMBER_BACKGROUND, palette.surfaceContainerLow.toArgb())
            setColor(EditorColorScheme.LINE_NUMBER, palette.onSurfaceVariant.toArgb())
            setColor(EditorColorScheme.TEXT_NORMAL, palette.onSurface.toArgb())
            setColor(EditorColorScheme.CURRENT_LINE, palette.surfaceContainerLow.toArgb())
            setColor(EditorColorScheme.SELECTION_INSERT, palette.primary.toArgb())
            setColor(EditorColorScheme.SELECTED_TEXT_BACKGROUND, palette.primary.copy(alpha = 0.24f).toArgb())
        }
    }
    val highlight = state.characters <= WORKSPACE_HIGHLIGHT_LIMIT
    AndroidView(
        modifier = modifier,
        factory = { context ->
            WorkspaceCodeView(context).apply {
                typefaceText = Typeface.MONOSPACE
                typefaceLineNumber = Typeface.MONOSPACE
                setTextSize(file.textSizeSp)
                setPinLineNumber(true)
                searcher.setCyclicJumping(true)
                subscribeEvent(ContentChangeEvent::class.java) { _, _ ->
                    if (!binding) file.onContentChanged(text)
                }
                onMounted(this)
            }
        },
        update = { view ->
            if (view.colorScheme !== scheme) view.colorScheme = scheme
            if (view.isWordwrap != wordWrap) view.setWordwrap(wordWrap)
            if (view.isEditable != editable) view.isEditable = editable
            if (view.syntax != (highlight to dark)) {
                view.syntax = highlight to dark
                view.setEditorLanguage(if (highlight) WorkspaceSyntaxLanguage(file.extension, dark) else EmptyLanguage())
            }
            if (view.text !== file.content) {
                view.binding = true
                try {
                    view.setText(file.content)
                } finally {
                    view.binding = false
                }
                val x = file.scrollX
                val y = file.scrollY
                if (x != 0 || y != 0) view.post {
                    if (!view.isReleased) {
                        view.scroller.startScroll(x.coerceIn(0, view.scrollMaxX), y.coerceIn(0, view.scrollMaxY), 0, 0, 0)
                        view.scroller.abortAnimation()
                        view.invalidate()
                    }
                }
            }
        },
        onRelease = { view ->
            if (view.text === file.content) {
                file.scrollX = view.offsetX
                file.scrollY = view.offsetY
                val metrics = view.resources.displayMetrics
                file.textSizeSp = if (Build.VERSION.SDK_INT >= 34) {
                    TypedValue.deriveDimension(TypedValue.COMPLEX_UNIT_SP, view.textSizePx, metrics)
                } else {
                    view.textSizePx / TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 1f, metrics)
                }
            }
            view.release()
        },
    )
}

private class WorkspaceCodeView(context: Context) : CodeEditor(context) {
    var binding = false
    var syntax: Pair<Boolean, Boolean>? = null
}
