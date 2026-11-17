package top.wkbin.taixu.harness.bot.inbox

import kotlinx.coroutines.CancellationException
import top.wkbin.taixu.core.common.logging.AppLogger
import top.wkbin.taixu.core.database.repository.BotInboxRepository
import top.wkbin.taixu.core.model.bot.BotInboxItem
import top.wkbin.taixu.core.model.bot.BotInboxState

/**
 * Inbox 投影的写入端（ADR-0007）。
 *
 * 投影是派生事实：任何写失败只记日志，绝不阻断原始投递。Observation 只在内容真正进入
 * 某一轮时由调用方显式调用，不许把「入队/通知/UI 看过」当成 Bot 看到。
 */
class BotInboxProjection(
    private val repository: BotInboxRepository,
    private val logger: AppLogger,
) {
    suspend fun admit(
        botId: String,
        sourceKind: String,
        sourceId: String,
        reason: String,
        sessionId: String? = null,
        priority: Int = 0,
        now: Long = System.currentTimeMillis(),
    ): Boolean = guard("admit $sourceKind/$sourceId") {
        repository.admit(
            BotInboxItem(
                id = "$botId:$sourceKind:$sourceId",
                botId = botId,
                sourceKind = sourceKind,
                sourceId = sourceId,
                sessionId = sessionId,
                reason = reason,
                priority = priority,
                createdAt = now,
            ),
        )
    } ?: false

    suspend fun observe(botId: String, sourceKind: String, sourceId: String, now: Long = System.currentTimeMillis()) =
        decide(botId, sourceKind, sourceId, BotInboxState.OBSERVED, now, now)

    suspend fun defer(
        botId: String,
        sourceKind: String,
        sourceId: String,
        now: Long = System.currentTimeMillis(),
        observedAt: Long? = null,
    ) = decide(botId, sourceKind, sourceId, BotInboxState.DEFERRED, observedAt, now)

    suspend fun ignore(botId: String, sourceKind: String, sourceId: String, now: Long = System.currentTimeMillis()) =
        decide(botId, sourceKind, sourceId, BotInboxState.IGNORED, null, now)

    suspend fun handle(botId: String, sourceKind: String, sourceId: String, now: Long = System.currentTimeMillis()) =
        decide(botId, sourceKind, sourceId, BotInboxState.HANDLED, now, now)

    private suspend fun decide(
        botId: String,
        sourceKind: String,
        sourceId: String,
        state: String,
        observedAt: Long?,
        now: Long,
    ): Boolean = guard("decide $state $sourceKind/$sourceId") {
        val item = repository.findBySource(botId, sourceKind, sourceId) ?: return@guard false
        repository.decide(item.id, state, observedAt, now)
    } ?: false

    private suspend fun <T> guard(action: String, block: suspend () -> T): T? = try {
        block()
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (failure: Throwable) {
        logger.w("[BotInboxProjection] $action 失败: ${failure.message}", failure)
        null
    }
}
