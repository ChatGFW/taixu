package top.wkbin.taixu

import android.app.Application
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.koin.android.ext.android.getKoin
import org.koin.core.Koin
import top.wkbin.taixu.core.database.HarnessBlobStore
import top.wkbin.taixu.core.database.HarnessRuntimeDao
import top.wkbin.taixu.runtime.AttachmentMigrator
import top.wkbin.taixu.runtime.AttachmentReferenceScanMode

/**
 * 一次性把历史附件按引用搬到会话私有目录。
 *
 * 与备份恢复一样放在启动早期同步执行：迁移完成前沙箱不应挂载会话目录，
 * 否则旧消息里的 `/attachments/<name>` 会短暂失效，同时补回旧迁移遗漏的引用。
 *
 * 对过账之后不再读取 `harness_blobs`。外置 JSON 是工具结果缓存，单文件可达数 MB；
 * 若每次冷启动都整文件读入并用正则扫描，主线程会在 `bindApplication` 里被拖过 15 秒。
 * 迁移已完成且 `legacy/` 为空时，连对账扫描也跳过，只写标记。
 */
internal fun Application.migrateLegacyAttachments() {
    runCatching {
        runBlocking(Dispatchers.IO) {
            val koin = getKoin()
            val migrator = koin.get<AttachmentMigrator>()
            val mode = migrator.referenceScanMode()
            val references = when (mode) {
                AttachmentReferenceScanMode.NONE -> emptyList()
                AttachmentReferenceScanMode.FULL -> attachmentReferences(koin, limitTo = null)
                AttachmentReferenceScanMode.ORPHANS ->
                    attachmentReferences(koin, limitTo = migrator.orphanAttachmentNames())
            }
            migrator.migrate(references)
            migrator.markReferencesScanned()
        }
    }.onFailure { Log.w(TAG, "附件迁移失败，保持原布局以便排查", it) }
}

private suspend fun attachmentReferences(
    koin: Koin,
    limitTo: Set<String>?,
): List<Pair<String, String>> {
    if (limitTo != null && limitTo.isEmpty()) return emptyList()
    val blobs = koin.get<HarnessBlobStore>()
    return koin.get<HarnessRuntimeDao>().attachmentReferenceRows().mapNotNull { row ->
        val names = attachmentNames(blobs, row.payloadJson, limitTo)
        if (names.isEmpty()) null
        else row.sessionId to names.joinToString("\n") { "/attachments/$it" }
    }
}

private fun attachmentNames(
    blobs: HarnessBlobStore,
    payloadOrPointer: String,
    limitTo: Set<String>?,
): Set<String> {
    if (payloadOrPointer.startsWith(HarnessBlobStore.BLOB_PREFIX)) {
        val file = blobs.existingBlob(payloadOrPointer) ?: return emptySet()
        return runCatching { AttachmentMigrator.referencedAttachmentNames(file, limitTo) }
            .onFailure { Log.w(TAG, "读取附件引用失败，忽略该载荷", it) }
            .getOrDefault(emptySet())
    }
    return AttachmentMigrator.referencedAttachmentNames(payloadOrPointer, limitTo)
}

private const val TAG = "TaiXu"
