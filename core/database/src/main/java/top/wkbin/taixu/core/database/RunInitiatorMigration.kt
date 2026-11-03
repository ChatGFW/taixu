package top.wkbin.taixu.core.database

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * 85→86：运行发起因（OpenBot-style initiator）落库。
 *
 * - agent_tasks：准入时固化，恢复/审批续跑可重建协程上下文
 * - agent_approval_audits：免审/授权/deferred 行可筛「无人盯着」
 * 旧行回填 person；不回填猜测的 schedule/handoff。
 */
val MIGRATION_85_86 = object : Migration(85, 86) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE agent_tasks ADD COLUMN initiatorKind TEXT NOT NULL DEFAULT 'person'")
        db.execSQL("ALTER TABLE agent_tasks ADD COLUMN initiatorId TEXT DEFAULT NULL")
        db.execSQL("ALTER TABLE agent_approval_audits ADD COLUMN initiatorKind TEXT NOT NULL DEFAULT 'person'")
        db.execSQL("ALTER TABLE agent_approval_audits ADD COLUMN initiatorId TEXT DEFAULT NULL")
    }
}
