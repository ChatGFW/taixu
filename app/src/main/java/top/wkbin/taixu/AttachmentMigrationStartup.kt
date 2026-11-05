package top.wkbin.taixu

import android.app.Application
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.koin.android.ext.android.getKoin
import top.wkbin.taixu.core.database.HarnessRuntimeDao
import top.wkbin.taixu.runtime.AttachmentMigrator

/**
 * 一次性把历史附件按引用搬到会话私有目录。
 *
 * 与备份恢复一样放在启动早期同步执行：迁移完成前沙箱不应挂载会话目录，
 * 否则旧消息里的 `/attachments/<name>` 会短暂失效。marker 存在时无任何 IO。
 */
internal fun Application.migrateLegacyAttachments() {
    runCatching {
        runBlocking(Dispatchers.IO) {
            val koin = getKoin()
            val references = koin.get<HarnessRuntimeDao>().attachmentReferenceRows()
                .map { it.sessionId to it.payloadJson }
            koin.get<AttachmentMigrator>().migrate(references)
        }
    }.onFailure { Log.w("TaiXu", "附件迁移失败，保持原布局以便排查", it) }
}
