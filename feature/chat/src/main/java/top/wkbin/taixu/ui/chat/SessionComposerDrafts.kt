package top.wkbin.taixu.ui.chat

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

internal data class ComposerDraft<A>(val text: String = "", val attachments: List<A> = emptyList(), val sharedText: String? = null)
internal data class ComposerSharedText(val sessionId: String, val text: String)
internal data class ScopedComposerDraft<A>(val sessionId: String, val draft: ComposerDraft<A>)
internal class ComposerImportLease { internal var completed = false }

/** Main-thread owned drafts. Every async import carries an explicit owner until completion. */
internal class SessionComposerDrafts<A>(
    private val sessions: StateFlow<String>,
    scope: CoroutineScope,
    private val persistence: ComposerDraftTextState,
    private val release: (A) -> Unit,
    private val onSnapshotLimited: () -> Unit = {},
) {
    private val restored = persistence.restore()
    private var owner = sessions.value.ifBlank { restored.activeSessionId }
    private val drafts = LinkedHashMap<String, ComposerDraft<A>>().apply {
        restored.texts.forEach { (id, text) -> put(id, ComposerDraft(text)) }
    }
    private val imports = mutableMapOf<ComposerImportLease, String>()
    private val discarded = mutableSetOf<String>()
    private var initialOwner: String? = null
    private var limitedNotified = false
    private val mutableText = MutableStateFlow(drafts[owner]?.text.orEmpty())
    private val mutableAttachments = MutableStateFlow(drafts[owner]?.attachments.orEmpty())
    private val mutableProcessing = MutableStateFlow(false)
    private val mutableShared = MutableStateFlow<ComposerSharedText?>(null)
    val text = mutableText.asStateFlow()
    val attachments = mutableAttachments.asStateFlow()
    val processing = mutableProcessing.asStateFlow()
    val sharedText = mutableShared.asStateFlow()

    init {
        if (owner.isNotBlank()) adoptUnassigned(owner)
        publish()
        persist()
        scope.launch { sessions.collect { if (it.isNotBlank() && it != owner) select(it) } }
    }

    fun snapshot(): ScopedComposerDraft<A> {
        sync()
        return ScopedComposerDraft(owner, drafts[owner] ?: ComposerDraft())
    }

    fun setText(value: String, sessionId: String = snapshot().sessionId) = change(sessionId) {
        it.copy(text = value, sharedText = it.sharedText.takeIf { value.isNotEmpty() })
    }
    fun share(value: String, sessionId: String = snapshot().sessionId) = change(sessionId) { it.copy(text = value, sharedText = value) }
    fun dismissShare(sessionId: String = snapshot().sessionId) = change(sessionId) { it.copy(sharedText = null) }
    fun addAttachments(items: List<A>, sessionId: String = snapshot().sessionId) {
        val target = resolve(sessionId)
        if (target in discarded) { items.forEach(release); return }
        change(target) { it.copy(attachments = it.attachments + items) }
    }
    fun removeAttachment(item: A, sessionId: String = snapshot().sessionId) {
        val target = resolve(sessionId)
        val existing = drafts[target]?.attachments.orEmpty()
        if (item !in existing) return
        change(target) { it.copy(attachments = it.attachments.filterNot { attachment -> attachment == item }) }
        release(item)
    }
    /** Ownership transfers to the submitted message; do not delete its backing files. */
    fun detachAttachments(sessionId: String): List<A> {
        val target = resolve(sessionId)
        val items = drafts[target]?.attachments.orEmpty()
        change(target) { it.copy(attachments = emptyList()) }
        return items
    }

    fun beginImport(sessionId: String = snapshot().sessionId): ComposerImportLease {
        sync()
        val target = resolve(sessionId)
        val lease = ComposerImportLease()
        if (target !in discarded) imports[lease] = target
        publish()
        return lease
    }
    fun finishImport(lease: ComposerImportLease, items: List<A>): Boolean {
        if (lease.completed) return false
        lease.completed = true
        val target = imports.remove(lease)
        if (target == null || target in discarded) {
            items.forEach(release)
            publish()
            return false
        }
        addAttachments(items, target)
        publish()
        return true
    }

    fun discardSession(sessionId: String) {
        discarded += sessionId
        imports.entries.removeAll { it.value == sessionId }
        val previous = drafts.remove(sessionId)
        if (owner == sessionId) owner = ""
        publish()
        persist()
        previous?.attachments?.forEach(release)
    }

    private fun sync() {
        val current = sessions.value
        if (current.isNotBlank() && current != owner && current !in discarded) select(current)
    }
    private fun select(sessionId: String) {
        if (sessionId in discarded) return
        if (owner.isBlank()) adoptUnassigned(sessionId)
        owner = sessionId
        drafts.remove(owner)?.let { drafts[owner] = it }
        publish()
        persist()
    }
    private fun adoptUnassigned(sessionId: String) {
        initialOwner = sessionId
        imports.entries.filter { it.value.isBlank() }.forEach { it.setValue(sessionId) }
        val unassigned = drafts.remove("") ?: return
        val existing = drafts[sessionId]
        drafts[sessionId] = if (existing == null) unassigned else existing.copy(
            text = listOf(existing.text, unassigned.text).filter { it.isNotEmpty() }.joinToString("\n\n"),
            attachments = existing.attachments + unassigned.attachments, sharedText = unassigned.sharedText ?: existing.sharedText)
    }
    private fun resolve(sessionId: String): String = sessionId.ifBlank {
        initialOwner?.takeUnless { it in discarded } ?: owner
    }
    private fun change(sessionId: String, transform: (ComposerDraft<A>) -> ComposerDraft<A>) {
        val target = resolve(sessionId)
        if (target in discarded) return
        val next = transform(drafts[target] ?: ComposerDraft())
        drafts.remove(target)
        drafts[target] = next
        sync()
        publish()
        persist()
    }
    private fun publish() {
        val current = drafts[owner] ?: ComposerDraft()
        mutableText.value = current.text
        mutableAttachments.value = current.attachments
        mutableProcessing.value = imports.values.any { it == owner }
        mutableShared.value = current.sharedText?.let { ComposerSharedText(owner, it) }
    }
    private fun persist() {
        val chosen = mutableSetOf<String>()
        var remaining = SNAPSHOT_CHAR_BUDGET
        val candidates = listOf(owner) + drafts.keys.toList().asReversed().filter { it != owner }
        var limited = false
        for (id in candidates) {
            val value = drafts[id]?.text?.takeIf { it.isNotEmpty() } ?: continue
            val weight = id.length.toLong() + value.length
            if (weight > remaining || chosen.size >= SNAPSHOT_MAX_SESSIONS || id.length > 256) { limited = true; continue }
            chosen += id
            remaining -= weight.toInt()
        }
        persistence.save(ComposerTextSnapshot(owner, drafts.filterKeys { it in chosen }.mapValues { it.value.text }))
        if (limited && !limitedNotified) onSnapshotLimited()
        limitedNotified = limited
    }
    internal companion object {
        const val SNAPSHOT_CHAR_BUDGET = 32_768
        const val SNAPSHOT_MAX_SESSIONS = 64
    }
}
