package top.wkbin.taixu.core.database

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BotTaskCapabilityMigrationTest {
    @get:Rule val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), AppDatabase::class.java,
        emptyList(), FrameworkSQLiteOpenHelperFactory())

    @Test fun `87 to 88 preserves old tasks without inventing grants`() {
        val path = InstrumentationRegistry.getInstrumentation().targetContext.getDatabasePath("task-capability-migration.db").absolutePath
        helper.createDatabase(path, 87).use { db ->
            db.execSQL("INSERT INTO bot_assignments (id,groupId,botId,botName,title,task,status,writePathsJson,createdAt,startedAt) " +
                "VALUES ('old',NULL,'bot','Bot','Work','Original task','RUNNING','[]',12,13)")
        }
        helper.runMigrationsAndValidate(path, 88, true, MIGRATION_87_88).use { db ->
            db.query("SELECT task,status,createdAt,capabilityGrantJson FROM bot_assignments WHERE id='old'").use { row ->
                assertTrue(row.moveToFirst())
                assertEquals("Original task", row.getString(0))
                assertEquals("RUNNING", row.getString(1))
                assertEquals(12L, row.getLong(2))
                assertTrue(row.isNull(3))
            }
        }
    }
}
