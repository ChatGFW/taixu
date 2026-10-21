package top.wkbin.taixu.harness.groupchat

import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import top.wkbin.taixu.core.common.logging.AppLogger
import top.wkbin.taixu.core.database.repository.GroupChatRepository
import top.wkbin.taixu.core.model.bot.Bot
import top.wkbin.taixu.core.model.bot.GroupChat
import top.wkbin.taixu.harness.MentionExtractor

internal fun interface GroupRoundResponder {
    suspend fun respond(
        group: GroupChat,
        bot: Bot,
        trigger: String,
        history: String,
        members: List<Bot>,
        imageUrls: List<String>,
        onLateReply: suspend (String) -> Unit,
    ): GroupTurnOutcome
}

internal enum class GroupSlotStatus { WAITING, SPEAKING, POSTED, PASSED, WORKING, FAILED, LEFT }

internal data class GroupRoundSlot(
    val botId: String,
    val name: String,
    var status: GroupSlotStatus = GroupSlotStatus.WAITING,
    var trigger: String? = null,
    var calledBy: String? = null,
)

/**
 * 群聊一轮一次只有一位成员发言。用户消息与 @ 接力共用同一个按群串行的轮次；
 * 每个成员只读到自己的 seen 之后的新消息，回复 [PASS] 时删除占位、不打扰群。
 */
