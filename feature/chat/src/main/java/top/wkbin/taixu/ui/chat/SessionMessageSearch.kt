package top.wkbin.taixu.ui.chat

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.isActive
import top.wkbin.taixu.core.database.MessageSearchHitRow

internal data class SessionMessageSearchState(
    val query: String = "",
    val loading: Boolean = false,
    val failed: Boolean = false,
    val hits: List<MessageSearchHitRow> = emptyList(),
)

/** One current query owns publication, even when a storage adapter ignores cancellation. */
internal class SessionMessageSearch(
    private val scope: CoroutineScope,
    private val search: suspend (String) -> List<MessageSearchHitRow>,
    private val debounceMillis: Long = 250,
) {
    private var generation = 0L
    private var job: Job? = null
    private val mutableState = MutableStateFlow(SessionMessageSearchState())
    val state = mutableState.asStateFlow()

    fun submit(query: String) {
        val term = query.trim()
        val request = ++generation
        job?.cancel()
        mutableState.value = SessionMessageSearchState(term, loading = term.isNotEmpty())
        if (term.isEmpty()) return
        job = scope.launch {
            try {
                delay(debounceMillis)
                val hits = search(term)
                currentCoroutineContext().ensureActive()
                if (generation == request) mutableState.value = SessionMessageSearchState(term, hits = hits)
            } catch (cancelled: CancellationException) {
                // A storage-owned cancellation must not leave an active query spinning forever.
                if (currentCoroutineContext().isActive && generation == request) {
                    mutableState.value = SessionMessageSearchState(term, failed = true)
                }
                throw cancelled
            }
            catch (_: Exception) {
                if (generation == request) mutableState.value = SessionMessageSearchState(term, failed = true)
            }
        }
    }

    fun cancel() { generation++; job?.cancel(); mutableState.value = SessionMessageSearchState() }
}

/** Literal, case-insensitive matching. Percent/underscore and regex symbols have no special meaning. */
internal fun literalSearchRanges(text: String, query: String): List<IntRange> {
    val term = query.trim().takeIf { it.isNotEmpty() } ?: return emptyList()
    val result = mutableListOf<IntRange>()
    var offset = 0
    while (offset <= text.length - term.length) {
        val match = text.indexOf(term, offset, ignoreCase = true)
        if (match < 0) break
        result += match until match + term.length
        offset = match + term.length
    }
    return result
}
