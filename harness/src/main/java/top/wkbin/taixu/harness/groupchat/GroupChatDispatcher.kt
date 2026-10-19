package top.wkbin.taixu.harness.groupchat

import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.first
import top.wkbin.taixu.core.common.logging.AppLogger
import top.wkbin.taixu.core.database.repository.GroupChatRepository
import top.wkbin.taixu.core.database.repository.PersonaBotRepository
import top.wkbin.taixu.core.model.SessionRunState
import top.wkbin.taixu.core.model.bot.Bot
import top.wkbin.taixu.core.model.bot.GroupAttachment
import top.wkbin.taixu.core.model.bot.GroupChat
import top.wkbin.taixu.core.model.bot.GroupMessage
import top.wkbin.taixu.core.model.bot.GroupSenderType
import top.wkbin.taixu.harness.MentionExtractor
import top.wkbin.taixu.harness.bot.BotOrchestrator
import top.wkbin.taixu.harness.session.PromptSubmission
import top.wkbin.taixu.harness.session.SessionControl

/** 一轮群聊成员回复的结果；主控委派据此判断成员是否真正完成。 */
data class GroupTurnOutcome(val text: String, val failed: Boolean)

/**
 * 群聊消息分流：精确 @ 路由优先，否则交给 Admin Bot；Bot 独占会话的助手文本
 * 流式回写到群消息表。不在群内做 Bot 间自动广播，避免两个 Bot 互相客套死循环。
 */