internal class GroupRoundCoordinator(
    private val groups: GroupChatRepository,
    private val queue: GroupReplyQueue,
    private val responder: GroupRoundResponder,
    private val scope: CoroutineScope,
    private val logger: AppLogger,
) {
    private val groupLocks = ConcurrentHashMap<String, Mutex>()
    private val jobs = ConcurrentHashMap<String, MutableSet<Job>>()
    private val lateRounds = ConcurrentHashMap<String, Int>()
    private val cancelledGroups = ConcurrentHashMap.newKeySet<String>()
    private val jobsGuard = Any()

    fun resetLateRounds(groupId: String) {
        lateRounds.remove(groupId)
        cancelledGroups.remove(groupId)
    }

    fun start(group: GroupChat, responders: List<Bot>, trigger: String, imageUrls: List<String> = emptyList()) {
        if (responders.isEmpty()) return
        val job = scope.launch {
            groupLocks.getOrPut(group.id) { Mutex() }.withLock {
                runRound(group.id, responders.map { it.id }.distinct(), trigger, imageUrls)
            }
        }
        synchronized(jobsGuard) {
            jobs.getOrPut(group.id) { ConcurrentHashMap.newKeySet() }.add(job)
        }
        job.invokeOnCompletion {
            synchronized(jobsGuard) {
                jobs[group.id]?.let { set ->
                    set.remove(job)
                    if (set.isEmpty()) jobs.remove(group.id, set)
                }
            }
        }
    }

    fun cancel(groupId: String) {
        cancelledGroups += groupId
        jobs[groupId]?.toList().orEmpty().forEach { it.cancel() }
    }

    private suspend fun runRound(
        groupId: String,
        responderIds: List<String>,
        roundTrigger: String,
        imageUrls: List<String>,
    ) {
        var group = groups.getGroup(groupId) ?: return
        var members = groups.getMembers(groupId)
        if (members.isEmpty()) return
        val slots = responderIds.mapNotNull { id -> members.firstOrNull { it.id == id } }
            .map { GroupRoundSlot(it.id, it.name) }
            .toMutableList()
        if (slots.isEmpty()) return
        val turns = mutableMapOf<String, Int>()
        var index = 0
        while (index < slots.size && index < ROUND_MAX_TOTAL_TURNS) {
            group = groups.getGroup(groupId) ?: return
            members = groups.getMembers(groupId)
            val slot = slots[index]
            val bot = members.firstOrNull { it.id == slot.botId }
            if (bot == null) {
                slot.status = GroupSlotStatus.LEFT
                index += 1
                continue
            }
            if ((turns[bot.id] ?: 0) >= capFor(bot, group)) {
                index += 1
                continue
            }
            slot.status = GroupSlotStatus.SPEAKING
            val seen = groups.getSeenSeq(groupId, bot.id)
            val messages = groups.observeMessages(groupId).first()
            groups.setSeenSeq(groupId, bot.id, messages.maxOfOrNull { it.seq } ?: 0)
            val history = formatRoundHistory(group, messages, seen)
            val trigger = buildTrigger(group, bot, slot, slots, index, roundTrigger)
            val outcome = try {
                queue.withLock(groupId, "${bot.id}:$groupId") {
                    responder.respond(group, bot, trigger, history, members, imageUrls) { text ->
                        lateReply(groupId, slot, text)
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                logger.e("[GroupRound] ${bot.name} 发言失败", failure)
                GroupTurnOutcome("❌ 回复失败：${failure.message}", failed = true)
            }
            turns[bot.id] = (turns[bot.id] ?: 0) + 1
            when {
                outcome.working -> slot.status = GroupSlotStatus.WORKING
                outcome.passed -> slot.status = GroupSlotStatus.PASSED
                outcome.failed -> slot.status = GroupSlotStatus.FAILED
                else -> {
                    slot.status = GroupSlotStatus.POSTED
                    appendMentions(slots, index, outcome.text, members, turns, group)
                }
            }
            index += 1
        }
    }

    private fun appendMentions(
        slots: MutableList<GroupRoundSlot>,
        currentIndex: Int,
        text: String,
        members: List<Bot>,
        turns: Map<String, Int>,
        group: GroupChat,
    ) {
        if (text.isBlank()) return
        val mentionedNames = MentionExtractor.parse(text, members.map { it.name })
        if (mentionedNames.isEmpty()) return
        val speakerId = slots.getOrNull(currentIndex)?.botId
        val mentioned = members.filter { member ->
            member.id != speakerId && mentionedNames.any { it.equals(member.name, ignoreCase = true) }
        }
        var insertAt = currentIndex + 1
        for (member in mentioned) {
            if ((turns[member.id] ?: 0) >= capFor(member, group)) continue
            val waiting = slots.withIndex().firstOrNull { (at, slot) ->
                at > currentIndex && slot.botId == member.id && slot.status == GroupSlotStatus.WAITING
            }
            val slot = if (waiting == null) {
                GroupRoundSlot(member.id, member.name)
            } else {
                slots.removeAt(waiting.index)
            }
            slot.trigger = text.take(TRIGGER_MAX)
            slot.calledBy = speakerId
            slots.add(insertAt.coerceAtMost(slots.size), slot)
            insertAt += 1
        }
    }

    private fun buildTrigger(
        group: GroupChat,
        bot: Bot,
        slot: GroupRoundSlot,
        slots: List<GroupRoundSlot>,
        index: Int,
        roundTrigger: String,
    ): String = buildString {
        appendLine("【群聊轮次 · ${index + 1}/${slots.size}】")
        appendLine("发言顺序：${slots.joinToString(" → ") { it.name + statusSuffix(it.status) }}")
        appendLine(
            "你是 ${bot.name}。" +
                if (index == 0) "本轮由你先说。" else "前面 $index 位已经发言。",
        )
        appendLine(
            if (index == slots.lastIndex) "你是最后一位，说完本轮结束。"
            else "之后还有 ${slots.size - index - 1} 位会看到你的发言。",
        )
        appendLine("只回应群里的最新消息；没有新的补充、或这条消息不是找你的，只回复 [PASS]。")
        appendLine("不要复述已有结论，不要发感谢、确认或进度回声。需要别人接手时用 @名字；需要多步执行就直接做，完成后给出结果。")
        if (slot.botId == group.adminBotId) appendLine("你是本群管理员：可以 @ 其他成员接手；管理员每轮最多发言 4 次。")
        appendLine()
        appendLine("【本次触发】")
        appendLine((slot.trigger ?: roundTrigger).take(TRIGGER_MAX))
    }

    private fun lateReply(groupId: String, slot: GroupRoundSlot, text: String) {
        if (groupId in cancelledGroups) return
        val callerId = slot.calledBy ?: return
        if (callerId == slot.botId) return
        val count = lateRounds.getOrDefault(groupId, 0)
        if (count >= MAX_LATE_ROUNDS) return
        lateRounds[groupId] = count + 1
        scope.launch {
            val group = groups.getGroup(groupId) ?: return@launch
            val caller = groups.getMembers(groupId).firstOrNull { it.id == callerId } ?: return@launch
            start(
                group,
                listOf(caller),
                "【迟到回复】${slot.name} 完成了你交给它的工作：\n${text.take(TRIGGER_MAX)}",
            )
        }
    }

    private fun capFor(bot: Bot, group: GroupChat): Int =
        if (bot.id == group.adminBotId) ADMIN_TURNS_EACH else MEMBER_TURNS_EACH

    private fun statusSuffix(status: GroupSlotStatus): String = when (status) {
        GroupSlotStatus.SPEAKING -> "（你，现在）"
        GroupSlotStatus.POSTED -> "（已发言）"
        GroupSlotStatus.PASSED -> "（已跳过）"
        GroupSlotStatus.WORKING -> "（执行中，完成后回报）"
        GroupSlotStatus.FAILED -> "（失败）"
        GroupSlotStatus.LEFT -> "（已离开）"
        GroupSlotStatus.WAITING -> ""
    }

    private companion object {
        const val MEMBER_TURNS_EACH = 2
        const val ADMIN_TURNS_EACH = 4
        const val TRIGGER_MAX = 1_000
        const val MAX_LATE_ROUNDS = 10
    }
}
