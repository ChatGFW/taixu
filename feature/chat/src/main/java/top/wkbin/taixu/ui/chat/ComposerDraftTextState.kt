package top.wkbin.taixu.ui.chat

import android.os.Bundle
import androidx.lifecycle.SavedStateHandle

internal data class ComposerTextSnapshot(val activeSessionId: String = "", val texts: Map<String, String> = emptyMap())
internal interface ComposerDraftTextState {
    fun restore(): ComposerTextSnapshot
    fun save(snapshot: ComposerTextSnapshot)
}

/** Only bounded text enters the system state bundle; attachments stay in the ViewModel. */
internal class SavedComposerDraftTextState(private val saved: SavedStateHandle, initialSessionId: String) : ComposerDraftTextState {
    private val initialOwner = initialSessionId
    @Suppress("DEPRECATION")
    override fun restore(): ComposerTextSnapshot {
        val bundle = runCatching { saved.get<Any>(KEY) as? Bundle }.getOrNull()
        val legacy = runCatching { saved.remove<Any>(LEGACY_KEY) as? String }.getOrNull()
        if (bundle == null) return ComposerTextSnapshot(initialOwner,
            legacy?.takeIf { it.isNotEmpty() }?.let { mapOf(initialOwner to it) }.orEmpty())
        return runCatching {
            val ids = bundle.get("ids") as? List<*> ?: emptyList<Any?>()
            val texts = bundle.get("texts") as? List<*> ?: emptyList<Any?>()
            val restored = ids.zip(texts).mapNotNull { (id, text) ->
                if (id is String && text is String) id to text else null
            }.toMap()
            ComposerTextSnapshot(bundle.get("owner") as? String ?: "", restored)
        }.getOrDefault(ComposerTextSnapshot(initialOwner))
    }
    override fun save(snapshot: ComposerTextSnapshot) {
        saved[KEY] = Bundle().apply {
            putString("owner", snapshot.activeSessionId.takeIf { it.length <= 256 }.orEmpty())
            putStringArrayList("ids", ArrayList(snapshot.texts.keys))
            putStringArrayList("texts", ArrayList(snapshot.texts.values))
        }
    }
    internal companion object {
        const val KEY = "chat_session_drafts_v1"
        const val LEGACY_KEY = "chat_input_draft"
    }
}
