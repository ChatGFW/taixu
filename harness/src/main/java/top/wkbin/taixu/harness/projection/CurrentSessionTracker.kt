package top.wkbin.taixu.harness.projection

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Shared foreground identity and ordering for pending selections and published projections. */
class CurrentSessionTracker {
    private val selectionLock = Any()
    private var requestedSelection = 0L
    private var publishedSelection = 0L
    private val mutableCurrentSessionId = MutableStateFlow("")
    val currentSessionId: StateFlow<String> = mutableCurrentSessionId.asStateFlow()
    val foregroundId: String get() = mutableCurrentSessionId.value

    fun isForeground(sessionId: String): Boolean = sessionId == foregroundId

    /** Preparing a new conversation keeps the currently published conversation usable. */
    fun reserveSelection(): Long = synchronized(selectionLock) { ++requestedSelection }

    fun select(sessionId: String): Long = synchronized(selectionLock) {
        val selection = ++requestedSelection
        publishedSelection = selection
        mutableCurrentSessionId.value = sessionId
        selection
    }

    fun selectIfLatest(selection: Long, sessionId: String): Boolean = synchronized(selectionLock) {
        if (requestedSelection != selection) return@synchronized false
        publishedSelection = selection
        mutableCurrentSessionId.value = sessionId
        // Unconfined observers can synchronously request another selection during publication.
        publishedSelection == selection && mutableCurrentSessionId.value == sessionId
    }

    fun isSelectionCurrent(selection: Long, sessionId: String): Boolean = synchronized(selectionLock) {
        publishedSelection == selection && mutableCurrentSessionId.value == sessionId
    }

    fun setCurrent(sessionId: String) {
        select(sessionId)
    }
}
