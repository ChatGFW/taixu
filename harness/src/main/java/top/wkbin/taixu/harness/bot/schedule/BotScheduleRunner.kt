package top.wkbin.taixu.harness.bot.schedule

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import top.wkbin.taixu.core.common.logging.AppLogger
import top.wkbin.taixu.core.database.repository.BotScheduleRepository
import top.wkbin.taixu.core.database.repository.PersonaBotRepository
import top.wkbin.taixu.core.model.bot.Bot
import top.wkbin.taixu.core.model.bot.BotScheduleDelivery

fun interface BotScheduledSubmission {
    suspend fun submit(bot: Bot, instruction: String, groupId: String?): Boolean
}

/** Schedules stage durable occurrences before attempting durable Harness admission. */
class BotScheduleRunner(
    private val scheduleRepository: BotScheduleRepository,
    private val botRepository: PersonaBotRepository,
    private val logger: AppLogger,
    private val submit: BotScheduledSubmission,
) {
    private val mutex = Mutex()
    private var recovered = false

    suspend fun recover() = mutex.withLock { recoverOnce() }

    private suspend fun recoverOnce() {
        if (!recovered) {
            scheduleRepository.recoverInterruptedDeliveries()
            recovered = true
        }
    }

    suspend fun runDue(now: Long = System.currentTimeMillis()): Int = mutex.withLock {
        recoverOnce()
        scheduleRepository.listDue(now).take(MAX_DUE_PER_RUN).forEach { schedule ->
            val parsed = runCatching { CronSchedule.parse(schedule.cron).nextAfter(now) }
            if (parsed.isFailure) {
                logger.e("[BotScheduleRunner] 排期 ${schedule.id} 的 cron 表达式无效（${schedule.cron}），已禁用该排期以停止无效重复触发", parsed.exceptionOrNull())
                scheduleRepository.setScheduleEnabled(schedule.id, false)
                return@forEach
            }
            scheduleRepository.stageDelivery(schedule, now, parsed.getOrThrow())
        }
        var admitted = 0
        scheduleRepository.pendingDeliveries(MAX_DUE_PER_RUN).forEach { delivery ->
            if (!scheduleRepository.claimDelivery(delivery.id)) return@forEach
            try {
                val bot = botRepository.getBot(delivery.botId)
                val accepted = bot != null && submit.submit(bot, delivery.instruction, delivery.groupId)
                scheduleRepository.finishDelivery(
                    delivery.id,
                    if (accepted) BotScheduleDelivery.ADMITTED else BotScheduleDelivery.REJECTED,
                    if (accepted) null else "会话未受理，请检查 Bot、群成员和目标会话",
                )
                if (accepted) admitted++
            } catch (cancelled: CancellationException) {
                withContext(NonCancellable) {
                    scheduleRepository.finishDelivery(delivery.id, BotScheduleDelivery.UNKNOWN, "投递被中断，请核对会话记录")
                }
                throw cancelled
            } catch (failure: Exception) {
                // Submission can throw after committing admission. Retrying would risk duplicate effects.
                scheduleRepository.finishDelivery(delivery.id, BotScheduleDelivery.UNKNOWN, "投递结果待核对")
                logger.e("[BotScheduleRunner] 排期 ${delivery.scheduleId} 投递结果未知", failure)
            }
        }
        admitted
    }

    suspend fun nextRunAt(): Long? = scheduleRepository.nextWakeAt()

    private companion object { const val MAX_DUE_PER_RUN = 8 }
}
