package top.wkbin.taixu.core.database

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val MIGRATION_88_89 = object : Migration(88, 89) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE group_deliveries ADD COLUMN claimNonce TEXT DEFAULT NULL")
    }
}
