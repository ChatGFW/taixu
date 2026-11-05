package top.wkbin.taixu.core.database

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val MIGRATION_86_87 = object : Migration(86, 87) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE harness_queue_items ADD COLUMN editingToken TEXT DEFAULT NULL")
    }
}
