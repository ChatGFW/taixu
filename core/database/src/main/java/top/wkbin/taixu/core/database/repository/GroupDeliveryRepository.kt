package top.wkbin.taixu.core.database.repository

import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import top.wkbin.taixu.core.database.GroupChatDao
import top.wkbin.taixu.core.model.bot.GroupDelivery
import top.wkbin.taixu.core.model.bot.GroupMessage

interface GroupDeliveryRepository {
    suspend fun stageMessage(message: GroupMessage, botIds: List<String>)
    fun pendingDeliveries(): Flow<List<GroupDelivery>>
    suspend fun deliverySource(id: String): GroupMessage?
    suspend fun listDeliveries(groupId: String, limit: Int = 100): List<GroupDelivery>
    suspend fun findDelivery(id: String): GroupDelivery?
    /** Returns the claim nonce on success; null if already claimed or unavailable. */
    suspend fun claimDelivery(id: String): String?
    suspend fun prepareDelivery(id: String, sessionId: String, claimNonce: String): Boolean
    suspend fun admitDelivery(id: String, sessionId: String, taskId: String?, claimNonce: String): Boolean
    /** With nonce settles the active claim; without nonce only settles still-pending receipts. */
    suspend fun finishDelivery(id: String, status: String, error: String? = null, claimNonce: String? = null)
    suspend fun recoverDeliveries()
    suspend fun recoverOrphanStreams(notice: String = "⚠️ 群聊回复因进程退出中断，结果待核对")
    suspend fun cancelDeliveries(groupId: String)
}

internal class RoomGroupDeliveryRepository(private val dao: GroupChatDao, private val json: Json) : GroupDeliveryRepository {
    override suspend fun stageMessage(message: GroupMessage, botIds: List<String>) = dao.stageGroupDelivery(message.toEntity(json), botIds)
    override fun pendingDeliveries() = dao.pendingGroupDeliveries().map { rows -> rows.map { it.toModel() } }
    override suspend fun deliverySource(id: String) = dao.findMessage(id)?.toModel(json)
    override suspend fun listDeliveries(groupId: String, limit: Int) = dao.listGroupDeliveries(groupId, limit.coerceIn(1, 100)).map { it.toModel() }
    override suspend fun findDelivery(id: String) = dao.findGroupDelivery(id)?.toModel()
    override suspend fun claimDelivery(id: String): String? {
        val nonce = UUID.randomUUID().toString()
        return nonce.takeIf { dao.claimGroupDelivery(id, nonce) == 1 }
    }
    override suspend fun prepareDelivery(id: String, sessionId: String, claimNonce: String) =
        dao.prepareGroupDelivery(id, sessionId, claimNonce) == 1
    override suspend fun admitDelivery(id: String, sessionId: String, taskId: String?, claimNonce: String) =
        dao.admitGroupDelivery(id, sessionId, taskId, claimNonce) == 1
    override suspend fun finishDelivery(id: String, status: String, error: String?, claimNonce: String?) =
        dao.finishGroupDelivery(id, status, error, claimNonce)
    override suspend fun recoverDeliveries() = dao.recoverGroupDeliveries()
    override suspend fun recoverOrphanStreams(notice: String) = dao.settleOrphanStreams(notice)
    override suspend fun cancelDeliveries(groupId: String) = dao.cancelGroupDeliveries(groupId)
}
