package top.wkbin.taixu.core.database

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Harness 会话从单表流水（harness_messages）改为 entry/lane/operation 结构化存储。
 *
 * 该版本的迁移曾漏提交，导致 29 版设备升级时 Room 直接抛 "migration not found" 崩溃。
 * 这里按 `schemas/.../30.json` 的导出结果重建（建表 SQL/索引与 Room 校验的期望完全一致），
 * 并把历史消息按 entryType=type 原样搬进 harness_entries，保留会话记录、顺序按 createdAt。
 *
 * 单独成文件：`DatabaseMigrations.kt` 已接近单文件 400 行上限。
 */
val MIGRATION_29_30 = object : Migration(29, 30) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS `harness_entries` (`sequence` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `id` TEXT NOT NULL, `sessionId` TEXT NOT NULL, `parentId` TEXT, `createdAt` INTEGER NOT NULL, `entryType` TEXT NOT NULL, `customType` TEXT, `payloadJson` TEXT NOT NULL)")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_harness_entries_id` ON `harness_entries` (`id`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_harness_entries_sessionId_sequence` ON `harness_entries` (`sessionId`, `sequence`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_harness_entries_sessionId_parentId` ON `harness_entries` (`sessionId`, `parentId`)")

        db.execSQL("CREATE TABLE IF NOT EXISTS `harness_lane_results` (`sessionId` TEXT NOT NULL, `laneName` TEXT NOT NULL, `operationId` TEXT NOT NULL, `outcome` TEXT NOT NULL, `finalEntryId` TEXT, `detailsJson` TEXT, `completedAt` INTEGER NOT NULL, PRIMARY KEY(`sessionId`, `laneName`))")

        db.execSQL("CREATE TABLE IF NOT EXISTS `harness_lanes` (`sessionId` TEXT NOT NULL, `name` TEXT NOT NULL, `leafId` TEXT, `currentOperationId` TEXT, `modelId` TEXT, `thinkingLevel` TEXT NOT NULL, `faulted` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, PRIMARY KEY(`sessionId`, `name`))")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_harness_lanes_sessionId_currentOperationId` ON `harness_lanes` (`sessionId`, `currentOperationId`)")

        db.execSQL("CREATE TABLE IF NOT EXISTS `harness_operations` (`id` TEXT NOT NULL, `sessionId` TEXT NOT NULL, `laneName` TEXT NOT NULL, `kind` TEXT NOT NULL, `status` TEXT NOT NULL, `phase` TEXT NOT NULL, `startedAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, `startLeafId` TEXT, `stateJson` TEXT NOT NULL, `pendingEffectKind` TEXT, `pendingEffectId` TEXT, `replayPolicy` TEXT, `attempt` INTEGER NOT NULL, PRIMARY KEY(`id`))")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_harness_operations_sessionId_laneName` ON `harness_operations` (`sessionId`, `laneName`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_harness_operations_status_updatedAt` ON `harness_operations` (`status`, `updatedAt`)")

        db.execSQL("CREATE TABLE IF NOT EXISTS `harness_queue_items` (`id` TEXT NOT NULL, `sessionId` TEXT NOT NULL, `laneName` TEXT NOT NULL, `operationId` TEXT, `queueType` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, `payloadJson` TEXT NOT NULL, PRIMARY KEY(`id`))")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_harness_queue_items_sessionId_laneName_queueType_createdAt` ON `harness_queue_items` (`sessionId`, `laneName`, `queueType`, `createdAt`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_harness_queue_items_operationId` ON `harness_queue_items` (`operationId`)")

        db.execSQL("CREATE TABLE IF NOT EXISTS `harness_usage` (`sequence` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `id` TEXT NOT NULL, `sessionId` TEXT NOT NULL, `operationId` TEXT, `entryId` TEXT, `provider` TEXT, `modelId` TEXT, `inputTokens` INTEGER NOT NULL, `outputTokens` INTEGER NOT NULL, `reasoningTokens` INTEGER NOT NULL, `cacheReadTokens` INTEGER NOT NULL, `cacheWriteTokens` INTEGER NOT NULL, `estimatedCostUsd` REAL, `adjustment` INTEGER NOT NULL, `detailsJson` TEXT, `createdAt` INTEGER NOT NULL)")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_harness_usage_id` ON `harness_usage` (`id`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_harness_usage_sessionId_sequence` ON `harness_usage` (`sessionId`, `sequence`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_harness_usage_operationId` ON `harness_usage` (`operationId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_harness_usage_entryId` ON `harness_usage` (`entryId`)")

        db.execSQL(
            """
            INSERT OR IGNORE INTO harness_entries (id, sessionId, parentId, createdAt, entryType, customType, payloadJson)
            SELECT id, sessionId, NULL, createdAt, type, NULL, payloadJson
            FROM harness_messages
            ORDER BY createdAt
            """.trimIndent(),
        )
        db.execSQL("DROP TABLE IF EXISTS `harness_messages`")
    }
}