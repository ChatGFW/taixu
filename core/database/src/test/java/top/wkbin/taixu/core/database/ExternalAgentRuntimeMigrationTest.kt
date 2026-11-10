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
class ExternalAgentRuntimeMigrationTest {
    @get:Rule val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), AppDatabase::class.java,
        emptyList(), FrameworkSQLiteOpenHelperFactory())

    @Test fun `89 to 90 defaults bots to local and creates token table`() {
        val path = InstrumentationRegistry.getInstrumentation().targetContext
            .getDatabasePath("external-agent-runtime-migration.db").absolutePath
        helper.createDatabase(path, 89).close()
        helper.runMigrationsAndValidate(path, 90, true, MIGRATION_89_90).use { db ->
            db.query("PRAGMA table_info(`persona_bots`)").use { row ->
                val columns = mutableListOf<String>()
                while (row.moveToNext()) columns += row.getString(1)
                assertTrue(columns.contains("runtimeMode"))
            }
            db.query("SELECT name FROM sqlite_master WHERE type='table' AND name='external_agent_runtime_tokens'").use {
                assertTrue(it.moveToFirst())
            }
        }
    }

    @Test fun `90 to 91 adds claimedAt for lease TTL`() {
        val path = InstrumentationRegistry.getInstrumentation().targetContext
            .getDatabasePath("external-agent-claim-lease-migration.db").absolutePath
        helper.createDatabase(path, 90).close()
        helper.runMigrationsAndValidate(path, 91, true, MIGRATION_90_91).use { db ->
            db.query("PRAGMA table_info(`group_deliveries`)").use { row ->
                val columns = mutableListOf<String>()
                while (row.moveToNext()) columns += row.getString(1)
                assertTrue(columns.contains("claimedAt"))
            }
        }
    }
}
