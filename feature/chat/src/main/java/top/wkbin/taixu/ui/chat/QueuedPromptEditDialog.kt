package top.wkbin.taixu.ui.chat

import androidx.compose.runtime.setValue

import androidx.compose.runtime.getValue

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import top.wkbin.taixu.ui.miuix.RuntimeTheme
import top.yukonga.miuix.kmp.basic.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import top.wkbin.taixu.feature.chat.R
import top.wkbin.taixu.ui.components.RuntimeAlertDialog
import top.wkbin.taixu.ui.components.RuntimeFilledTextField
import top.wkbin.taixu.ui.components.RuntimeTextButton

@Composable
internal fun QueuedPromptEditDialog(editor: QueuedPromptEditor) {
    val state by editor.state.collectAsStateWithLifecycle()
    val edit = state ?: return
    RuntimeAlertDialog(
        onDismissRequest = editor::cancel,
        title = { Text(stringResource(R.string.chat_queue_edit_title)) },
        text = {
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.chat_queue_edit_description), style = RuntimeTheme.typography.bodySmall)
                if (edit.prompt.message.imageUrls.isNotEmpty()) Text(stringResource(R.string.chat_queue_edit_images, edit.prompt.message.imageUrls.size))
                RuntimeFilledTextField(
                    value = edit.text, onValueChange = editor::update, enabled = !edit.busy,
                    modifier = Modifier.fillMaxWidth(), minLines = 3,
                    labelText = stringResource(R.string.chat_queue_edit_content),
                    isError = edit.error != null,
                    supportingText = edit.error?.let { error -> { Text(error) } },
                )
            }
        },
        confirmButton = {
            RuntimeTextButton(onClick = editor::save, enabled = !edit.busy && (edit.text.isNotBlank() || edit.prompt.message.imageUrls.isNotEmpty())) {
                Text(stringResource(R.string.chat_queue_edit_save))
            }
        },
        dismissButton = { RuntimeTextButton(onClick = editor::cancel) { Text(stringResource(R.string.chat_queue_edit_cancel)) } },
    )
}
