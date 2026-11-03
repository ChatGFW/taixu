package top.wkbin.taixu.core.database

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** MIGRATION_85_86：agent_tasks / agent_approval_audits 增加 initiator 列，旧行回填 person。 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RunInitiatorMigration85To86Test {
    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory(),
    )

    private val dbPath: String =
        InstrumentationRegistry.getInstrumentation().targetContext.getDatabasePath(TEST_DB).absolutePath

    @Test
    fun `legacy tasks and approval audits default initiator to person`() {
        helper.createDatabase(dbPath, 85).use { db ->
            db.execSQL(
                """INSERT INTO harness_sessions (id, title, createdAt, updatedAt, modelId, modelVariant, workspace, projectType, approvalMode, runMode, executionTargetJson, pinnedAt)
                   VALUES ('s1', '会话', 1, 1, NULL, NULL, '', '', 'full_access', 'build', 'unbound', 0)""",
            )
            db.execSQL(
                """INSERT INTO agent_tasks (id, sessionId, title, description, status, createdAt, updatedAt, progress, attemptCount, maxAttempts, autoResume, lastRound, maxRounds)
                   VALUES ('t1', 's1', '标题', '描述', 'QUEUED', 1, 1, 0, 0, 2, 1, 0, 0)""",
            )
            db.execSQL(
                """INSERT INTO agent_approval_audits (id, sessionId, kind, toolName, riskLevel, createdAt)
                   VALUES ('a1', 's1', 'grant_exempted', 'base', 'high', 1)""",
            )
        }
        helper.runMigrationsAndValidate(dbPath, 86, true, MIGRATION_85_86).use { db ->
            db.query("SELECT initiatorKind, initiatorId FROM agent_tasks WHERE id = 't1'").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("person", cursor.getString(0))
                assertTrue(cursor.isNull(1))
            }
            db.query("SELECT initiatorKind, initiatorId FROM agent_approval_audits WHERE id = 'a1'").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("person", cursor.getString(0))
                assertTrue(cursor.isNull(1))
            }
            db.execSQL("UPDATE agent_tasks SET initiatorKind = 'schedule', initiatorId = 'sched-1' WHERE id = 't1'")
            db.query("SELECT initiatorKind, initiatorId FROM agent_tasks WHERE id = 't1'").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("schedule", cursor.getString(0))
                assertEquals("sched-1", cursor.getString(1))
            }
        }
    }

    private companion object {
        const val TEST_DB = "run-initiator-migration-test.db"
    }
}
