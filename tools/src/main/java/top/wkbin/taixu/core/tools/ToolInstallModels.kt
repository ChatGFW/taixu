package top.wkbin.taixu.core.tools

import android.net.Uri
import top.wkbin.taixu.core.model.ToolManifest

data class ToolInstallProgress(
    val toolId: String,
    val message: String,
    val progress: Float? = null,
    val terminal: Boolean = false,
)

sealed interface LocalPluginImportState {
    data object Idle : LocalPluginImportState
    /** The URI has been selected; only manifest metadata is being read. */
    data class Reading(val fileName: String) : LocalPluginImportState
    data class PendingConfirmation(
        val uri: Uri,
        val fileName: String,
        val manifest: ToolManifest,
        val archiveSizeBytes: Long?,
        val addedCapabilities: List<String> = emptyList(),
        val distroId: String = "",
    ) : LocalPluginImportState
    data class Importing(
        val fileName: String,
        val progress: Float?,
        val bytesRead: Long,
        val totalBytes: Long?,
        val currentEntry: String?,
    ) : LocalPluginImportState
    data class Succeeded(val pluginName: String, val version: String) : LocalPluginImportState
    data class AlreadyImported(val pluginName: String, val version: String) : LocalPluginImportState
    data class Failed(val message: String) : LocalPluginImportState
}
