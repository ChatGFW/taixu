package top.wkbin.taixu.ui.chat

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.sample
import top.wkbin.taixu.core.database.HarnessRuntimeRepository
import top.wkbin.taixu.core.model.SessionRunState
import top.wkbin.taixu.harness.AssistantText
import top.wkbin.taixu.harness.HarnessMessage
import top.wkbin.taixu.harness.compaction.CompactedContext
import top.wkbin.taixu.harness.compaction.CompactionManager
import top.wkbin.taixu.harness.session.SessionControl
import top.wkbin.taixu.harness.session.SessionTreeStore

/** Persisted history follows the main Lane, independently of token-by-token UI updates. */
@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
internal fun observeContextUsageProjection(
    sessions: SessionControl,
    repository: HarnessRuntimeRepository,
    compaction: CompactionManager,
): Flow<CompactedContext?> = retainUsageProjection(sessions.currentSessionId) { sessionId ->
    val leaves = repository.observeLanes(sessionId)
        .map { lanes -> lanes.firstOrNull { it.name == SessionTreeStore.MAIN_LANE }?.leafId }
    val persisted = observeUsageHistory(leaves, compaction.compactionRevision) { compaction.project(sessionId) }
    combine(
        persisted, sessions.messagesForSession(sessionId).sample(200), sessions.sessionRunStates,
    ) { context, live, states ->
        context.copy(messages = contextUsageMessages(context, live, states[sessionId] == SessionRunState.RUNNING))
    }
}

/** Keep one session's last value across subscriber restarts; a session switch clears it. */
@OptIn(ExperimentalCoroutinesApi::class)
internal fun retainUsageProjection(
    sessionIds: Flow<String>,
    project: (String) -> Flow<CompactedContext>,
): Flow<CompactedContext?> {
    var cachedSessionId: String? = null
    var cachedProjection: CompactedContext? = null
    return sessionIds.distinctUntilChanged().flatMapLatest { sessionId ->
        if (cachedSessionId != sessionId) {
            cachedSessionId = sessionId
            cachedProjection = null
        }
        if (sessionId.isBlank()) return@flatMapLatest flowOf(null)
        val projection: Flow<CompactedContext?> = project(sessionId).onEach { cachedProjection = it }
        projection.onStart { emit(cachedProjection) }
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
internal fun observeUsageHistory(
    leaves: Flow<String?>,
    revisions: Flow<Long>,
    project: suspend () -> CompactedContext,
): Flow<CompactedContext> = leaves.distinctUntilChanged()
    .combine(revisions) { leaf, revision -> leaf to revision }
    .mapLatest { project() }

/** Never restore folded transcript history. Merge only the final assistant from this branch. */
internal fun contextUsageMessages(
    context: CompactedContext,
    live: List<HarnessMessage>,
    running: Boolean,
): List<HarnessMessage> {
    val assistant = live.lastOrNull() as? AssistantText ?: return context.messages
    val index = context.messages.indexOfLast { it.id == assistant.id }
    if (index >= 0) return context.messages.toMutableList().also { it[index] = assistant }
    if (!running) return context.messages
    // Require an immediate shared predecessor; a stale transcript from another branch
    // or a history-only window must not be appended to the current projection.
    val predecessor = live.getOrNull(live.lastIndex - 1)?.id ?: return context.messages
    if (context.messages.lastOrNull()?.id != predecessor) return context.messages
    return context.messages + assistant
}
