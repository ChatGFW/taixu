package top.wkbin.taixu.core.database

import androidx.room.Query
import androidx.room.Transaction

/** Compare-and-set edits never replace or resurrect queue rows. */
interface HarnessQueueEditQueries {
    @Query("SELECT * FROM harness_queue_items WHERE id = :itemId AND sessionId = :sessionId AND laneName = :laneName")
    suspend fun findEditableQueue(itemId: String, sessionId: String, laneName: String): HarnessQueueItemEntity?

    @Query("UPDATE harness_queue_items SET editingToken = :token WHERE id = :itemId AND sessionId = :sessionId AND laneName = :laneName AND editingToken IS NULL")
    suspend fun holdQueueEdit(itemId: String, sessionId: String, laneName: String, token: String): Int

    @Query("DELETE FROM harness_queue_items WHERE id = :itemId AND sessionId = :sessionId AND laneName = :laneName AND editingToken IS NULL")
    suspend fun claimUneditedQueue(itemId: String, sessionId: String, laneName: String): Int

    @Query("""UPDATE harness_queue_items SET payloadJson = :payload, editingToken = NULL,
        queueType = CASE WHEN operationId IS NULL AND queueType IN ('steer', 'follow_up') THEN 'next_run' ELSE queueType END
        WHERE id = :itemId AND sessionId = :sessionId AND laneName = :laneName AND editingToken = :token""")
    suspend fun replaceHeldQueue(itemId: String, sessionId: String, laneName: String, token: String, payload: String): Int

    @Query("""UPDATE agent_tasks SET title = :title, description = :description, updatedAt = :now
        WHERE id = :taskId AND sessionId = :sessionId AND status = 'QUEUED' AND operationId IS NULL""")
    suspend fun updateQueuedTaskInstruction(taskId: String, sessionId: String, title: String, description: String, now: Long): Int

    @Transaction
    suspend fun saveQueueEdit(itemId: String, sessionId: String, laneName: String, token: String,
        payload: String, taskId: String?, title: String, description: String, now: Long): Boolean {
        val current = findEditableQueue(itemId, sessionId, laneName) ?: return false
        if (current.editingToken != token) return false
        if (taskId != null) check(updateQueuedTaskInstruction(taskId, sessionId, title, description, now) == 1) {
            "Queued task changed before editing"
        }
        check(replaceHeldQueue(itemId, sessionId, laneName, token, payload) == 1)
        return true
    }

    @Query("SELECT currentOperationId FROM harness_lanes WHERE sessionId = :sessionId AND name = :laneName")
    suspend fun queueLaneOperation(sessionId: String, laneName: String): String?

    @Query("UPDATE harness_queue_items SET queueType = 'next_run', operationId = NULL WHERE operationId = :operationId AND queueType = :queueType")
    suspend fun preserveEditedOperationQueue(operationId: String, queueType: String)

    @Query("""UPDATE agent_tasks SET status = 'CANCELLED', statusDetail = '已转为修正指令',
        updatedAt = :now, completedAt = :now WHERE id = :taskId AND sessionId = :sessionId AND status = 'QUEUED' AND operationId IS NULL""")
    suspend fun cancelSteeredTask(taskId: String, sessionId: String, now: Long): Int

    @Query("""UPDATE harness_queue_items SET payloadJson = :payload, queueType = 'steer', operationId = :operationId
        WHERE id = :itemId AND sessionId = :sessionId AND laneName = :laneName AND queueType = 'next_run'
        AND payloadJson = :expectedPayload AND editingToken IS NULL""")
    suspend fun retargetOneQueue(itemId: String, sessionId: String, laneName: String, expectedPayload: String, payload: String, operationId: String): Int

    @Transaction
    suspend fun convertQueueToSteer(item: HarnessQueueItemEntity, payload: String, taskId: String?, operationId: String, now: Long): Boolean {
        if (queueLaneOperation(item.sessionId, item.laneName) != operationId) return false
        val current = findEditableQueue(item.id, item.sessionId, item.laneName) ?: return false
        if (current != item || current.editingToken != null || current.queueType != "next_run") return false
        if (taskId != null) check(cancelSteeredTask(taskId, item.sessionId, now) == 1) { "Queued task changed before steering" }
        check(retargetOneQueue(item.id, item.sessionId, item.laneName, item.payloadJson, payload, operationId) == 1)
        return true
    }

    @Query("""UPDATE harness_queue_items SET editingToken = NULL,
        queueType = CASE WHEN operationId IS NULL AND queueType IN ('steer', 'follow_up') THEN 'next_run' ELSE queueType END
        WHERE id = :itemId AND sessionId = :sessionId AND laneName = :laneName AND editingToken = :token""")
    suspend fun releaseQueueEdit(itemId: String, sessionId: String, laneName: String, token: String): Int

    @Query("""UPDATE harness_queue_items SET editingToken = NULL,
        queueType = CASE WHEN operationId IS NULL AND queueType IN ('steer', 'follow_up') THEN 'next_run' ELSE queueType END
        WHERE editingToken IS NOT NULL""")
    suspend fun resetQueueEdits()
}
