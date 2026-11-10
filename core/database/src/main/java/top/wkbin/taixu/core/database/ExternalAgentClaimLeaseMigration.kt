package top.wkbin.taixu.core.database

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val MIGRATION_90_91 = object : Migration(90, 91) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE group_deliveries ADD COLUMN claimedAt INTEGER DEFAULT NULL")
        // Existing in-flight EXTERNAL leases get a clock so the TTL reclaim can age them.
        db.execSQL(
            "UPDATE group_deliveries SET claimedAt = createdAt " +
                "WHERE status = 'submitting' AND claimNonce IS NOT NULL AND claimedAt IS NULL",
        )
    }
}
