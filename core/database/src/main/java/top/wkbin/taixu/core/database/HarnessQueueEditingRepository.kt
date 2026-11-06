package top.wkbin.taixu.core.database

/** Optional edit capability, so read-only/test runtime stores fail closed. */
interface HarnessQueueEditingRepository {
    suspend fun holdQueueEdit(itemId: String, sessionId: String, laneName: String, token: String): Boolean = false
    suspend fun saveQueueEdit(itemId: String, sessionId: String, laneName: String, token: String,
        payload: String, taskId: String?, title: String, description: String, now: Long): Boolean = false
    suspend fun releaseQueueEdit(itemId: String, sessionId: String, laneName: String, token: String) {}
    suspend fun resetQueueEdits() {}
    suspend fun convertQueueToSteer(item: HarnessQueueItemEntity, payload: String, taskId: String?, operationId: String, now: Long): Boolean = false
}

internal class RoomHarnessQueueEditingRepository(private val dao: HarnessRuntimeDao) : HarnessQueueEditingRepository {
    override suspend fun holdQueueEdit(itemId: String, sessionId: String, laneName: String, token: String) =
        dao.holdQueueEdit(itemId, sessionId, laneName, token) == 1
    override suspend fun saveQueueEdit(itemId: String, sessionId: String, laneName: String, token: String,
        payload: String, taskId: String?, title: String, description: String, now: Long) =
        dao.saveQueueEdit(itemId, sessionId, laneName, token, payload, taskId, title, description, now)
    override suspend fun releaseQueueEdit(itemId: String, sessionId: String, laneName: String, token: String) {
        dao.releaseQueueEdit(itemId, sessionId, laneName, token)
    }
    override suspend fun resetQueueEdits() = dao.resetQueueEdits()
    override suspend fun convertQueueToSteer(item: HarnessQueueItemEntity, payload: String, taskId: String?, operationId: String, now: Long) =
        dao.convertQueueToSteer(item, payload, taskId, operationId, now)
}
