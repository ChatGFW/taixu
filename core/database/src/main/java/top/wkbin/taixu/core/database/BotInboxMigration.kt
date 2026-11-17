package top.wkbin.taixu.core.database

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/** 91 → 92：新增 Inbox 投影表；纯派生表，不迁移既有数据（重放由生产者按需补齐）。 */
val MIGRATION_91_92 = object : Migration(91, 92) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `bot_inbox_items` (" +
                "`id` TEXT NOT NULL, `botId` TEXT NOT NULL, `sourceKind` TEXT NOT NULL, " +
                "`sourceId` TEXT NOT NULL, `sessionId` TEXT, `reason` TEXT NOT NULL, " +
                "`priority` INTEGER NOT NULL, `state` TEXT NOT NULL, `observedAt` INTEGER, " +
                "`updatedAt` INTEGER, `createdAt` INTEGER NOT NULL, PRIMARY KEY(`id`))",
        )
        db.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_bot_inbox_items_botId_sourceKind_sourceId` " +
                "ON `bot_inbox_items` (`botId`, `sourceKind`, `sourceId`)",
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_bot_inbox_items_botId_state_createdAt` " +
                "ON `bot_inbox_items` (`botId`, `state`, `createdAt`)",
        )
    }
}
