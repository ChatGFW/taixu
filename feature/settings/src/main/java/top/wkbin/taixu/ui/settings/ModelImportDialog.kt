package top.wkbin.taixu.ui.settings

import top.wkbin.taixu.ui.miuix.RuntimeSpacing
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import top.wkbin.taixu.ui.miuix.RuntimeTheme
import top.wkbin.taixu.ui.components.RuntimeFilledTextField
import top.yukonga.miuix.kmp.basic.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.wkbin.taixu.core.database.AiModelEntity
import top.wkbin.taixu.core.model.AiModelProfileExport
import top.wkbin.taixu.core.model.AiProfileImportMode
import top.wkbin.taixu.core.tools.AiProfileTransferFormat
import top.wkbin.taixu.feature.settings.R
import top.wkbin.taixu.ui.components.*

/** Preview is read-only; only the second confirmation writes the selected batch. */
@Composable
fun ModelImportDialog(
    importing: Boolean = false,
    models: List<AiModelEntity>,
    parseProfiles: (String) -> Result<List<AiModelProfileExport>>,
    onDismiss: () -> Unit,
    onImportJson: (String, AiProfileImportMode) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var jsonText by remember { mutableStateOf("") }
    var profiles by remember { mutableStateOf<List<AiModelProfileExport>?>(null) }
    var mode by remember { mutableStateOf(AiProfileImportMode.COPY) }
    var reading by remember { mutableStateOf(false) }
    var previewing by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val busy = importing || reading || previewing
    val fileError = stringResource(R.string.profile_transfer_read_error)
    val invalidError = stringResource(R.string.profile_transfer_invalid)
    fun replaceInput(value: String) { jsonText = value; profiles = null; mode = AiProfileImportMode.COPY; error = null }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            reading = true
            try {
                val content = withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)?.use(AiProfileTransferFormat::read)
                        ?: throw IllegalArgumentException(fileError)
                }
                replaceInput(content)
            } catch (cancelled: CancellationException) { throw cancelled
            } catch (_: Exception) { error = fileError
            } finally { reading = false }
        }
    }
    RuntimeSelectionSheet(
        onDismissRequest = onDismiss, dismissEnabled = !busy,
        title = stringResource(if (profiles == null) R.string.profile_transfer_import else R.string.profile_transfer_preview),
        actions = {
            RuntimeActionRow(modifier = Modifier.fillMaxWidth(), spacing = top.wkbin.taixu.ui.miuix.RuntimeSpacing.dialogActionGap, scrollable = true) {
            RuntimeTextButton(enabled = !busy, onClick = {
                if (profiles == null) onDismiss() else { profiles = null; error = null }
            }) { Text(stringResource(if (profiles == null) R.string.profile_transfer_cancel else R.string.profile_transfer_edit)) }
            RuntimeButton(enabled = jsonText.isNotBlank() && !busy, onClick = {
                if (profiles != null) onImportJson(jsonText, mode)
                else scope.launch {
                    previewing = true
                    try {
                        withContext(Dispatchers.Default) { parseProfiles(jsonText) }.fold(
                            onSuccess = { profiles = it; error = null },
                            onFailure = { error = it.message ?: invalidError },
                        )
                    } finally { previewing = false }
                }
            }) {
                if (busy) RuntimeCircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                Text(stringResource(if (profiles == null) R.string.profile_transfer_preview else R.string.profile_transfer_confirm))
            }
            }
        },
    ) {
        Column(Modifier.fillMaxWidth().weight(1f, fill = false).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(RuntimeSpacing.cardGap)) {
                val preview = profiles
                if (preview == null) {
                    RuntimeFilledTextField(
                        value = jsonText, onValueChange = ::replaceInput, enabled = !busy,
                        modifier = Modifier.fillMaxWidth(), minLines = 4, maxLines = 8,
                        placeholder = { Text(stringResource(R.string.profile_transfer_paste_hint)) },
                        textStyle = RuntimeTheme.typography.bodyMedium,
                    )
                    RuntimeTextButton(onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                        val value = clipboard?.primaryClip?.getItemAt(0)?.text?.toString().orEmpty()
                        if (value.isNotBlank()) replaceInput(value)
                        else Toast.makeText(context, R.string.profile_transfer_clipboard_empty, Toast.LENGTH_SHORT).show()
                    }, modifier = Modifier.fillMaxWidth(), enabled = !busy) {
                        Text(stringResource(R.string.profile_transfer_paste))
                    }
                    RuntimeTextButton(onClick = { picker.launch(arrayOf("application/json", "text/plain", "*/*")) },
                        modifier = Modifier.fillMaxWidth(), enabled = !busy) {
                        Text(stringResource(R.string.profile_transfer_choose_file))
                    }
                    Text(stringResource(R.string.profile_transfer_limits), style = RuntimeTheme.typography.bodyMedium,
                        color = RuntimeTheme.colorScheme.onSurfaceVariant)
                } else {
                    val existing = models.associateBy { it.id }
                    val matches = preview.count { it.id?.trim() in existing }
                    Text(stringResource(R.string.profile_transfer_summary, preview.size, matches))
                    ImportChoice(mode == AiProfileImportMode.COPY, !busy,
                        stringResource(R.string.profile_transfer_copy), stringResource(R.string.profile_transfer_copy_detail, preview.size)) {
                        mode = AiProfileImportMode.COPY
                    }
                    ImportChoice(mode == AiProfileImportMode.UPDATE_MATCHING_IDS, !busy,
                        stringResource(R.string.profile_transfer_update),
                        stringResource(R.string.profile_transfer_update_detail, matches, preview.size - matches)) {
                        mode = AiProfileImportMode.UPDATE_MATCHING_IDS
                    }
                    Text(stringResource(R.string.profile_transfer_credentials_detail), style = RuntimeTheme.typography.bodyMedium,
                        color = RuntimeTheme.colorScheme.onSurfaceVariant)
                    preview.forEach { profile ->
                        val old = existing[profile.id?.trim()]
                        val update = mode == AiProfileImportMode.UPDATE_MATCHING_IDS && old != null
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(stringResource(if (update) R.string.profile_transfer_row_update else R.string.profile_transfer_row_create,
                                profile.name.ifBlank { profile.model }), maxLines = 1, overflow = TextOverflow.Ellipsis,
                                style = RuntimeTheme.typography.titleSmall)
                            Text(if (update) old.name else profile.provider.ifBlank { "Custom" }, maxLines = 1,
                                overflow = TextOverflow.Ellipsis, style = RuntimeTheme.typography.bodyMedium,
                                color = RuntimeTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                error?.let { Text(it, color = RuntimeTheme.colorScheme.error, style = RuntimeTheme.typography.bodyMedium) }
        }
    }
}

@Composable
private fun ImportChoice(selected: Boolean, enabled: Boolean, title: String, detail: String, onClick: () -> Unit) {
    top.yukonga.miuix.kmp.preference.RadioButtonPreference(
        title = resolveLegacyString(title),
        summary = resolveLegacyString(detail),
        selected = selected,
        enabled = enabled,
        onClick = onClick,
        insideMargin = PaddingValues(horizontal = 0.dp, vertical = 16.dp),
    )
}