class GroupChatDispatcher(
    private val groupRepository: GroupChatRepository,
    private val botRepository: PersonaBotRepository,
    private val orchestrator: BotOrchestrator,
    private val sessionControl: SessionControl,
    private val logger: AppLogger,
    private val attachmentPreparer: BotAttachmentPreparer? = null,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
) {
    private val replies = GroupReplyQueue(scope)
    private val observer = GroupReplyObserver(sessionControl, logger)

    private val liveReplies = MutableStateFlow<Map<String, GroupReplyProgress>>(emptyMap())

    fun observeMessages(groupId: String): Flow<List<GroupMessage>> =
        combine(groupRepository.observeMessages(groupId), liveReplies) { messages, live ->
            messages.map { message ->
                val progress = live[message.id]
                if (!message.isStreaming || progress == null) message else message.copy(
                    content = progress.content, reasoning = progress.reasoning, steps = progress.steps,
                )
            }
        }

    fun observeMembers(groupId: String): Flow<List<Bot>> = groupRepository.observeMembers(groupId)

    suspend fun onUserSend(
        groupId: String,
        rawText: String,
        attachments: List<GroupAttachment> = emptyList(),
    ) {
        val text = rawText.trim()
        if (text.isEmpty() && attachments.isEmpty()) return
        val group = groupRepository.getGroup(groupId) ?: return
        val members = groupRepository.getMembers(groupId)
        val prepared = if (attachments.isEmpty()) PreparedGroupAttachments() else
            requireNotNull(attachmentPreparer) { "附件读取服务尚未就绪" }.prepare(attachments)
        groupRepository.postMessage(
            GroupMessage(
                id = UUID.randomUUID().toString(),
                groupId = groupId,
                senderId = USER_SENDER_ID,
                senderName = "我",
                senderType = GroupSenderType.USER,
                content = text,
                timestamp = System.currentTimeMillis(),
                attachments = attachments,
            ),
        )
        if (members.isEmpty()) {
            postSystemNotification(groupId, "⚠️ 当前群聊暂无可响应的 Bot 成员")
            return
        }
        val mentions = MentionExtractor.parse(text, members.map { it.name })
        val targeted = members.filter { bot -> mentions.any { it.equals(bot.name, ignoreCase = true) } }
        val responders = targeted.ifEmpty {
            listOfNotNull(members.firstOrNull { it.id == group.adminBotId } ?: members.firstOrNull())
        }
        val history = formatGroupHistory(
            group,
            runCatching { groupRepository.observeMessages(groupId).first() }.getOrDefault(emptyList()),
        )
        val trigger = if (attachments.isEmpty()) text else buildString {
            appendLine(text.ifBlank { "（用户发送了附件，请查看并处理）" })
            appendLine()
            appendLine("【附件】")
            appendLine(prepared.description)
        }
        responders.forEach { bot -> dispatchToBot(group, bot, trigger, history, members, prepared.imageUrls) }
    }

    /**
     * 定时任务入口：有群聊时按普通群消息回复（含流式占位），没有群聊则直接投递到 Bot 会话。
     */
    suspend fun triggerScheduled(bot: Bot, instruction: String, groupId: String?): Boolean {
        val trigger = "【定时任务】$instruction"
        val group = if (groupId != null) {
            groupRepository.getGroup(groupId) ?: return false
        } else groupRepository.observeGroupsForBot(bot.id).first().firstOrNull()
        if (group != null) {
            val history = runCatching { groupRepository.observeMessages(group.id).first() }
                .getOrDefault(emptyList())
            if (groupRepository.getMembers(group.id).none { it.id == bot.id }) return false
            val admitted = CompletableDeferred<Boolean>()
            val job = dispatchToBot(group, bot, trigger, formatGroupHistory(group, history), groupRepository.getMembers(group.id), admission = admitted)
            return try { admitted.await() } catch (cancelled: CancellationException) {
                if (!admitted.isCompleted) job?.cancel()
                throw cancelled
            }
        }
        orchestrator.ensureSession(bot)
        return sessionControl.submit(bot.sessionId, trigger) is PromptSubmission.Accepted
    }

    suspend fun postSystemNotification(groupId: String, text: String, taskId: String? = null) {
        groupRepository.postMessage(
            GroupMessage(
                id = UUID.randomUUID().toString(),
                groupId = groupId,
                senderId = SYSTEM_SENDER_ID,
                senderName = "系统通知",
                senderType = GroupSenderType.SYSTEM,
                content = text,
                timestamp = System.currentTimeMillis(),
                delegatedTaskId = taskId,
            ),
        )
    }

    /**
     * 主控把任务交给成员在群聊**前台**执行：成员以自己的头像/会话流式输出与工具步骤，
     * 不再走后台子智能体 Lane。挂起等待本轮结束并返回结果，供 wait=true 的委派直接验收。
     */
    suspend fun runAssignment(
        group: GroupChat,
        target: Bot,
        requester: Bot,
        task: String,
        taskId: String,
        writePaths: List<String> = emptyList(),
    ): GroupTurnOutcome {
        val history = runCatching { groupRepository.observeMessages(group.id).first() }
            .getOrDefault(emptyList())
        val members = groupRepository.getMembers(group.id)
        val trigger = buildString {
            appendLine("【主控 ${requester.name} 委派】")
            appendLine("请以你在本群的身份直接执行并实时回复进度与结果，不要只给计划；")
            appendLine("需要时可以调用你的工具，完成后给出可验收的结论。")
            if (writePaths.isNotEmpty()) {
                appendLine("建议写入范围：${writePaths.joinToString("、")}")
            }
            appendLine()
            appendLine(task)
        }
        val result = CompletableDeferred<GroupTurnOutcome>()
        val job = replies.launch(group.id, "${target.id}:${group.id}") {
            result.complete(
                respond(
                    group = group,
                    bot = target,
                    trigger = trigger,
                    history = formatGroupHistory(group, history),
                    members = members,
                    imageUrls = emptyList(),
                    admission = null,
                    delegatedTaskId = taskId,
                ),
            )
        }
        if (job == null) {
            return GroupTurnOutcome("❌ 群聊正在停止，任务未开始", failed = true)
        }
        job.invokeOnCompletion { cause ->
            if (cause != null && !result.isCompleted) result.completeExceptionally(cause)
        }
        return try {
            result.await()
        } catch (cancelled: CancellationException) {
            job.cancel()
            throw cancelled
        }
    }

    /**
     * 委派完成后唤醒主控验收：结果已作为成员消息落在群里，主控直接读群记录并决定汇总/补派。
     */
    suspend fun triggerAssignmentReport(orchestratorBot: Bot, groupId: String, report: String): Boolean {
        val group = groupRepository.getGroup(groupId) ?: return false
        val trigger = buildString {
            appendLine("【成员委派结果】")
            appendLine(report)
            appendLine()
            appendLine("请核对是否满足原任务：满足则向群聊简要汇总；不满足则说明缺口并再次派发。")
        }
        val history = runCatching { groupRepository.observeMessages(groupId).first() }
            .getOrDefault(emptyList())
        dispatchToBot(
            group = group,
            bot = orchestratorBot,
            trigger = trigger,
            history = formatGroupHistory(group, history),
            members = groupRepository.getMembers(groupId),
        )
        return true
    }

    /**
     * 停止群内所有正在运行的成员会话，并把流式占位改为“已停止”。
     * 后台委派由 [top.wkbin.taixu.harness.bot.BotAssignmentTracker] 单独取消。
     */
    suspend fun cancelGroup(groupId: String) {
        replies.cancel(groupId)
        val states = sessionControl.sessionRunStates.value
        val streaming = groupRepository.observeMessages(groupId).first().filter { it.isStreaming }
        val streamingSenders = streaming.map { it.senderId }.toSet()
        groupRepository.getMembers(groupId).forEach { bot ->
            val sessionIds = buildList {
                addAll(orchestrator.groupSessionId(bot.id, groupId)?.let(::listOf).orEmpty())
            }.distinct()
            val running = sessionIds.any { sessionId ->
                val state = states[sessionId]
                state == SessionRunState.RUNNING || state == SessionRunState.WAITING_APPROVAL
            }
            if (running || bot.id in streamingSenders) {
                sessionIds.forEach { sessionId ->
                    sessionControl.cancel(sessionId)
                }
            }
        }
        streaming.forEach { groupRepository.updateStreamingMessage(it.id, STOPPED_MARKER, false, it.steps) }
    }

    private fun dispatchToBot(
        group: GroupChat,
        bot: Bot,
        trigger: String,
        history: String,
        members: List<Bot>,
        imageUrls: List<String> = emptyList(),
        admission: CompletableDeferred<Boolean>? = null,
    ): kotlinx.coroutines.Job? {
        // 同一 Bot 在不同群可以并行；锁按 (bot, group) 维度隔离。
        val job = replies.launch(group.id, "${bot.id}:${group.id}") {
            respond(group, bot, trigger, history, members, imageUrls, admission)
        }
        if (job == null) admission?.complete(false)
        job?.invokeOnCompletion { admission?.complete(false) }
        return job
    }

    private suspend fun respond(
        group: GroupChat,
        bot: Bot,
        trigger: String,
        history: String,
        members: List<Bot>,
        imageUrls: List<String>,
        admission: CompletableDeferred<Boolean>?,
        delegatedTaskId: String? = null,
    ): GroupTurnOutcome {
        var activeSessionId: String? = null
        val placeholderId = UUID.randomUUID().toString()
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
            val sessionReady = runCatching { orchestrator.ensureGroupSession(bot, group.id) }
            if (sessionReady.isFailure) {
                val error = sessionReady.exceptionOrNull()
                logger.e("[GroupChatDispatcher] Bot ${bot.name} 群维度会话准备失败", error)
                val text = "❌ 无法准备会话：${error?.message}"
                groupRepository.updateStreamingMessage(placeholderId, text, false)
                return GroupTurnOutcome(text, failed = true)
            }
            val sessionId = sessionReady.getOrThrow()
            activeSessionId = sessionId
            currentCoroutineContext().ensureActive()
            val baseline = sessionControl.messagesForSession(sessionId).value.map { it.id }.toSet()
            val prompt = buildGroupPrompt(group, bot, trigger, history, members)
            val submission = sessionControl.submit(sessionId, prompt, imageUrls)
            currentCoroutineContext().ensureActive()
            admission?.complete(submission is PromptSubmission.Accepted)
            if (submission !is PromptSubmission.Accepted) {
                val reason = (submission as? PromptSubmission.Rejected)?.reason?.name ?: "提交异常"
                val text = "❌ 无法开始回复：$reason"
                groupRepository.updateStreamingMessage(placeholderId, text, false)
                return GroupTurnOutcome(text, failed = true)
            }

            var lastCheckpoint = startedAt
            val outcome = observer.awaitReply(sessionId, bot, baseline, startedAt) { progress ->
                liveReplies.update { it + (placeholderId to progress) }
                // Smooth UI updates stay in memory; persist only periodic crash-recovery checkpoints.
                val now = System.currentTimeMillis()
                if (now - lastCheckpoint >= 2_000) {
                    groupRepository.updateStreamingMessage(placeholderId, progress.content, true, progress.steps, progress.reasoning)
                    lastCheckpoint = now
                }
            }
            val latest = outcome.candidate
            val content = latest?.text?.trim().orEmpty()
            val finalText = when {
                outcome.replyTimedOut -> "❌ 回复超时，已停止，请核对已执行的工具结果"
                outcome.firstTokenTimedOut ->
                    "❌ 模型无响应，已停止（${FIRST_TOKEN_TIMEOUT_MS / 1000} 秒未收到首 token）"
                content.isNotBlank() -> content
                latest == null -> "❌ 本轮未产生回复，请稍后重试"
                else -> "（无文本回复）"
            }
            groupRepository.updateStreamingMessage(placeholderId, finalText, false, outcome.steps, outcome.reasoning)
            awaitSettledPlaceholder(group.id, placeholderId)
            return GroupTurnOutcome(
                text = finalText,
                failed = outcome.replyTimedOut || outcome.firstTokenTimedOut || latest == null,
            )
        } catch (cancelled: CancellationException) {
            activeSessionId?.let { sessionControl.cancel(it) }
            withContext(NonCancellable) {
                val progress = liveReplies.value[placeholderId]
                groupRepository.updateStreamingMessage(placeholderId, STOPPED_MARKER, false, progress?.steps.orEmpty(), progress?.reasoning)
                awaitSettledPlaceholder(group.id, placeholderId)
            }
            throw cancelled
        } catch (failure: Exception) {
            activeSessionId?.let { sessionControl.cancel(it) }
            admission?.completeExceptionally(failure)
            logger.e("[GroupChatDispatcher] 群聊回复失败", failure)
            val text = "❌ 回复失败：${failure.message}"
            groupRepository.updateStreamingMessage(placeholderId, text, false)
            awaitSettledPlaceholder(group.id, placeholderId)
            return GroupTurnOutcome(text, failed = true)
        } finally {
            liveReplies.update { it - placeholderId }
            admission?.complete(false)
        }
    }

    private suspend fun awaitSettledPlaceholder(groupId: String, messageId: String) {
        // Keep the live projection until Room emits a settled row, avoiding a blank-frame flash.
        groupRepository.observeMessages(groupId).first { rows -> rows.none { it.id == messageId && it.isStreaming } }
    }

    private companion object {
        const val USER_SENDER_ID = "user_me"
        const val SYSTEM_SENDER_ID = "system"
        const val STOPPED_MARKER = "⏹ 已停止"
        const val FIRST_TOKEN_TIMEOUT_MS = 120_000L
    }
}
