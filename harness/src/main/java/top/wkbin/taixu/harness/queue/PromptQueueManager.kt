package top.wkbin.taixu.harness.queue

import java.util.UUID
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import top.wkbin.taixu.harness.session.DefaultSessionTitle
import kotlinx.serialization.json.Json
import top.wkbin.taixu.core.common.logging.AppLogger
import top.wkbin.taixu.core.database.HarnessEntryEntity
import top.wkbin.taixu.core.database.HarnessQueueItemEntity
import top.wkbin.taixu.core.database.HarnessRuntimeRepository
import top.wkbin.taixu.harness.HarnessMessage
import top.wkbin.taixu.harness.PendingMessage
import top.wkbin.taixu.harness.QueuedPrompt
import top.wkbin.taixu.harness.UserMessage
import top.wkbin.taixu.harness.session.SessionTreeStore

enum class PromptQueue(val id: String) {
    STEER("steer"), FOLLOW_UP("follow_up"), NEXT_RUN("next_run")
}

/** Input drained at the end of a text-only round. [continuationCount] keeps the turn alive. */
data class TextRoundInput(
    val followUps: List<UserMessage>,
    val steering: List<UserMessage>,
) {
    val continuationCount: Int get() = followUps.size + steering.size
}

/**
 * Durable prompt queues with explicit consumption timing.
 *
 * 所有操作以 [laneName] 定位队列（默认主 lane）；子智能体等独立 lane
 * 可通过显式传参获得同等的持久化队列能力。
 */
