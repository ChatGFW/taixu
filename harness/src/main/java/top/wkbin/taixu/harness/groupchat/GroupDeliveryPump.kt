package top.wkbin.taixu.harness.groupchat

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.retryWhen
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import top.wkbin.taixu.core.common.logging.AppLogger
import top.wkbin.taixu.core.database.repository.GroupDeliveryRepository
import top.wkbin.taixu.core.model.bot.BotInboxSource
import top.wkbin.taixu.core.model.bot.GroupDelivery
import top.wkbin.taixu.harness.bot.inbox.BotInboxProjection

/** Only unsubmitted receipts are replayable. Native admission uncertainty never retries execution. */
internal class GroupDeliveryPump(
    private val repository: GroupDeliveryRepository,
    private val replies: GroupReplyQueue,
    private val scope: CoroutineScope,
    private val logger: AppLogger,
    private val isExternalRuntime: suspend (botId: String) -> Boolean = { false },
    private val inbox: BotInboxProjection? = null,
    private val deliver: suspend (GroupDelivery) -> GroupTurnOutcome,
) {
    private val startLock = Mutex()
    private var collector: Job? = null
    private var recovered = false
    private val scheduled = mutableSetOf<String>()
    private val refresh = Channel<Unit>(Channel.CONFLATED)

    suspend fun start() = startLock.withLock {
        if (collector?.isActive == true) return@withLock
        if (!recovered) {
            repository.recoverDeliveries()
            repository.recoverOrphanStreams()
            recovered = true
        }
        collector = scope.launch {
            merge(repository.pendingDeliveries(), refresh.receiveAsFlow().map {
                repository.pendingDeliveries().first()
            }).retryWhen { failure, _ ->
                if (failure is CancellationException) throw failure
                logger.e("[GroupDelivery] 收件读取失败，稍后重试", failure)
                delay(5_000)
                true
            }.collect { receipts ->
                // Iterable.forEach 是 inline（非 crossinline）；落在 Flow.collect 的 suspend 体里可调用 isExternalRuntime。
                receipts.distinctBy { "${it.botId}:${it.groupId}" }.forEach { receipt ->
                    if (isExternalRuntime(receipt.botId)) return@forEach
                    if (synchronized(scheduled) { scheduled.add("${receipt.botId}:${receipt.groupId}") }) enqueue(receipt)
                }
            }
        }
    }

    private suspend fun enqueue(receipt: GroupDelivery) {
        val job = replies.launch(receipt.groupId, "${receipt.botId}:${receipt.groupId}") {
            val nonce = repository.claimDelivery(receipt.id) ?: return@launch
            inbox?.admit(receipt.botId, BotInboxSource.GROUP_MESSAGE, receipt.sourceMessageId, "群消息")
            val claimed = receipt.copy(claimNonce = nonce, status = GroupDelivery.SUBMITTING)
            try {
                val result = deliver(claimed)
                repository.finishDelivery(claimed.id, when {
                    result.uncertain -> GroupDelivery.UNKNOWN
                    result.failed -> GroupDelivery.FAILED
                    else -> GroupDelivery.COMPLETED
                }, result.text.takeIf { result.failed || result.uncertain }, nonce)
                projectResult(result, claimed)
            } catch (cancelled: CancellationException) {
                withContext(NonCancellable) {
                    inbox?.defer(claimed.botId, BotInboxSource.GROUP_MESSAGE, claimed.sourceMessageId)
                    markUnknown(claimed.id, "群消息处理被中断，请核对会话记录", nonce)
                }
                throw cancelled
            } catch (failure: Exception) {
                inbox?.defer(claimed.botId, BotInboxSource.GROUP_MESSAGE, claimed.sourceMessageId)
                logger.e("[GroupDelivery] 群消息处理失败", failure)
                markUnknown(claimed.id, "群消息处理结果待核对，请检查会话记录", nonce)
            }
        }
        if (job == null) {
            runCatching { repository.finishDelivery(receipt.id, GroupDelivery.CANCELLED) }
                .onFailure { logger.e("[GroupDelivery] 取消收件记录失败", it) }
            unschedule(receipt)
        } else job.invokeOnCompletion { unschedule(receipt) }
    }

    private fun unschedule(receipt: GroupDelivery) {
        synchronized(scheduled) { scheduled.remove("${receipt.botId}:${receipt.groupId}") }
        refresh.trySend(Unit)
    }

    /** Attention Decision：不确定优先记 deferred，其次才看是否真正进过轮次。 */
    private suspend fun projectResult(result: GroupTurnOutcome, delivery: GroupDelivery) {
        val inbox = inbox ?: return
        when {
            result.uncertain -> inbox.defer(
                delivery.botId,
                BotInboxSource.GROUP_MESSAGE,
                delivery.sourceMessageId,
                observedAt = if (result.observed) System.currentTimeMillis() else null,
            )
            result.observed -> inbox.observe(delivery.botId, BotInboxSource.GROUP_MESSAGE, delivery.sourceMessageId)
            else -> inbox.ignore(delivery.botId, BotInboxSource.GROUP_MESSAGE, delivery.sourceMessageId)
        }
    }

    private suspend fun markUnknown(id: String, reason: String, claimNonce: String) {
        // A storage outage must not crash the process or trigger a second native submission.
        runCatching { repository.finishDelivery(id, GroupDelivery.UNKNOWN, reason, claimNonce) }
            .onFailure { logger.e("[GroupDelivery] 保存待核对状态失败，保留收件供重启核对", it) }
    }
}
