package top.wkbin.taixu.harness.groupchat

import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.first
import top.wkbin.taixu.core.common.logging.AppLogger
import top.wkbin.taixu.core.database.repository.GroupChatRepository
import top.wkbin.taixu.core.model.SessionRunState
import top.wkbin.taixu.core.model.bot.Bot
import top.wkbin.taixu.core.model.bot.GroupDelivery
import top.wkbin.taixu.core.model.bot.GroupChat
import top.wkbin.taixu.core.model.bot.GroupMessage
import top.wkbin.taixu.core.model.bot.GroupSenderType
import top.wkbin.taixu.harness.session.PromptSubmission
import top.wkbin.taixu.harness.session.SessionControl

/** Projects a single native turn into the group timeline; admission is recorded before observing output. */
internal class GroupBotResponder(
    private val groupRepository: GroupChatRepository,
    private val prepareSession: suspend (Bot, String) -> String,
    private val sessionControl: SessionControl,
    private val logger: AppLogger,
    private val liveReplies: MutableStateFlow<Map<String, GroupReplyProgress>>,
    private val freshness: GroupReplyFreshness? = null,
    private val lateReplyScope: CoroutineScope? = null,
    private val workGraceMs: Long = WORK_GRACE_MS,
) {
    private val observer = GroupReplyObserver(sessionControl, logger)
    suspend fun respond(
        group: GroupChat,
        bot: Bot,
        trigger: String,
        history: String,
        members: List<Bot>,
        imageUrls: List<String>,
        admission: CompletableDeferred<Boolean>?,
        delegatedTaskId: String? = null,
        deliveryId: String? = null,
        sessionOverride: String? = null,
        onAdmitted: suspend (String?) -> Unit = {},
        silentPass: Boolean = false,
        promptHistory: String? = null,
        onLateReply: (suspend (String) -> Unit)? = null,
    ): GroupTurnOutcome {
        var activeSessionId: String? = null
        var submissionAttempted = false
        var contextBinding: GroupReplyFreshness.Binding? = null
        val placeholderId = deliveryId?.let { "delivery-reply:$it" } ?: UUID.randomUUID().toString()
        val startedAt = System.currentTimeMillis()
        try {
            groupRepository.postMessage(
                GroupMessage(
                    id = placeholderId,
                    groupId = group.id,
                    senderId = bot.id,
                    senderName = bot.name,
                    senderType = GroupSenderType.BOT,
                    content = "",
                    timestamp = startedAt,
                    isStreaming = true,
                    delegatedTaskId = delegatedTaskId,
                ),
            )
            val sessionReady = runCatching { sessionOverride ?: prepareSession(bot, group.id) }
            if (sessionReady.isFailure) {
                val error = sessionReady.exceptionOrNull()
                logger.e("[GroupChatDispatcher] Bot ${bot.name} 群维度会话准备失败", error)
                val text = "❌ 无法准备会话：${error?.message}"
                groupRepository.updateStreamingMessage(placeholderId, text, false)
                if (deliveryId != null) groupRepository.finishDelivery(deliveryId, GroupDelivery.REJECTED, text)
                return GroupTurnOutcome(text, failed = true)
            }
            val sessionId = sessionReady.getOrThrow()
            activeSessionId = sessionId
            currentCoroutineContext().ensureActive()
            val baseline = sessionControl.messagesForSession(sessionId).value.map { it.id }.toSet()
            val baselineOutputRevision = sessionControl.modelOutputRevisions.value[sessionId] ?: 0L
            contextBinding = freshness?.begin(sessionId, group, bot.id, placeholderId)
            // A round passes only unseen lines; other deliveries keep the binding's full view.
            val resolvedHistory = promptHistory ?: contextBinding?.history ?: history
            val prompt = buildGroupPrompt(group, bot, trigger, resolvedHistory, members)
            if (deliveryId != null) {
                check(groupRepository.prepareDelivery(deliveryId, sessionId)) { "收件已停止、成员已移出或来源已清除" }
            }
            if (sessionOverride != null) require(groupRepository.getMembers(group.id).any { it.id == bot.id }) {
                "执行成员已移出来源群聊，未提交任务"
            }
            submissionAttempted = true
            val submission = sessionControl.submit(sessionId, prompt, imageUrls)
            if (deliveryId != null && submission is PromptSubmission.Accepted) {
                check(groupRepository.admitDelivery(deliveryId, sessionId, submission.taskId)) { "群消息受理回执未保存" }
            }
            if (submission is PromptSubmission.Accepted) onAdmitted(submission.taskId)
            currentCoroutineContext().ensureActive()
            admission?.complete(submission is PromptSubmission.Accepted)
            if (submission !is PromptSubmission.Accepted) {
                val reason = (submission as? PromptSubmission.Rejected)?.reason?.name ?: "提交异常"
                val text = "❌ 无法开始回复：$reason"
                groupRepository.updateStreamingMessage(placeholderId, text, false)
                if (deliveryId != null) groupRepository.finishDelivery(deliveryId, GroupDelivery.REJECTED, text)
                return GroupTurnOutcome(text, failed = true)
            }

            var lastCheckpoint = startedAt
            var sawWork = false
            var lastProgress: GroupReplyProgress? = null
            suspend fun observe(completionOnly: Boolean): GroupReplyObserver.ReplyOutcome =
                observer.awaitReply(
                    sessionId, bot, baseline, startedAt, baselineOutputRevision, completionOnly,
                ) { progress ->
                    lastProgress = progress
                    if (progress.steps.isNotEmpty() || progress.content.isNotBlank()) sawWork = true
                    liveReplies.update { it + (placeholderId to progress) }
                    // Smooth UI updates stay in memory; persist only periodic crash-recovery checkpoints.
                    val now = System.currentTimeMillis()
                    if (now - lastCheckpoint >= 2_000) {
                        groupRepository.updateStreamingMessage(placeholderId, progress.content, true, progress.steps, progress.reasoning)
                        lastCheckpoint = now
                    }
                }

            val outcome = if (lateReplyScope != null && onLateReply != null) {
                val first = withTimeoutOrNull(workGraceMs) { observe(completionOnly = false) }
                if (first == null && sawWork && sessionControl.sessionRunStates.value[sessionId] == SessionRunState.RUNNING) {
                    lastProgress?.let { progress ->
                        groupRepository.updateStreamingMessage(
                            placeholderId, progress.content, true, progress.steps, progress.reasoning,
                        )
                    }
                    launchLateReplyWatcher(
                        scope = lateReplyScope,
                        group = group,
                        bot = bot,
                        sessionId = sessionId,
                        baseline = baseline,
                        startedAt = startedAt,
                        baselineOutputRevision = baselineOutputRevision,
                        placeholderId = placeholderId,
                        onLateReply = onLateReply,
                    )
                    return GroupTurnOutcome("", failed = false, working = true, messageId = placeholderId)
                }
                first ?: observe(completionOnly = false)
            } else {
                observe(completionOnly = false)
            }
            val latest = outcome.candidate
            val content = latest?.text?.trim().orEmpty()
            val cleanPass = silentPass && !outcome.failed && !outcome.replyTimedOut &&
                !outcome.firstTokenTimedOut && !outcome.stalled && !outcome.interrupted &&
                isGroupPass(content)
            if (cleanPass) {
                groupRepository.deleteMessage(placeholderId)
                return GroupTurnOutcome("", failed = false, passed = true, messageId = placeholderId)
            }
            val finalText = when {
                outcome.replyTimedOut -> "❌ 本轮超过 ${GroupReplyTimeoutPolicy.REPLY_TIMEOUT_MS / 60_000} 分钟，已请求停止；执行结果待核对，请检查已有工具结果"
                outcome.firstTokenTimedOut ->
                    "❌ ${FIRST_TOKEN_TIMEOUT_MS / 1000} 秒未收到模型正文、推理或工具调用，已请求停止；执行结果待核对"
                outcome.stalled -> "❌ 连续 ${FIRST_TOKEN_TIMEOUT_MS / 1000} 秒无新输出，已请求停止；执行结果待核对，请先检查已有产物"
                outcome.failed -> "❌ 本轮执行失败，请核对会话错误与已有产物" + content.takeIf { it.isNotBlank() }?.let { "\n最后输出：$it" }.orEmpty()
                outcome.interrupted -> "⏹ 本轮已中断，执行结果待核对" + content.takeIf { it.isNotBlank() }?.let { "\n最后输出：$it" }.orEmpty()
                content.isNotBlank() -> content
                latest == null -> "❌ 本轮未产生回复，请稍后重试"
                else -> "（无文本回复）"
            }
            groupRepository.updateStreamingMessage(placeholderId, finalText, false, outcome.steps, outcome.reasoning)
            awaitSettledPlaceholder(group.id, placeholderId)
            return GroupTurnOutcome(
                text = finalText,
                failed = outcome.replyTimedOut || outcome.firstTokenTimedOut || outcome.stalled || outcome.failed || outcome.interrupted || content.isBlank(),
                uncertain = outcome.replyTimedOut || outcome.firstTokenTimedOut || outcome.stalled || outcome.interrupted,
                messageId = placeholderId,
            )
        } catch (cancelled: CancellationException) {
            if (submissionAttempted) activeSessionId?.let { sessionControl.cancel(it) }
            withContext(NonCancellable) {
                val progress = liveReplies.value[placeholderId]
                groupRepository.updateStreamingMessage(placeholderId, STOPPED_MARKER, false, progress?.steps.orEmpty(), progress?.reasoning)
                awaitSettledPlaceholder(group.id, placeholderId)
            }
            throw cancelled
        } catch (failure: Exception) {
            if (submissionAttempted) activeSessionId?.let { sessionControl.cancel(it) }
            admission?.completeExceptionally(failure)
            if (deliveryId != null) groupRepository.finishDelivery(deliveryId,
                if (submissionAttempted) GroupDelivery.UNKNOWN else GroupDelivery.REJECTED,
                if (submissionAttempted) "群消息处理结果待核对，请检查会话记录" else "群消息准备失败，未提交执行")
            logger.e("[GroupChatDispatcher] 群聊回复失败", failure)
            val text = "❌ 回复失败：${failure.message}"
            groupRepository.updateStreamingMessage(placeholderId, text, false)
            awaitSettledPlaceholder(group.id, placeholderId)
            return GroupTurnOutcome(text, failed = true, uncertain = submissionAttempted)
        } finally {
            val binding = contextBinding
            if (binding != null && activeSessionId != null) freshness?.end(activeSessionId, binding)
            liveReplies.update { it - placeholderId }
            admission?.complete(false)
        }
    }

    private fun launchLateReplyWatcher(
        scope: CoroutineScope,
        group: GroupChat,
        bot: Bot,
        sessionId: String,
        baseline: Set<String>,
        startedAt: Long,
        baselineOutputRevision: Long,
        placeholderId: String,
        onLateReply: suspend (String) -> Unit,
    ) {
        scope.launch {
            var lastCheckpoint = System.currentTimeMillis()
            val outcome = try {
                observer.awaitReply(
                    sessionId, bot, baseline, startedAt, baselineOutputRevision, completionOnly = true,
                ) { progress ->
                    liveReplies.update { it + (placeholderId to progress) }
                    val now = System.currentTimeMillis()
                    if (now - lastCheckpoint >= 2_000) {
                        groupRepository.updateStreamingMessage(
                            placeholderId, progress.content, true, progress.steps, progress.reasoning,
                        )
                        lastCheckpoint = now
                    }
                }
            } catch (cancelled: CancellationException) {
                liveReplies.update { it - placeholderId }
                throw cancelled
            } catch (failure: Exception) {
                logger.e("[GroupChatDispatcher] ${bot.name} 迟到回复等待失败", failure)
                null
            }
            val latest = outcome?.candidate?.text?.trim().orEmpty()
            val failed = outcome == null || outcome.replyTimedOut || outcome.failed || outcome.interrupted ||
                (outcome.candidate == null && outcome.steps.isEmpty())
            val finalText = when {
                outcome?.replyTimedOut == true ->
                    "❌ 后台执行超过 ${GroupReplyTimeoutPolicy.REPLY_TIMEOUT_MS / 60_000} 分钟，已请求停止；结果待核对"
                failed -> "❌ 群聊成员的后台执行未能完成" +
                    latest.takeIf { it.isNotBlank() }?.let { "\n最后输出：$it" }.orEmpty()
                latest.isBlank() -> "（工作完成，未补充文字）"
                else -> latest
            }
            val stillStreaming = runCatching {
                groupRepository.observeMessages(group.id).first().any { it.id == placeholderId && it.isStreaming }
            }.getOrDefault(false)
            if (stillStreaming && groupRepository.getGroup(group.id) != null) {
                groupRepository.updateStreamingMessage(
                    placeholderId, finalText, false, outcome?.steps.orEmpty(), outcome?.reasoning.orEmpty(),
                )
                liveReplies.update { it - placeholderId }
                if (!failed) onLateReply(finalText)
            } else {
                liveReplies.update { it - placeholderId }
            }
        }
    }

    private suspend fun awaitSettledPlaceholder(groupId: String, messageId: String) {
        // Keep the live projection until Room emits a settled row, avoiding a blank-frame flash.
        groupRepository.observeMessages(groupId).first { rows -> rows.none { it.id == messageId && it.isStreaming } }
    }

    private companion object {
        const val STOPPED_MARKER = "⏹ 已停止"
        const val FIRST_TOKEN_TIMEOUT_MS = GroupReplyTimeoutPolicy.OUTPUT_WAIT_MS
        const val WORK_GRACE_MS = 15_000L
    }
}