class PromptQueueManager(
    private val repository: HarnessRuntimeRepository,
    private val json: Json,
    private val sessionStore: SessionTreeStore,
    private val logger: AppLogger? = null,
) {
    private val recoveryMutex = Mutex()
    private var recovered = false
    private suspend fun ready() = recoveryMutex.withLock {
        if (!recovered) { repository.resetQueueEdits(); recovered = true }
    }

    suspend fun beginEdit(sessionId: String, itemId: String, token: String): QueuedPrompt? {
        ready()
        return sessionStore.withLaneLock(sessionId, SessionTreeStore.MAIN_LANE) {
            val prompt = listAll(sessionId).firstOrNull { it.id == itemId } ?: return@withLaneLock null
            // Governed group/scheduled instructions retain their original authority and content.
            if (prompt.message.initiatorKind != "person" || prompt.message.groupWorkId != null ||
                prompt.message.groupId != null) return@withLaneLock null
            if (repository.holdQueueEdit(itemId, sessionId, SessionTreeStore.MAIN_LANE, token))
                prompt.copy(isEditing = true) else null
        }
    }

    suspend fun saveEdit(sessionId: String, itemId: String, token: String, text: String): Boolean {
        ready()
        val item = repository.listAllQueues(sessionId, SessionTreeStore.MAIN_LANE)
            .firstOrNull { it.id == itemId && it.editingToken == token } ?: return false
        val original = decodeOrCancel(item) ?: return false
        val updated = original.copy(text = text.trim())
        if (updated.text.isBlank() && updated.imageUrls.isEmpty()) return false
        val description = updated.text.ifBlank { "用户发送了 ${updated.imageUrls.size} 张图片" }
        return repository.saveQueueEdit(itemId, sessionId, SessionTreeStore.MAIN_LANE, token,
            json.encodeToString(PendingMessage.serializer(), updated), updated.taskId,
            DefaultSessionTitle.fromInstruction(description), description, System.currentTimeMillis())
    }

    suspend fun cancelEdit(sessionId: String, itemId: String, token: String) {
        ready()
        repository.releaseQueueEdit(itemId, sessionId, SessionTreeStore.MAIN_LANE, token)
    }

    suspend fun removeById(sessionId: String, itemId: String): QueuedPrompt? =
        sessionStore.withLaneLock(sessionId, SessionTreeStore.MAIN_LANE) {
            val prompt = listAll(sessionId).firstOrNull { it.id == itemId } ?: return@withLaneLock null
            repository.cancelQueued(itemId)
            prompt
        }

    suspend fun convertToSteer(sessionId: String, itemId: String): Boolean =
        sessionStore.withLaneLock(sessionId, SessionTreeStore.MAIN_LANE) {
            ready()
            val item = repository.listAllQueues(sessionId, SessionTreeStore.MAIN_LANE)
                .firstOrNull { it.id == itemId && it.queueType == PromptQueue.NEXT_RUN.id && it.editingToken == null }
                ?: return@withLaneLock false
            val operation = repository.findLane(sessionId, SessionTreeStore.MAIN_LANE)?.currentOperationId
                ?: return@withLaneLock false
            val original = decodeOrCancel(item) ?: return@withLaneLock false
            if (original.initiatorKind != "person" || original.groupWorkId != null || original.groupId != null) return@withLaneLock false
            repository.convertQueueToSteer(item, json.encodeToString(PendingMessage.serializer(), original.copy(taskId = null)),
                original.taskId, operation, System.currentTimeMillis())
        }

    private suspend fun rows(sessionId: String, laneName: String, queue: PromptQueue): List<HarnessQueueItemEntity> {
        ready()
        return repository.listQueue(sessionId, laneName, queue.id)
    }

    private suspend fun decodeOrCancel(item: HarnessQueueItemEntity): PendingMessage? =
        runCatching {
            json.decodeFromString(PendingMessage.serializer(), item.payloadJson).copy(createdAt = item.createdAt)
        }
            .onFailure { failure ->
                logger?.w("Removing invalid queued prompt ${item.id}: ${failure.message}")
                repository.cancelQueued(item.id)
            }.getOrNull()

    suspend fun enqueue(
        sessionId: String,
        queue: PromptQueue,
        prompt: PendingMessage,
        laneName: String = SessionTreeStore.MAIN_LANE,
    ): String {
        ready()
        val operationId = repository.ensureLane(sessionId, laneName).currentOperationId
        val id = UUID.randomUUID().toString()
        repository.enqueue(
            HarnessQueueItemEntity(
                id = id,
                sessionId = sessionId,
                laneName = laneName,
                operationId = operationId,
                queueType = queue.id,
                createdAt = prompt.createdAt,
                payloadJson = json.encodeToString(PendingMessage.serializer(), prompt),
            ),
        )
        return id
    }

    suspend fun list(
        sessionId: String,
        queue: PromptQueue,
        laneName: String = SessionTreeStore.MAIN_LANE,
    ): List<Pair<String, PendingMessage>> =
        rows(sessionId, laneName, queue).mapNotNull { item ->
            decodeOrCancel(item)?.let { item.id to it }
        }

    suspend fun first(
        sessionId: String,
        queue: PromptQueue,
        laneName: String = SessionTreeStore.MAIN_LANE,
    ): Pair<String, PendingMessage>? {
        for (head in rows(sessionId, laneName, queue)) {
            if (head.editingToken != null) return null
            decodeOrCancel(head)?.let { return head.id to it }
        }
        return null
    }

    suspend fun listAll(
        sessionId: String,
        laneName: String = SessionTreeStore.MAIN_LANE,
    ): List<QueuedPrompt> {
        ready()
        return repository.listAllQueues(sessionId, laneName).mapNotNull { item ->
            val queue = PromptQueue.entries.firstOrNull { it.id == item.queueType }
            if (queue == null) {
                logger?.w("Removing queued prompt ${item.id} with unknown queue type ${item.queueType}")
                repository.cancelQueued(item.id)
                return@mapNotNull null
            }
            decodeOrCancel(item)?.let { QueuedPrompt(item.id, queue, it, item.editingToken != null) }
        }
    }

    suspend fun cancel(sessionId: String, queue: PromptQueue, index: Int, laneName: String = SessionTreeStore.MAIN_LANE) {
        list(sessionId, queue, laneName).getOrNull(index)?.first?.let { repository.cancelQueued(it) }
    }

    suspend fun clear(sessionId: String, queue: PromptQueue, laneName: String = SessionTreeStore.MAIN_LANE) {
        repository.clearQueue(sessionId, laneName, queue.id)
    }

    /**
     * Text-only rounds are the last chance to deliver input before the run ends.
     * Follow-ups stay ahead of steering so a correction is the newest injected turn.
     */
    suspend fun takeTextRoundInput(
        sessionId: String,
        laneName: String = SessionTreeStore.MAIN_LANE,
    ): TextRoundInput {
        val followUps = consume(sessionId, PromptQueue.FOLLOW_UP, laneName = laneName)
        val steering = consume(sessionId, PromptQueue.STEER, laneName = laneName)
        return TextRoundInput(followUps, steering)
    }

    /**
     * When a run ends with steering still queued and no next run waiting, those
     * corrections become the next run. createdAt and id are unchanged, so order holds.
     * Returns 0 when a next run is already pending or nothing is steered.
     */
    suspend fun promoteSteeringToNextRun(
        sessionId: String,
        laneName: String = SessionTreeStore.MAIN_LANE,
    ): Int {
        if (list(sessionId, PromptQueue.NEXT_RUN, laneName).isNotEmpty()) return 0
        return repository.retargetQueue(sessionId, laneName, PromptQueue.STEER.id, PromptQueue.NEXT_RUN.id)
    }

    /** Next queued run, promoting leftover steering first when nothing else is waiting. */
    suspend fun nextRunOrPromotedSteer(
        sessionId: String,
        laneName: String = SessionTreeStore.MAIN_LANE,
    ): Pair<String, PendingMessage>? {
        if (list(sessionId, PromptQueue.NEXT_RUN, laneName).isEmpty()) {
            promoteSteeringToNextRun(sessionId, laneName)
        }
        return first(sessionId, PromptQueue.NEXT_RUN, laneName)
    }

    /** Sessions among [sessionIds] that still hold any of [queues], in input order. */
    suspend fun sessionsHolding(
        sessionIds: Iterable<String>,
        queues: Set<PromptQueue>,
        laneName: String = SessionTreeStore.MAIN_LANE,
    ): List<String> = sessionIds.filter { id -> queues.any { queue -> first(id, queue, laneName) != null } }

    /** Atomically turns queued prompts into immutable entries on the given lane. */
    suspend fun consume(
        sessionId: String,
        queue: PromptQueue,
        limit: Int = Int.MAX_VALUE,
        laneName: String = SessionTreeStore.MAIN_LANE,
    ): List<UserMessage> = sessionStore.withLaneLock(sessionId, laneName) {
        // 与 SessionTreeStore.append 同一把 per-lane 锁：consume 会把队列项转成 lane 上的
        // message entry（读 leafId → 写 entry → 移动叶子），不持锁时与并发 append 只靠
        // DAO 的乐观校验兜底。锁内只有本地 DB 读写，无长耗时挂起。
        val items = rows(sessionId, laneName, queue).takeWhile { it.editingToken == null }.take(limit)
        val consumed = ArrayList<UserMessage>(items.size)
        for (item in items) {
            val prompt = decodeOrCancel(item) ?: continue
            val lane = repository.ensureLane(sessionId, laneName)
            val message = UserMessage(
                id = UUID.randomUUID().toString(),
                createdAt = System.currentTimeMillis(),
                text = prompt.text,
                imageUrls = prompt.imageUrls,
            )
            val entry = HarnessEntryEntity(
                id = message.id,
                sessionId = sessionId,
                parentId = lane.leafId,
                createdAt = message.createdAt,
                entryType = "message",
                customType = "user",
                payloadJson = json.encodeToString(HarnessMessage.serializer(), message),
            )
            repository.consumeQueued(
                itemId = item.id,
                entry = entry,
                lane = lane.copy(leafId = entry.id, updatedAt = System.currentTimeMillis()),
            )
            consumed += message
        }
        consumed
    }
}
