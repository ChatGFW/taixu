package top.wkbin.taixu.harness.groupchat

import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.produceIn
import kotlinx.coroutines.withTimeoutOrNull
import top.wkbin.taixu.core.common.logging.AppLogger
import top.wkbin.taixu.core.model.SessionRunState
import top.wkbin.taixu.core.model.bot.Bot
import top.wkbin.taixu.core.model.bot.GroupToolStep
import top.wkbin.taixu.harness.AssistantText
import top.wkbin.taixu.harness.HarnessMessage
import top.wkbin.taixu.harness.ToolCall
import top.wkbin.taixu.harness.ToolResult
import top.wkbin.taixu.harness.session.SessionControl

internal data class GroupReplyProgress(
    val content: String,
    val reasoning: String,
    val steps: List<GroupToolStep>,
)

/** Projects provider output without mixing reasoning, transient status, and answer text. */
internal class GroupReplyObserver(
    private val sessionControl: SessionControl,
    private val logger: AppLogger,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    internal data class ReplyOutcome(
        val candidate: AssistantText?,
        val firstTokenTimedOut: Boolean = false,
        val steps: List<GroupToolStep> = emptyList(),
        val replyTimedOut: Boolean = false,
        val reasoning: String = "",
    )

    suspend fun awaitReply(
        sessionId: String,
        bot: Bot,
        baseline: Set<String>,
        startedAt: Long,
        onProgress: suspend (GroupReplyProgress) -> Unit,
    ): ReplyOutcome = coroutineScope {
        var latestWithText: AssistantText? = null
        var latestCandidate: AssistantText? = null
        var latestSteps: List<GroupToolStep> = emptyList()
        var reasoning = ""
        var seenRunning = false
        var lastProgress: GroupReplyProgress? = null
        var firstTokenDeadline = startedAt + FIRST_TOKEN_TIMEOUT_MS
        var deadline = startedAt + REPLY_TIMEOUT_MS
        var previous = startedAt
        var previousState: SessionRunState? = null
        val messageFlow = sessionControl.messagesForSession(sessionId)
        val events = combine(messageFlow, sessionControl.sessionRunStates) { messages, states ->
            messages to (states[sessionId] ?: SessionRunState.IDLE)
        }.distinctUntilChanged().conflate().produceIn(this)
        try {
            var snapshot = events.receive()
            while (true) {
                val now = clock()
                val (messages, state) = snapshot
                if (previousState == SessionRunState.WAITING_APPROVAL) {
                    deadline += (now - previous).coerceAtLeast(0)
                    firstTokenDeadline = now + FIRST_TOKEN_TIMEOUT_MS
                }
                previous = now
                previousState = state
                val candidates = messages.filterIsInstance<AssistantText>()
                    .filter { it.id !in baseline && it.createdAt >= startedAt - CLOCK_SKEW_MS }
                candidates.lastOrNull()?.let { candidate ->
                    latestCandidate = candidate
                    if (candidate.text.isNotBlank()) latestWithText = candidate
                }
                reasoning = candidates.mapNotNull { it.reasoning?.takeIf(String::isNotBlank) }
                    .joinToString("\n\n").take(MAX_REASONING_CHARS)
                latestSteps = toolSteps(messages, baseline, startedAt)
                val progress = GroupReplyProgress(latestWithText?.text.orEmpty(), reasoning, latestSteps)
                if (progress != lastProgress) {
                    onProgress(progress)
                    lastProgress = progress
                }
                if (state == SessionRunState.RUNNING || state == SessionRunState.WAITING_APPROVAL) seenRunning = true
                if (state != SessionRunState.WAITING_APPROVAL && now >= deadline) {
                    sessionControl.cancel(sessionId)
                    return@coroutineScope ReplyOutcome(latestWithText ?: latestCandidate, steps = latestSteps, replyTimedOut = true, reasoning = reasoning)
                }
                // A persisted text response can still consume follow-ups and continue this run.
                val settled = state in TERMINAL_STATES && (seenRunning || now - startedAt >= START_GRACE_MS)
                if (settled) break
                if (state != SessionRunState.WAITING_APPROVAL && latestCandidate == null && now >= firstTokenDeadline) {
                    logger.w("[GroupReplyObserver] Bot ${bot.name} 等待首 token 超时，已请求取消")
                    sessionControl.cancel(sessionId)
                    return@coroutineScope ReplyOutcome(null, firstTokenTimedOut = true, steps = latestSteps, reasoning = reasoning)
                }
                val wakeAt = when {
                    state == SessionRunState.WAITING_APPROVAL -> Long.MAX_VALUE
                    state in TERMINAL_STATES && !seenRunning -> startedAt + START_GRACE_MS
                    latestCandidate == null -> minOf(deadline, firstTokenDeadline)
                    else -> deadline
                }
                // No polling: wait for model/state changes, with timers only for actual deadlines.
                val next = withTimeoutOrNull((wakeAt - clock()).coerceAtLeast(1)) { events.receive() }
                // A receive racing its timeout can discard an event; re-read the authoritative flows.
                snapshot = next ?: (messageFlow.value to
                    (sessionControl.sessionRunStates.value[sessionId] ?: SessionRunState.IDLE))
            }
            ReplyOutcome(latestWithText ?: latestCandidate, steps = latestSteps, reasoning = reasoning)
        } finally {
            events.cancel()
        }
    }

    private fun toolSteps(messages: List<HarnessMessage>, baseline: Set<String>, startedAt: Long): List<GroupToolStep> {
        val results = messages.filterIsInstance<ToolResult>().associateBy { it.toolCallId }
        return messages.filterIsInstance<ToolCall>()
            .filter { it.id !in baseline && it.createdAt >= startedAt - CLOCK_SKEW_MS }
            .map { call ->
                val result = results[call.id]
                GroupToolStep(call.id, call.rawToolName ?: call.tool.name, result?.success, result?.durationMs)
            }
    }

    private companion object {
        const val REPLY_TIMEOUT_MS = 15 * 60 * 1000L
        const val FIRST_TOKEN_TIMEOUT_MS = 120_000L
        const val START_GRACE_MS = 2_000L
        const val CLOCK_SKEW_MS = 2_000L
        const val MAX_REASONING_CHARS = 32_000
        val TERMINAL_STATES = setOf(SessionRunState.COMPLETED, SessionRunState.FAILED, SessionRunState.IDLE)
    }
}
