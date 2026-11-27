package top.wkbin.taixu.ui.git

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import top.wkbin.taixu.feature.git.R
import top.wkbin.taixu.ui.components.RuntimeAlertDialog
import top.wkbin.taixu.ui.components.RuntimeCircularProgressIndicator
import top.wkbin.taixu.ui.components.RuntimeTextButton
import top.wkbin.taixu.ui.miuix.RuntimeTheme
import top.yukonga.miuix.kmp.basic.Text

@Composable
internal fun GitChangeDiffDialog(
    change: GitManager.GitFileChange,
    loadDiff: suspend () -> GitChangeDiff,
    onDismiss: () -> Unit,
) {
    val result by produceState<Result<GitChangeDiff>?>(null, change) {
        value = try {
            Result.success(loadDiff())
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            Result.failure(failure)
        }
    }
    RuntimeAlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(stringResource(if (change.staged) R.string.fgit_diff_staged else R.string.fgit_diff_unstaged))
        },
        text = {
            Column(Modifier.fillMaxWidth().heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                Text(change.path, maxLines = 2, overflow = TextOverflow.Ellipsis)
                val diff = result?.getOrNull()
                when {
                    result == null -> RuntimeCircularProgressIndicator()
                    diff == null -> Text(stringResource(R.string.fgit_diff_failed))
                    diff.text.isEmpty() -> Text(stringResource(R.string.fgit_diff_empty))
                    else -> {
                        if (diff.truncated) Text(stringResource(R.string.fgit_diff_truncated))
                        SelectionContainer {
                            Text(
                                text = diff.text,
                                modifier = Modifier.horizontalScroll(rememberScrollState()),
                                style = RuntimeTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                                softWrap = false,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            RuntimeTextButton(onClick = onDismiss) { Text(stringResource(R.string.fgit_diff_close)) }
        },
    )
}
