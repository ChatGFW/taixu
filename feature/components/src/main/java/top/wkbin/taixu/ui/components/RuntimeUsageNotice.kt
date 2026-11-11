package top.wkbin.taixu.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import top.yukonga.miuix.kmp.basic.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import top.wkbin.taixu.feature.components.R
/** The same product disclosure is available in first-run setup and Settings. */
@Composable
fun RuntimeUsageNoticeContent(modifier: Modifier = Modifier) {
    val sections = listOf(
        R.string.runtime_notice_actions_title to R.string.runtime_notice_actions_body,
        R.string.runtime_notice_ai_title to R.string.runtime_notice_ai_body,
        R.string.runtime_notice_data_title to R.string.runtime_notice_data_body,
        R.string.runtime_notice_access_title to R.string.runtime_notice_access_body,
    )
    top.yukonga.miuix.kmp.basic.Card(modifier.fillMaxWidth()) {
        sections.forEach { (title, body) ->
            top.yukonga.miuix.kmp.basic.BasicComponent(title = stringResource(title), summary = stringResource(body))
        }
    }
}

@Composable
fun RuntimeUsageNoticeDialog(onDismiss: () -> Unit) {
    RuntimeAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.runtime_notice_title)) },
        text = {
            RuntimeUsageNoticeContent(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()))
        },
        confirmButton = {
            RuntimeTextButton(onClick = onDismiss) { Text(stringResource(R.string.runtime_notice_close)) }
        },
    )
}
